package com.example.pharmafind;

import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class PharmaFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "PharmaFCM";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New Firebase Device Token: " + token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = "PharmaFind Alert";
        String body = "You have a new notification.";
        String type = "general";

        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }
            if (remoteMessage.getNotification().getBody() != null) {
                body = remoteMessage.getNotification().getBody();
            }
        }

        UserSessionManager sessionManager = new UserSessionManager(getApplicationContext());

        if (remoteMessage.getData().size() > 0) {
            if (remoteMessage.getData().containsKey("title")) {
                title = remoteMessage.getData().get("title");
            }
            if (remoteMessage.getData().containsKey("body")) {
                body = remoteMessage.getData().get("body");
            }
            if (remoteMessage.getData().containsKey("type")) {
                type = remoteMessage.getData().get("type");
            }

            // Suppress notification if sent by the logged-in user themselves
            if (remoteMessage.getData().containsKey("sender_id") && remoteMessage.getData().containsKey("sender_role")) {
                try {
                    int senderId = Integer.parseInt(remoteMessage.getData().get("sender_id"));
                    String senderRole = remoteMessage.getData().get("sender_role");
                    if (sessionManager.isLoggedIn() && senderId == sessionManager.getUserId() && senderRole != null && senderRole.equalsIgnoreCase(sessionManager.getRole())) {
                        Log.d(TAG, "Suppressed self-sent notification from " + senderRole + " ID: " + senderId);
                        return;
                    }
                } catch (Exception ignored) {}
            }
        }

        Intent intent;
        if ("chat".equalsIgnoreCase(type)) {
            intent = new Intent(this, ActivityChat.class);
        } else {
            intent = new Intent(this, MainActivity.class);
        }

        NotificationHelper.sendNotification(
                this,
                NotificationHelper.CHANNEL_URGENT_ID,
                (int) System.currentTimeMillis(),
                title,
                body,
                intent
        );
    }
}
