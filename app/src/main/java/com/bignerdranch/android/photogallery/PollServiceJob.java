package com.bignerdranch.android.photogallery;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.concurrent.TimeUnit;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class PollServiceJob extends JobService {
    private static final String TAG = "PollServiceJob";

    private static final long POLL_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1);

    private static final int JOB_ID = 1;

    private PollTask mCurrentTask;

    private class PollTask extends AsyncTask<JobParameters, Void, Void> {

        @Override
        protected Void doInBackground(JobParameters... params) {
            JobParameters jobParams = params[0];
            new Poller(PollServiceJob.this, TAG).pollLatest();
            jobFinished(jobParams, false);
            return null;
        }
    }

    public static void setJobSchedule(Context context, boolean isOn){
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(isOn){
            JobInfo jobInfo = new JobInfo.Builder(
                    JOB_ID, new ComponentName(context, PollServiceJob.class))
//                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                    .setPeriodic(POLL_INTERVAL_MS)
                    .setPersisted(true)
                    .build();
            scheduler.schedule(jobInfo);
            Log.i(TAG, "Service started. Executing every " + POLL_INTERVAL_MS);
        } else {
            scheduler.cancel(JOB_ID);
            Log.i(TAG, "Service stopped.");
        }
    }

    public static boolean isJobScheduled(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        for(JobInfo jobInfo : scheduler.getAllPendingJobs()){
            if(jobInfo.getId() == JOB_ID) return true;
        }

        return false;
    }

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        mCurrentTask = new PollTask();
        mCurrentTask.execute(jobParameters);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        if(mCurrentTask != null) mCurrentTask.cancel(true);
        return true;
    }


}
