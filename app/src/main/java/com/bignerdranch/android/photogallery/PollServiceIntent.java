package com.bignerdranch.android.photogallery;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.concurrent.TimeUnit;

public class PollServiceIntent extends IntentService {
    private static final String TAG = "PollServiceIntent";

    private static final long POLL_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1);

    public static Intent newIntent(Context context){
        return new Intent(context, PollServiceIntent.class);
    }

    public PollServiceIntent(){
        super(TAG);
    }

    public static void setServiceAlarm(Context context, boolean isOn) {
        Intent i = PollServiceIntent.newIntent(context);
        PendingIntent pi = PendingIntent.getService(context, 0, i , 0);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if(isOn) {
            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime(), POLL_INTERVAL_MS, pi);
            Log.i(TAG, "Service started. Executing every " + POLL_INTERVAL_MS);
        } else {
            alarmManager.cancel(pi);
            pi.cancel();
            Log.i(TAG, "Service stopped.");
        }
    }

    public static boolean isServiceAlarmOn(Context context){
        Intent i = PollServiceIntent.newIntent(context);
        PendingIntent pi = PendingIntent.getService(context, 0, i, PendingIntent.FLAG_NO_CREATE);
        return pi != null;
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if(!isNetworkAvailableAndConnected()) return;
        new Poller(this, TAG).pollLatest();
    }

    private boolean isNetworkAvailableAndConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        boolean isNetworkAvailable = cm.getActiveNetworkInfo() != null;
        boolean isNetworkConnected = isNetworkAvailable && cm.getActiveNetworkInfo().isConnected();

        return isNetworkConnected;
    }
}
