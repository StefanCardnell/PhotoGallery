package com.bignerdranch.android.photogallery;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.ProgressBar;

import java.util.ArrayList;
import java.util.List;

public class PhotoGalleryFragment extends VisibleFragment {

    private static final String TAG = "PhotoGalleryFragment";

    private boolean mLoadingPage = false; // Indicates if page load is in progress
    private int mNextPage = 1;
    private List<GalleryItem> mItems = new ArrayList<>();
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;

    private RecyclerView mPhotoRecyclerView;
    private ProgressBar mProgressBar;
    private SearchView mSearchView;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    private class FetchItemsTask extends AsyncTask<Void, Void, List<GalleryItem>> {
        private String mQuery;

        public FetchItemsTask(String query){
            mQuery = query;
        }

        @Override
        protected List<GalleryItem> doInBackground(Void... voids) {
            if(mQuery == null || mQuery.isEmpty()) {
                return new FlickrFetchr().fetchRecentPhotos(mNextPage);
            } else {
                return new FlickrFetchr().searchPhotos(mQuery, mNextPage);
            }
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mLoadingPage = true;
            updateUI(true);
        }

        @Override
        protected void onPostExecute(List<GalleryItem> items) {
            mNextPage++;
            mLoadingPage = false;
            addToItems(items);
            updateUI(true);
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Log.i(TAG, "OnCreate called");
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
        fetchNextPage();

        Handler responseHandler = new Handler();
        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler);
        mThumbnailDownloader.setThumbnailDownloadListener(
                new ThumbnailDownloader.ThumbnailDownloadListener<PhotoHolder>() {
                    @Override
                    public void onThumbnailDownloaded(PhotoHolder photoHolder, Bitmap bitmap) {
                        Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                        photoHolder.bindDrawable(drawable);
                    }
                }
        );
        mThumbnailDownloader.start();
        mThumbnailDownloader.getLooper();
        Log.i(TAG, "Background thread started");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.i(TAG, "OnCreateView Called");
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);
        mPhotoRecyclerView = v.findViewById(R.id.photo_recycler_view);
        mPhotoRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Calculate number of columns, allocating one every 0.8 inches
                DisplayMetrics dm = getResources().getDisplayMetrics();
                float screenInches = (dm.widthPixels / dm.xdpi);
                int cols = (int) (screenInches / 0.8);
                mPhotoRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(), cols));
                mPhotoRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);  // Prevent infinite loop
            }
        });
        mPhotoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (!mPhotoRecyclerView.canScrollVertically(1)) {
                    fetchNextPage();
                }
            }
        });
        mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));

        mProgressBar = v.findViewById(R.id.progress_bar);

        updateUI(false);

        return v;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_photo_gallery, menu);

        final MenuItem searchItem = menu.findItem(R.id.menu_item_search);
        mSearchView = (SearchView) searchItem.getActionView();
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener(){
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.i(TAG, "QueryTextSubmit: " + query);
                submitQuery(query);
                hideKeyboard();
                searchItem.collapseActionView();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.i(TAG, "QueryTextChange: " + newText);
                return false;
            }
        });
        mSearchView.setOnSearchClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                String query = QueryPreferences.getStoredQuery(getActivity());
                mSearchView.setQuery(query, false);
            }
        });

        MenuItem toggleItem = menu.findItem(R.id.menu_item_toggle_polling);
        if(PollService.isServiceOn(getActivity())){
            toggleItem.setTitle(R.string.stop_polling);
        } else {
            toggleItem.setTitle(R.string.start_polling);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch(item.getItemId()){
            case R.id.menu_item_clear:
                mSearchView.setQuery(null, false); // Sets shown query to null
                submitQuery(null);
                return true;
            case R.id.menu_item_toggle_polling:
                boolean shouldStartService = !PollService.isServiceOn(getActivity());
                PollService.setService(getActivity(), shouldStartService);
                getActivity().invalidateOptionsMenu();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    @Override
    public void onDestroyView() {
        Log.i(TAG, "onDestroyView called");
        super.onDestroyView();
        mThumbnailDownloader.clearQueue();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy called");
        super.onDestroy();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Log.i(TAG, "onDetach called");
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Log.i(TAG, "onAttach called.");
    }

    /**
     * Updates the UI to show or hide the indeterminate progress bar.
     *
     * @param checkView Whether to check Views are in place before updating. Set to false if calling
     *                 from onCreateView and the view has been put in place.
     *
     */
    public void updateUI(boolean checkView){
        if(checkView && getView() == null) return; // Fragment not ready to access recycler view yet

        if(mLoadingPage && mNextPage == 1){
            mProgressBar.setVisibility(View.VISIBLE);
            mPhotoRecyclerView.setVisibility(View.GONE);
        } else {
            mProgressBar.setVisibility(View.GONE);
            mPhotoRecyclerView.setVisibility(View.VISIBLE);
        }

    }

    public void setItems(List<GalleryItem> items) {
        mItems = items;

        if (getView() == null) return; // Fragment not necessarily able to access recycler view yet
        PhotoAdapter adapter = (PhotoAdapter) mPhotoRecyclerView.getAdapter();
        adapter.setGalleryItems(mItems);
        adapter.notifyDataSetChanged();
    }

    private void addToItems(List<GalleryItem> items){
        int oldSize = mItems.size();
        mItems.addAll(items);

        if(getView() == null) return; // Fragment not necessarily attached to access recycler view
        PhotoAdapter adapter = (PhotoAdapter) mPhotoRecyclerView.getAdapter();
        adapter.setGalleryItems(mItems);
        // BY using notifyItemRangeInserted we always hold on to the same View Holders, which means
        // ThumbnailDownloader will cancel older fetches done for these ViewHolders when the user is
        // scrolling (as removeMessages is done against the ViewHolder hash code)
        adapter.notifyItemRangeInserted(oldSize, items.size());
    }

    private void submitQuery(String query){
        QueryPreferences.setStoredQuery(getActivity(), query);
        mThumbnailDownloader.clearQueue(); // Remove current fetches in progress
        mNextPage = 1; // Reset current page
        setItems(new ArrayList<GalleryItem>()); // Reset items
        fetchNextPage();
    }

    private void hideKeyboard() {
        View view = getActivity().getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void fetchNextPage() {
        if (mLoadingPage) return; // Previous task is still loading a page
        String query = QueryPreferences.getStoredQuery(getActivity());
        new FetchItemsTask(query).execute();
    }

    private class PhotoHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private ImageView mItemImageView;
        private GalleryItem mGalleryItem;

        public PhotoHolder(View itemView) {
            super(itemView);
            mItemImageView = itemView.findViewById(R.id.item_image_view);
            itemView.setOnClickListener(this);
        }

        public void bindDrawable(Drawable drawable) {
            mItemImageView.setImageDrawable(drawable);
        }

        public void bindGalleryItem(GalleryItem galleryItem){
            mGalleryItem = galleryItem;
        }

        @Override
        public void onClick(View view) {
            Intent i = new Intent(Intent.ACTION_VIEW, mGalleryItem.getPhotoPageUri());
            startActivity(i);
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {

        private List<GalleryItem> mGalleryItems;
        private Handler prefetchHandler = new Handler();

        public PhotoAdapter(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }

        public void setGalleryItems(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }

        @NonNull
        @Override
        public PhotoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.list_item_gallery, parent, false);
            Log.i(TAG, "Creating View Holder");
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoHolder photoHolder, final int position) {
            GalleryItem galleryItem = mGalleryItems.get(position);
            photoHolder.bindGalleryItem(galleryItem);
            Drawable placeholder = getResources().getDrawable(R.drawable.ana_blink);
            photoHolder.bindDrawable(placeholder);

            mThumbnailDownloader.queueThumbnail(photoHolder, galleryItem.getUrl());

            // Prefetch images for those 40 before and after this bound position. To stop flooding
            // the message queue with too many fetch requests for those scrolled out of view, we do
            // two things:
            // 1) We use a handler in this thread with a delay of 100ms, so nothing is run until an
            // the user stops scrolling.
            // 2) queuePrefetchUrls does a removal of messages already in queue.
            prefetchHandler.removeCallbacksAndMessages(null);
            prefetchHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    int start_idx = Math.max(0, position-40);
                    int end_idx = Math.min(mGalleryItems.size(), position+40);
                    List<String> prefetchUrls = new ArrayList<>();
                    for(GalleryItem prefetchItem : mGalleryItems.subList(start_idx, end_idx)) {
                        prefetchUrls.add(prefetchItem.getUrl());
                    }
                    mThumbnailDownloader.queuePrefetchUrls(prefetchUrls, true);
                }
            }, 100);

            // Fetch next page if we are binding the 25th-from-last element
            if(mGalleryItems.size() - position == 25){
                fetchNextPage();
            }

        }

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }
}