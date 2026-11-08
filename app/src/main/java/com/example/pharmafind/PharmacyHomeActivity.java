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
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PharmacyHomeActivity extends AppCompatActivity {

    private conSQL sqlHelper;
    private UserSessionManager sessionManager;
    private BottomNavigationView bottomNav;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {});

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pharmacy_home);

        sqlHelper = new conSQL();
        sessionManager = new UserSessionManager(this);

        NotificationHelper.createChannels(this);
        NotificationHelper.requestPermissionIfNeeded(this, requestPermissionLauncher);
        NotificationHelper.subscribeToFcmTopics();
        NotificationScheduler.sync(this);
        NotificationScheduler.schedulePeriodicCheck(this);

        TextView tvPharmacyWelcome = findViewById(R.id.tvPharmacyWelcome);
        TextView tvPharmacyLocation = findViewById(R.id.tvPharmacyLocation);
        MaterialButton btnLogout = findViewById(R.id.btnPharmacyLogout);
        bottomNav = findViewById(R.id.pharmacyBottomNavigation);

        View headerContent = findViewById(R.id.headerContentPharmacy);
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

        tvPharmacyWelcome.setText("Welcome, " + sessionManager.getFullName());
        tvPharmacyLocation.setText("Barangay " + sessionManager.getBarangay() + ", " + sessionManager.getCity());

        btnLogout.setOnClickListener(v -> {
            sessionManager.logoutUser();
            startActivity(new Intent(PharmacyHomeActivity.this, MainActivity.class));
            finish();
        });

        ensurePharmacyColumnsExist();

        loadFragment(new FragmentPharmacyFeed());

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_pharmacy_feed) {
                loadFragment(new FragmentPharmacyFeed());
                return true;
            } else if (itemId == R.id.nav_pharmacy_notifications) {
                loadFragment(new FragmentPharmacyNotifications());
                return true;
            } else if (itemId == R.id.nav_chats) {
                loadFragment(new FragmentChats());
                return true;
            } else if (itemId == R.id.nav_pharmacy_profile) {
                loadFragment(new FragmentPharmacyProfile());
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        NotificationChecker.checkAsync(this);
        checkPharmacyNotifications();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void checkPharmacyNotifications() {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) return;

            try {
                NotificationState state = new NotificationState(PharmacyHomeActivity.this);
                int pharmacyId = sessionManager.getUserId();
                int lastMsg = state.get("pharmacy", pharmacyId, "msg");

                if (lastMsg < 0) {
                    String maxMsgSql = "SELECT ISNULL(MAX(MessageID), 0) FROM dbo.Messages WHERE ReceiverID = ? AND SenderRole = 'customer'";
                    PreparedStatement maxPs = con.prepareStatement(maxMsgSql);
                    maxPs.setInt(1, pharmacyId);
                    ResultSet rs = maxPs.executeQuery();
                    if (rs.next()) lastMsg = rs.getInt(1);
                    maxPs.close();
                    state.set("pharmacy", pharmacyId, "msg", lastMsg);
                }

                String msgQuery = "SELECT COUNT(*) FROM dbo.Messages m JOIN dbo.Inquiries i ON i.InquiryID = m.InquiryID WHERE m.ReceiverID = ? AND m.SenderRole = 'customer' AND m.MessageID > ? AND LOWER(ISNULL(i.Status, 'open')) <> 'fulfilled'";
                PreparedStatement msgStmt = con.prepareStatement(msgQuery);
                msgStmt.setInt(1, pharmacyId);
                msgStmt.setInt(2, lastMsg);
                ResultSet msgRs = msgStmt.executeQuery();
                int unreadMessages = msgRs.next() ? msgRs.getInt(1) : 0;
                msgStmt.close();

                final int count = unreadMessages;
                con.close();

                mainHandler.post(() -> {
                    if (bottomNav == null) return;
                    if (count > 0) {
                        BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.nav_pharmacy_notifications);
                        badge.setNumber(count);
                        badge.setBackgroundColor(ContextCompat.getColor(PharmacyHomeActivity.this, R.color.primary_teal));
                        badge.setVisible(true);
                    } else {
                        bottomNav.removeBadge(R.id.nav_pharmacy_notifications);
                    }
                });

            } catch (SQLException e) {
                try { con.close(); } catch (SQLException ignored) {}
                Log.e("PharmacyHomeActivity", "Error checking notifications: " + e.getMessage());
            }
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.fragment_fade_enter, R.anim.fragment_fade_exit)
                .replace(R.id.pharmacyFragmentContainer, fragment)
                .commit();
    }

    private void ensurePharmacyColumnsExist() {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) return;

            try {
                Statement stmt = con.createStatement();
                String[] addColumns = {
                        "Latitude DECIMAL(10, 8) NULL",
                        "Longitude DECIMAL(11, 8) NULL",
                        "OperatingHours NVARCHAR(255) NULL",
                        "Bio NVARCHAR(MAX) NULL"
                };
                for (String column : addColumns) {
                    String name = column.substring(0, column.indexOf(' '));
                    stmt.executeUpdate("IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Pharmacies' AND COLUMN_NAME = '" + name + "') " +
                            "ALTER TABLE dbo.Pharmacies ADD " + column);
                }
                con.close();
            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
                Log.e("PharmacyHomeActivity", "Error adding columns: " + e.getMessage());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
