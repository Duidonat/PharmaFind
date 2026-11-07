package com.example.pharmafind;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Starts / stops the background check. Android's minimum interval for periodic work is 15 minutes. */
public final class NotificationScheduler {

    private static final String WORK_NAME = "pharmafind_notification_check";

    private NotificationScheduler() {}

    /**
     * Schedule only for sessions that are meant to survive the app being closed. Sessions without
     * "Remember me" end when the app process dies (see PharmaFindApp), so background checks make no sense for them.
     */
    public static void sync(Context context) {
        UserSessionManager session = new UserSessionManager(context);
        if (session.isLoggedIn() && session.getRememberMe()) {
            schedule(context);
        } else {
            cancel(context);
        }
    }

    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                NotificationWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(WORK_NAME);
    }
}
