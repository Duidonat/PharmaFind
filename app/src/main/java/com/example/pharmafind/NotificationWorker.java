package com.example.pharmafind;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Background check, run by WorkManager about every 15 minutes while the user is logged in. */
public class NotificationWorker extends Worker {

    public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            NotificationChecker.check(getApplicationContext(), true);
        } catch (Exception e) {
            Log.e("NotificationWorker", "Background check failed: " + e.getMessage());
        }
        // Always succeed: the next periodic run is the retry.
        return Result.success();
    }
}
