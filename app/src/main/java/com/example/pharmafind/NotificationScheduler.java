package com.example.pharmafind;

import android.content.Context;

public class NotificationScheduler {

    /**
     * Runs a one-off notification check immediately (background thread).
     * Kept for backwards compatibility - used on app start / login.
     */
    public static void sync(Context context) {
        if (context == null) return;
        NotificationChecker.checkAsync(context);
    }

    /**
     * Schedule periodic background notification checks via WorkManager.
     * Runs every 15 minutes with exponential backoff on failure.
     * Requires network connectivity.
     */
    public static void schedulePeriodicCheck(Context context) {
        if (context == null) return;
        NotificationWorker.schedulePeriodicCheck(context);
    }

    /**
     * Cancel any scheduled periodic notification checks.
     */
    public static void cancelPeriodicCheck(Context context) {
        if (context == null) return;
        NotificationWorker.cancelPeriodicCheck(context);
    }
}
