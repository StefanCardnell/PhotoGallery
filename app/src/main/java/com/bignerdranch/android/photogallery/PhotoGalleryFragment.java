package com.bignerdranch.android.photogallery;

import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class PhotoGalleryFragment extends Fragment {

    private static final String TAG = "PhotoGalleryFragment";

    private boolean mLoadingPage = false;
    private int mNextPage = 1;
    private List<GalleryItem> mItems = new ArrayList<>();

    private RecyclerView mPhotoRecyclerView;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    private class FetchItemsTask extends AsyncTask<Void, Void, List<GalleryItem>> {
        @Override
        protected List<GalleryItem> doInBackground(Void... voids) {
            return new FlickrFetchr().fetchItems(mNextPage);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mLoadingPage = true;
            Toast.makeText(getActivity(), "Loading next page...", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected void onPostExecute(List<GalleryItem> items) {
            mItems.addAll(items);
            mNextPage++;
            setupAdapter();
            mLoadingPage = false;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        fetchNextPage();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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

        setupAdapter();

        return v;
    }

    private void setupAdapter() {
        if (!isAdded()) return; // Fragment not necessarily attached to access recycler view

        PhotoAdapter adapter = (PhotoAdapter) mPhotoRecyclerView.getAdapter();
        if (adapter == null) {
            mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
        } else {
            adapter.setGalleryItems(mItems);
            adapter.notifyDataSetChanged();
        }
    }

    private void fetchNextPage() {
        if (mLoadingPage) return; // Previous task is still loading a page
        new FetchItemsTask().execute();
    }

    private class PhotoHolder extends RecyclerView.ViewHolder {
        private TextView mTitleTextView;

        public PhotoHolder(View itemView) {
            super(itemView);
            mTitleTextView = (TextView) itemView;
        }

        public void bindGalleryItem(GalleryItem item) {
            mTitleTextView.setText(item.toString());
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {

        private List<GalleryItem> mGalleryItems;

        public PhotoAdapter(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }

        public void setGalleryItems(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }

        @NonNull
        @Override
        public PhotoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView textView = new TextView(getActivity());
            return new PhotoHolder(textView);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoHolder holder, int position) {
            GalleryItem galleryItem = mGalleryItems.get(position);
            holder.bindGalleryItem(galleryItem);
        }

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }
}