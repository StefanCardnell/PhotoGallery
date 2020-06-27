package com.bignerdranch.android.photogallery;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.collection.LruCache;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ThumbnailDownloader<T> extends HandlerThread {

    private static final String TAG = "ThumbnailDownloader";

    private static final int MESSAGE_DOWNLOAD = 0;
    private static final int MESSAGE_PREFETCH = 1;

    private static final int LRU_CACHE_SIZE = 50 * 1024 * 1024; // 50MB cache

    private boolean mHasQuit = false;
    private Handler mRequestHandler;
    // mDownloadMap will hold the URL that currently needs to be fetched for a target T. It needs be
    // kept up to date and checked as Messages are created and processed, to ensure the currently
    // desired URL for a target is fetched.
    private ConcurrentMap<T, String> mDownloadMap = new ConcurrentHashMap<>();
    // Uses to ensure the same URL isn't being pre-fetched twice
    private ConcurrentMap<String, Boolean> mPrefetchMap = new ConcurrentHashMap<>();
    private Handler mResponseHandler;
    private ThumbnailDownloadListener<T> mThumbnailDownloadListener;
    private LruCache<String, Bitmap> mLruCache = new LruCache<String, Bitmap>(LRU_CACHE_SIZE){
        @Override
        protected int sizeOf(@NonNull String key, @NonNull Bitmap value) {
            return value.getByteCount();
        }
    };

    public interface ThumbnailDownloadListener<T> {
        void onThumbnailDownloaded(T target, Bitmap thumbnail);
    }

    public void setThumbnailDownloadListener(ThumbnailDownloadListener<T> listener) {
        mThumbnailDownloadListener = listener;
    }

    public ThumbnailDownloader(Handler responseHandler){
        super(TAG);
        mResponseHandler = responseHandler;
    }

    @SuppressLint("HandlerLeak")
    @Override
    protected void onLooperPrepared() {
        mRequestHandler = new Handler() {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if(msg.what == MESSAGE_DOWNLOAD) {
                    T target = (T) msg.obj;
                    Log.i(TAG, "Got a request to download URL: " + mDownloadMap.get(target));
                    handleRequest(target);
                } else if (msg.what == MESSAGE_PREFETCH) {
                    String url = (String) msg.obj;
                    Log.i(TAG, "Got a request to pre-fetch URL: " + url);
                    prefetchBitmap(url);
                }
            }
        };
    }

    @Override
    public boolean quit() {
        mHasQuit = true;
        return super.quit();
    }

    public void queueThumbnail(T target, String url){
        Log.i(TAG, "Got a URL: " + url);

        if(url == null){
            mDownloadMap.remove(target);
        } else if (mLruCache.get(url) != null) {
            Bitmap cachedBitmap = mLruCache.get(url);
            mThumbnailDownloadListener.onThumbnailDownloaded(target, cachedBitmap);
        }
        else {
            mDownloadMap.put(target, url);
            mRequestHandler.obtainMessage(MESSAGE_DOWNLOAD, target).sendToTarget();
        }
    }

    public void queuePrefetch(String url){
        if(url == null) return;
        else if(mLruCache.get(url) != null) return; // Already fetched and cached
        else if(mPrefetchMap.get(url) != null) return; // Fetch already in progress
        else {
            mPrefetchMap.put(url, true);
            mRequestHandler.obtainMessage(MESSAGE_PREFETCH, url).sendToTarget();
        }
    }

    public void clearQueue() {
        mRequestHandler.removeMessages(MESSAGE_PREFETCH);
        mRequestHandler.removeMessages(MESSAGE_DOWNLOAD);
        mPrefetchMap.clear();
        mDownloadMap.clear();
    }

    /**
     * Obtains and LRU caches a thumbnail at the specified URL.
     */
    private Bitmap getBitmap(String url) throws IOException {
        Bitmap bitmap;
        if(mLruCache.get(url) != null){
            bitmap = mLruCache.get(url);
        } else {
            byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
            bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);

            mLruCache.put(url, bitmap); // Cache for later
            int cacheUsage = (int) (100 * ((float) mLruCache.size() / LRU_CACHE_SIZE));
            Log.i(TAG, "Bitmap obtained and cached. Cache Usage: " + cacheUsage + "%");
        }
        return bitmap;
    }

    private void prefetchBitmap(String url){
        try{
            getBitmap(url);
            mPrefetchMap.remove(url);
        } catch (IOException ioe) {
            Log.e(TAG, "Error pre-fetching image", ioe);
            // By not removing mPrefetchMap in finally, the element can never be pre-fetched again.
        }

    }

    private void handleRequest(final T target){
        try {
            final String url = mDownloadMap.get(target);

            if (url == null) return;

            final Bitmap bitmap = getBitmap(url);

            mResponseHandler.post(new Runnable() {
                public void run() {
                    if( mDownloadMap.get(target) != url || mHasQuit) return;

                    mDownloadMap.remove(target);
                    mThumbnailDownloadListener.onThumbnailDownloaded(target, bitmap);
                }
            });

        } catch (IOException ioe) {
            Log.e(TAG, "Error downloading image", ioe);
        }

    }
}
