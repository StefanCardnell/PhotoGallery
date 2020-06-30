package com.bignerdranch.android.photogallery;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.List;

public class Poller {

    public static final String ACTION_SHOW_NOTIFICATION =
            "com.bignerdranch.android.photogallery.SHOW_NOTIFICATION";
    public static final String PERM_PRIVATE =
            "com.bignerdranch.android.photogallery.PRIVATE";

    public static final String REQUEST_CODE = "REQUEST_CODE";
    public static final String NOTIFICATION = "NOTIFICATION";
    
    private String mTag;
    private Context mContext;
    
    public Poller(Context context, String tag){
        mContext = context;
        mTag = tag;
    }

    public void pollLatest(){
        String query = QueryPreferences.getStoredQuery(mContext);
        String lastResultId = QueryPreferences.getLastResultId(mContext);

        List<GalleryItem> items;

        if(query == null || query.isEmpty()){
            items = new FlickrFetchr().fetchRecentPhotos(0);
        } else {
            items = new FlickrFetchr().searchPhotos(query, 0);
        }

        if (items.size() == 0) {
            return;
        }

        String resultId = items.get(0).getId();
        if(resultId.equals(lastResultId)){
            Log.i(mTag, "Got an old result: " + resultId);
        } else {
            Log.i(mTag, "Got a new result: " + resultId);

            Resources resources = mContext.getResources();
            Intent i = PhotoGalleryActivity.newIntent(mContext);
            PendingIntent pi = PendingIntent.getActivity(mContext, 0, i, 0);

            Notification notification = new NotificationCompat.Builder(mContext)
                    .setTicker(resources.getString(R.string.new_pictures_title))
                    .setSmallIcon(android.R.drawable.ic_menu_report_image)
                    .setContentTitle(resources.getString(R.string.new_pictures_title))
                    .setContentText(resources.getString(R.string.new_pictures_text))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();

            showBackgroundNotification(0, notification);

        }

        QueryPreferences.setLastResultId(mContext, resultId);
    }

    private void showBackgroundNotification(int requestCode, Notification notification) {
        Intent i = new Intent(ACTION_SHOW_NOTIFICATION);
        i.putExtra(REQUEST_CODE, requestCode);
        i.putExtra(NOTIFICATION, notification);
        mContext.sendOrderedBroadcast(i, PERM_PRIVATE, null, null, Activity.RESULT_OK, null, null);
    }
    
    
}
