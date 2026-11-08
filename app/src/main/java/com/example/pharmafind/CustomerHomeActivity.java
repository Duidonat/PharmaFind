package com.example.pharmafind;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CustomerHomeActivity extends AppCompatActivity {

    private conSQL sqlHelper;
    private UserSessionManager session;
    private TextView tvWelcomeUser, tvUserLocation;
    private BottomNavigationView bottomNav;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {});

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_home);

        sqlHelper = new conSQL();
        session = new UserSessionManager(this);

        NotificationHelper.createChannels(this);
        NotificationHelper.requestPermissionIfNeeded(this, requestPermissionLauncher);
        NotificationHelper.subscribeToFcmTopics();
        NotificationScheduler.sync(this);
        NotificationScheduler.schedulePeriodicCheck(this);

        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    android.util.Log.d("PharmaFCM", "FCM Registration Token: " + task.getResult());
                }
            });

        tvWelcomeUser = findViewById(R.id.tvWelcomeUser);
        tvUserLocation = findViewById(R.id.tvUserLocation);
        MaterialButton btnLogout = findViewById(R.id.btnLogout);
        bottomNav = findViewById(R.id.bottomNavigation);

        View headerContent = findViewById(R.id.headerContent);
        if (headerContent != null) {
            int baseTopPadding = headerContent.getPaddingTop();
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
                Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                headerContent.setPadding(
                    headerContent.getPaddingLeft(),
                    baseTopPadding + statusBars.top,
                    headerContent.getPaddingRight(),
                    headerContent.getPaddingBottom()
                );
                return insets;
            });
        }

        tvWelcomeUser.setText("Welcome, " + session.getFullName() + "!");

        String locationText = "Barangay " + session.getBarangay() + ", " + session.getCity();
        if (!session.getDistrict().isEmpty()) {
            locationText += " (" + session.getDistrict() + ")";
        }
        tvUserLocation.setText(locationText);

        btnLogout.setOnClickListener(v -> {
            session.logoutUser();
            startActivity(new Intent(CustomerHomeActivity.this, MainActivity.class));
            finish();
        });

        loadFragment(new FragmentCustomerFeed());

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_feed) {
                loadFragment(new FragmentCustomerFeed());
                return true;
            } else if (itemId == R.id.nav_customer_notifications) {
                loadFragment(new FragmentCustomerNotifications());
                return true;
            } else if (itemId == R.id.nav_chats) {
                loadFragment(new FragmentChats());
                return true;
            } else if (itemId == R.id.nav_profile) {
                loadFragment(new FragmentCustomerProfile());
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        NotificationChecker.checkAsync(this);
        checkCustomerNotifications();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void checkCustomerNotifications() {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) return;

            try {
                NotificationState state = new NotificationState(CustomerHomeActivity.this);
                int customerId = session.getUserId();
                int lastReply = state.get("customer", customerId, "reply");
                int lastMsg = state.get("customer", customerId, "msg");

                if (lastReply < 0) {
                    String maxReplySql = "SELECT ISNULL(MAX(r.ReplyID), 0) FROM dbo.PharmacyReplies r JOIN dbo.Inquiries i ON i.InquiryID = r.InquiryID WHERE i.CustomerID = ?";
                    PreparedStatement maxPs = con.prepareStatement(maxReplySql);
                    maxPs.setInt(1, customerId);
                    ResultSet rs = maxPs.executeQuery();
                    if (rs.next()) lastReply = rs.getInt(1);
                    maxPs.close();
                    state.set("customer", customerId, "reply", lastReply);
                }

                if (lastMsg < 0) {
                    String maxMsgSql = "SELECT ISNULL(MAX(MessageID), 0) FROM dbo.Messages WHERE ReceiverID = ? AND SenderRole = 'pharmacy'";
                    PreparedStatement maxPs = con.prepareStatement(maxMsgSql);
                    maxPs.setInt(1, customerId);
                    ResultSet rs = maxPs.executeQuery();
                    if (rs.next()) lastMsg = rs.getInt(1);
                    maxPs.close();
                    state.set("customer", customerId, "msg", lastMsg);
                }

                String replyQuery = "SELECT COUNT(*) FROM dbo.PharmacyReplies r JOIN dbo.Inquiries i ON i.InquiryID = r.InquiryID WHERE i.CustomerID = ? AND r.ReplyID > ? AND LOWER(ISNULL(i.Status, 'open')) <> 'fulfilled'";
                PreparedStatement replyStmt = con.prepareStatement(replyQuery);
                replyStmt.setInt(1, customerId);
                replyStmt.setInt(2, lastReply);
                ResultSet replyRs = replyStmt.executeQuery();
                int unreadReplies = replyRs.next() ? replyRs.getInt(1) : 0;
                replyStmt.close();

                String msgQuery = "SELECT COUNT(*) FROM dbo.Messages m JOIN dbo.Inquiries i ON i.InquiryID = m.InquiryID WHERE m.ReceiverID = ? AND m.SenderRole = 'pharmacy' AND m.MessageID > ? AND LOWER(ISNULL(i.Status, 'open')) <> 'fulfilled'";
                PreparedStatement msgStmt = con.prepareStatement(msgQuery);
                msgStmt.setInt(1, customerId);
                msgStmt.setInt(2, lastMsg);
                ResultSet msgRs = msgStmt.executeQuery();
                int unreadMessages = msgRs.next() ? msgRs.getInt(1) : 0;
                msgStmt.close();

                final int totalUnread = unreadReplies + unreadMessages;
                con.close();

                mainHandler.post(() -> {
                    if (bottomNav == null) return;
                    if (totalUnread > 0) {
                        BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.nav_customer_notifications);
                        badge.setNumber(totalUnread);
                        badge.setBackgroundColor(ContextCompat.getColor(CustomerHomeActivity.this, R.color.primary_teal));
                        badge.setVisible(true);
                    } else {
                        bottomNav.removeBadge(R.id.nav_customer_notifications);
                    }
                });

            } catch (SQLException e) {
                try { con.close(); } catch (SQLException ignored) {}
                Log.e("CustomerHomeActivity", "Error checking notifications: " + e.getMessage());
            }
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
            .beginTransaction()
            .setCustomAnimations(R.anim.fragment_fade_enter, R.anim.fragment_fade_exit)
            .replace(R.id.fragmentContainer, fragment)
            .commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
