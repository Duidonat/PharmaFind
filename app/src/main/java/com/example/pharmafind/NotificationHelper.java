package com.example.pharmafind;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.activity.result.ActivityResultLauncher;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class NotificationHelper {

    // --- Channel IDs ---------------------------------------------------------------
    // Each notification type gets its own channel so the user can silence low-priority
    // messages (e.g. routine replies) while keeping urgent ones (nearby requests) on.

    public static final String CHANNEL_ID = "pharmafind_notifications";
    public static final String CHANNEL_NAME = "PharmaFind Notifications";
    public static final String CHANNEL_DESC = "Notifications for customer inquiries, pharmacy responses, and messages";

    public static final String CHANNEL_URGENT_ID = "pharmafind_urgent";
    public static final String CHANNEL_URGENT_NAME = "Urgent - Nearby Requests";
    public static final String CHANNEL_URGENT_DESC = "New customer medicine requests near your pharmacy";

    public static final String CHANNEL_CHATS_ID = "pharmafind_chats";
    public static final String CHANNEL_CHATS_NAME = "Chats";
    public static final String CHANNEL_CHATS_DESC = "New direct messages about your medicine requests";

    public static final String CHANNEL_REPLIES_ID = "pharmafind_replies";
    public static final String CHANNEL_REPLIES_NAME = "Pharmacy Responses";
    public static final String CHANNEL_REPLIES_DESC = "Replies from pharmacies to your medicine requests";

    public static final String CHANNEL_GENERAL_ID = "pharmafind_general";
    public static final String CHANNEL_GENERAL_NAME = "General";
    public static final String CHANNEL_GENERAL_DESC = "System notifications and reminders";

    // --- Channel creation ----------------------------------------------------------

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        // Legacy channel (kept for backwards compatibility with old notifications)
        NotificationChannel legacy = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        legacy.setDescription(CHANNEL_DESC);
        manager.createNotificationChannel(legacy);

        // Urgent - nearby customer requests (HIGH importance, sound + pop-up)
        NotificationChannel urgent = new NotificationChannel(
                CHANNEL_URGENT_ID, CHANNEL_URGENT_NAME, NotificationManager.IMPORTANCE_HIGH);
        urgent.setDescription(CHANNEL_URGENT_DESC);
        urgent.enableLights(true);
        urgent.setLightColor(ContextCompat.getColor(context, R.color.primary_teal));
        urgent.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(urgent);

        // Chats - new direct messages (HIGH importance, sound + heads-up banner)
        NotificationChannel chats = new NotificationChannel(
                CHANNEL_CHATS_ID, CHANNEL_CHATS_NAME, NotificationManager.IMPORTANCE_HIGH);
        chats.setDescription(CHANNEL_CHATS_DESC);
        chats.enableLights(true);
        chats.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(chats);

        // Replies - pharmacy responses to customer inquiries (HIGH importance, sound + heads-up banner)
        NotificationChannel replies = new NotificationChannel(
                CHANNEL_REPLIES_ID, CHANNEL_REPLIES_NAME, NotificationManager.IMPORTANCE_HIGH);
        replies.setDescription(CHANNEL_REPLIES_DESC);
        replies.enableLights(true);
        replies.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(replies);

        // General - system notifications (LOW importance, no sound)
        NotificationChannel general = new NotificationChannel(
                CHANNEL_GENERAL_ID, CHANNEL_GENERAL_NAME, NotificationManager.IMPORTANCE_LOW);
        general.setDescription(CHANNEL_GENERAL_DESC);
        general.enableLights(false);
        manager.createNotificationChannel(general);
    }

    public static void requestPermissionIfNeeded(Activity activity, ActivityResultLauncher<String> launcher) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    // --- Public send API -----------------------------------------------------------

    /**
     * Send a notification on a specific channel.
     *
     * @param context  application or activity context
     * @param channel  one of the CHANNEL_*_ID constants (or CHANNEL_ID for legacy)
     * @param notificationId  unique ID for this notification
     * @param title    notification title
     * @param body     notification body text
     * @param intent   tapped intent
     */
    public static void sendNotification(Context context, String channel, int notificationId,
                                        String title, String body, Intent intent) {
        createChannels(context);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        // Set priority based on channel
        switch (channel) {
            case CHANNEL_URGENT_ID:
            case CHANNEL_CHATS_ID:
            case CHANNEL_REPLIES_ID:
                builder.setPriority(NotificationCompat.PRIORITY_HIGH);
                break;
            case CHANNEL_GENERAL_ID:
                builder.setPriority(NotificationCompat.PRIORITY_LOW);
                break;
            default:
                builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
                break;
        }

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            manager.notify(notificationId, builder.build());
        }
    }

    public static void subscribeToFcmTopics() {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("pharmafind_all");
        } catch (Exception e) {
            android.util.Log.e("NotificationHelper", "FCM topic subscribe error: " + e.getMessage());
        }
    }

    public static void triggerRenderPushNotification(Context context, String title, String body, String type) {
        if (context == null) return;
        try {
            String url = "https://pharmafind-push-server.onrender.com/notify";
            org.json.JSONObject jsonBody = new org.json.JSONObject();
            jsonBody.put("title", title);
            jsonBody.put("body", body);
            jsonBody.put("type", type);

            com.android.volley.toolbox.JsonObjectRequest request = new com.android.volley.toolbox.JsonObjectRequest(
                com.android.volley.Request.Method.POST,
                url,
                jsonBody,
                response -> android.util.Log.d("NotificationHelper", "Render push triggered successfully"),
                error -> android.util.Log.e("NotificationHelper", "Render push trigger failed: " + error.getMessage())
            );

            com.android.volley.toolbox.Volley.newRequestQueue(context.getApplicationContext()).add(request);
        } catch (Exception e) {
            android.util.Log.e("NotificationHelper", "Error building Render push request: " + e.getMessage());
        }
    }

    // --- Convenience wrappers ------------------------------------------------------

    /** Nearby customer request - urgent, high priority. */
    public static void sendUrgentNotification(Context context, int notificationId, String title, String body, Intent intent) {
        sendNotification(context, CHANNEL_URGENT_ID, notificationId, title, body, intent);
    }

    /** New direct message - default priority. */
    public static void sendChatNotification(Context context, int notificationId, String title, String body, Intent intent) {
        sendNotification(context, CHANNEL_CHATS_ID, notificationId, title, body, intent);
    }

    /** Pharmacy response - default priority. */
    public static void sendReplyNotification(Context context, int notificationId, String title, String body, Intent intent) {
        sendNotification(context, CHANNEL_REPLIES_ID, notificationId, title, body, intent);
    }

    /** System/general notification - low priority. */
    public static void sendGeneralNotification(Context context, int notificationId, String title, String body, Intent intent) {
        sendNotification(context, CHANNEL_GENERAL_ID, notificationId, title, body, intent);
    }

    /** @deprecated Use the channel-specific methods instead. */
    @Deprecated
    public static void sendNotification(Context context, int notificationId, String title, String body, Intent intent) {
        sendNotification(context, CHANNEL_ID, notificationId, title, body, intent);
    }
}
