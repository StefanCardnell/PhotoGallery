package com.bignerdranch.android.photogallery;

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

public class PollService {

    private static boolean useJobScheduler(){
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static boolean isServiceOn(Context context){
        if(useJobScheduler()){
            return PollServiceJob.isJobScheduled(context);
        } else {
            return PollServiceIntent.isServiceAlarmOn(context);
        }
    }

    public static void setService(Context context, boolean isOn){
        if(useJobScheduler()){
            PollServiceJob.setJobSchedule(context, isOn);
        } else {
            PollServiceIntent.setServiceAlarm(context, isOn);
        }
        QueryPreferences.setServiceOn(context, isOn);
    }

}
