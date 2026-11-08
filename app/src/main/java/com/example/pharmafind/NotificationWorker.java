package com.example.pharmafind;

import android.content.Context;
import android.util.Log;

import androidx.work.ListenableWorker;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.Constraints;

import java.util.concurrent.TimeUnit;

/**
 * Background worker that checks for new notifications (pharmacy replies, new messages,
 * nearby customer requests) using {@link NotificationChecker#checkAsync(Context)}.
 *
 * Scheduled via {@link NotificationScheduler#schedulePeriodicCheck(Context)}.
 */
public class NotificationWorker extends Worker {

    private static final String TAG = "NotificationWorker";

    public NotificationWorker(Context context, WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        try {
            NotificationChecker.checkAsync(context);
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Notification check failed: " + e.getMessage(), e);
            return Result.failure();
        }
    }

    /**
     * Schedule the periodic notification check (every 15 minutes, flex window 5 min).
     * Uses an existing-work policy so only one periodic check is ever registered.
     *
     * Requires network; will back off automatically on failure (WorkManager default:
     * exponential backoff, initial 30s, max ~6h).
     */
    public static void schedulePeriodicCheck(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                NotificationWorker.class,
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag("notification_check")
                .build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        "notification_periodic",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request);
    }

    /**
     * Cancel any scheduled periodic notification checks.
     */
    public static void cancelPeriodicCheck(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork("notification_periodic");
    }
}
