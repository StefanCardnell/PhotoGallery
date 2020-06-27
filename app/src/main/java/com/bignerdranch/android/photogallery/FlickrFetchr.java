package com.bignerdranch.android.photogallery;

import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class FlickrFetchr {

    private static final String TAG = "FlickrFetchr";

    private static final String API_KEY = "d01e31fd42a923d212e7cd23335f10e9";

    private int mConnectTimeout;
    private int mReadTimeout;

    public FlickrFetchr(){
        mReadTimeout = 0;
        mConnectTimeout = 0;
    }

    public FlickrFetchr(int timeout){
        mConnectTimeout = timeout;
        mReadTimeout = timeout;
    }

    public byte[] getUrlBytes(String urlSpec) throws IOException {
        URL url = new URL(urlSpec);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(mConnectTimeout);
        connection.setReadTimeout(mReadTimeout);

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream in = connection.getInputStream();

            if(connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException(connection.getResponseMessage() + ": with " + urlSpec);
            }

            int bytesRead = 0;
            byte[] buffer = new byte[1024];
            while((bytesRead = in.read(buffer)) > 0) {
                out.write(buffer, 0, bytesRead);
            }
            out.close();
            return out.toByteArray();

        } finally {
            connection.disconnect();
        }

    }

    public String getUrlString(String urlSpec) throws IOException {
        return new String(getUrlBytes(urlSpec));
    }

    public List<GalleryItem> fetchItems(int page) {

        List<GalleryItem> items = new ArrayList<>();

        try {
            String url = Uri.parse("https://api.flickr.com/services/rest/")
                    .buildUpon()
                    .appendQueryParameter("method", "flickr.photos.getRecent")
                    .appendQueryParameter("api_key", API_KEY)
                    .appendQueryParameter("format", "json")
                    .appendQueryParameter("nojsoncallback", "1")
                    .appendQueryParameter("extras", "url_s")
                    .appendQueryParameter("page", String.valueOf(page))
                    .build().toString();
            String jsonString = getUrlString(url);
            Log.i(TAG, "Received JSON: " + jsonString);
            parseItems(items, jsonString);
        } catch (IOException ioe) {
            Log.e(TAG, "Failed to fetch items", ioe);
        } catch (JsonSyntaxException je) {
            Log.e(TAG, "Failed to parse JSON", je);
        }

        return items;
    }

    private static class FlickrResult {
        public Photos photos;
        public static class Photos {
            public Photo[] photo;
            public static class Photo {
                public String id;
                public String title;
                public String url_s;
            }
        }
    }

    private void parseItems(List<GalleryItem> items, String jsonString) throws JsonSyntaxException {
        Gson gson = new Gson();
        FlickrResult flickrResult = gson.fromJson(jsonString, FlickrResult.class);
        for(int i = 0; i < flickrResult.photos.photo.length; ++i){
            FlickrResult.Photos.Photo photo = flickrResult.photos.photo[i];
            items.add(new GalleryItem(photo.title, photo.id, photo.url_s));
        }
    }

}
