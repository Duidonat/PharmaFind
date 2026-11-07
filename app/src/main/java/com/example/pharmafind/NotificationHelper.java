package com.example.pharmafind;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

/**
 * Creates the notification channels and builds / posts every PharmaFind notification.
 * Lock-screen versions are deliberately generic: medicine names and chat text are health-related,
 * so they are hidden until the phone is unlocked.
 */
public final class NotificationHelper {

    public static final String CHANNEL_REPLIES = "pharmacy_replies";
    public static final String CHANNEL_MESSAGES = "chat_messages";
    public static final String CHANNEL_REQUESTS = "new_requests";
    public static final String CHANNEL_ACCOUNT = "account_updates";

    // Separate ID ranges so a reply, a chat and a request for the same inquiry never overwrite each other.
    private static final int ID_REPLY_BASE = 100_000;
    private static final int ID_MESSAGE_BASE = 200_000;
    private static final int ID_REQUESTS = 300_000;
    private static final int ID_ACCOUNT = 300_001;

    // Set by ActivityChat while a conversation is on screen, so it doesn't notify about itself.
    private static volatile int activeChatInquiryId = -1;

    private NotificationHelper() {}

    public static void setActiveChat(int inquiryId) {
        activeChatInquiryId = inquiryId;
    }

    // ---------------------------------------------------------------- setup

    public static void createChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;

        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_REPLIES, "Pharmacy replies", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_MESSAGES, "Chat messages", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_REQUESTS, "New requests nearby", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ACCOUNT, "Account updates", NotificationManager.IMPORTANCE_DEFAULT));
    }

    /** Android 13+ needs a runtime permission. Asked once; after that the user can use system settings. */
    public static void requestPermissionIfNeeded(Context ctx, ActivityResultLauncher<String> launcher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        NotificationState state = new NotificationState(ctx);
        if (state.wasPermissionAsked()) return;
        state.setPermissionAsked();
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    public static boolean canNotify(Context ctx) {
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled();
    }

    public static void cancelChat(Context ctx, int inquiryId) {
        NotificationManagerCompat.from(ctx).cancel(ID_MESSAGE_BASE + inquiryId);
    }

    public static void cancelAll(Context ctx) {
        NotificationManagerCompat.from(ctx).cancelAll();
    }

    // -------------------------------------------------------- notifications

    /** Customer side: one or more pharmacies replied to a request. */
    public static void notifyReplies(Context ctx, int inquiryId, String medicine, int count,
                                     String lastPharmacy, String lastAvailability) {
        String med = isBlank(medicine) ? "your request" : medicine;
        String pharmacy = isBlank(lastPharmacy) ? "A pharmacy" : lastPharmacy;

        String title;
        String text;
        if (count <= 1) {
            title = pharmacy + " replied";
            text = isBlank(lastAvailability)
                    ? "New reply to your request for " + med
                    : med + ": " + lastAvailability;
        } else {
            title = count + " pharmacies replied";
            text = "New replies to your request for " + med;
        }

        Intent target = new Intent(ctx, ActivityInquiryThread.class);
        target.putExtra("inquiry_id", inquiryId);

        post(ctx, ID_REPLY_BASE + inquiryId, CHANNEL_REPLIES, NotificationCompat.CATEGORY_SOCIAL,
                title, text, "A pharmacy replied to your request",
                stack(ctx, false, target, ID_REPLY_BASE + inquiryId),
                NotificationCompat.PRIORITY_HIGH);
    }

    /** Either side: new chat message(s) about one inquiry. */
    public static void notifyMessages(Context ctx, boolean forPharmacy, int inquiryId, int senderId,
                                      String senderName, int count, String lastText) {
        if (inquiryId == activeChatInquiryId) return; // the chat screen is already showing it

        String name = isBlank(senderName) ? (forPharmacy ? "Customer" : "Pharmacy") : senderName;
        String title = name;
        String text = count <= 1 ? (isBlank(lastText) ? "New message" : lastText)
                                 : count + " new messages";

        Intent target = new Intent(ctx, ActivityChat.class);
        target.putExtra("inquiry_id", inquiryId);
        target.putExtra("receiver_id", senderId);
        target.putExtra("receiver_name", name);

        post(ctx, ID_MESSAGE_BASE + inquiryId, CHANNEL_MESSAGES, NotificationCompat.CATEGORY_MESSAGE,
                title, text, "You have a new message",
                stack(ctx, forPharmacy, target, ID_MESSAGE_BASE + inquiryId),
                NotificationCompat.PRIORITY_HIGH);
    }

    /** Pharmacy side: new open request(s) within range. */
    public static void notifyNewRequests(Context ctx, int count, int newestInquiryId, String newestMedicine) {
        String title;
        String text;
        Intent target = null;
        if (count <= 1) {
            title = "New request nearby";
            text = isBlank(newestMedicine) ? "A customer is looking for a medicine" : "Looking for " + newestMedicine;
            target = new Intent(ctx, ActivityInquiryThread.class);
            target.putExtra("inquiry_id", newestInquiryId);
        } else {
            title = count + " new requests nearby";
            text = "Customers near you are looking for medicines";
        }

        post(ctx, ID_REQUESTS, CHANNEL_REQUESTS, NotificationCompat.CATEGORY_RECOMMENDATION,
                title, text, "New medicine request nearby",
                stack(ctx, true, target, ID_REQUESTS),
                NotificationCompat.PRIORITY_DEFAULT);
    }

    /** Pharmacy side: the registration status changed (approved / rejected). */
    public static void notifyAccountStatus(Context ctx, boolean approved) {
        String title = approved ? "Your pharmacy was approved" : "Registration update";
        String text = approved
                ? "You can now see and reply to medicine requests."
                : "Your pharmacy registration was not approved. Open the app for details.";

        post(ctx, ID_ACCOUNT, CHANNEL_ACCOUNT, NotificationCompat.CATEGORY_STATUS,
                title, text, title,
                stack(ctx, true, null, ID_ACCOUNT),
                NotificationCompat.PRIORITY_DEFAULT);
    }

    // -------------------------------------------------------------- internals

    @SuppressLint("MissingPermission") // areNotificationsEnabled() covers the 13+ permission; SecurityException is caught too
    private static void post(Context ctx, int id, String channel, String category,
                             String title, String text, String lockScreenText,
                             @Nullable PendingIntent contentIntent, int priority) {
        if (!canNotify(ctx)) return;

        Notification publicVersion = new NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(R.drawable.ic_notifications)
                .setColor(ContextCompat.getColor(ctx, R.color.primary_teal))
                .setContentTitle(ctx.getString(R.string.app_name))
                .setContentText(lockScreenText)
                .build();

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(R.drawable.ic_notifications)
                .setColor(ContextCompat.getColor(ctx, R.color.primary_teal))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(category)
                .setPriority(priority)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion);
        if (contentIntent != null) b.setContentIntent(contentIntent);

        try {
            NotificationManagerCompat.from(ctx).notify(id, b.build());
        } catch (SecurityException ignored) {
            // Permission revoked between the check and the call - nothing to do.
        }
    }

    /** Tapping opens the target with the right home screen underneath it, so Back behaves normally. */
    @Nullable
    private static PendingIntent stack(Context ctx, boolean forPharmacy, @Nullable Intent target, int requestCode) {
        TaskStackBuilder builder = TaskStackBuilder.create(ctx);
        builder.addNextIntent(new Intent(ctx,
                forPharmacy ? PharmacyHomeActivity.class : CustomerHomeActivity.class));
        if (target != null) builder.addNextIntent(target);
        return builder.getPendingIntent(requestCode,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
