package com.example.pharmafind;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
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

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isPollingNotifs = false;

    private final Runnable notifRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPollingNotifs) {
                checkCustomerNotifications();
                mainHandler.postDelayed(this, 5000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_home);

        sqlHelper = new conSQL();
        session = new UserSessionManager(this);

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
        isPollingNotifs = true;
        checkCustomerNotifications();
        mainHandler.postDelayed(notifRunnable, 5000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isPollingNotifs = false;
        mainHandler.removeCallbacks(notifRunnable);
    }

    private void checkCustomerNotifications() {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) return;

            try {
                String msgQuery = "SELECT COUNT(*) AS MsgCount FROM dbo.Messages WHERE ReceiverID = ? AND SenderRole = 'pharmacy'";
                PreparedStatement msgStmt = con.prepareStatement(msgQuery);
                msgStmt.setInt(1, session.getUserId());
                ResultSet msgRs = msgStmt.executeQuery();
                int unreadMessages = 0;
                if (msgRs.next()) {
                    unreadMessages = msgRs.getInt("MsgCount");
                }
                msgStmt.close();

                final int count = unreadMessages;
                con.close();

                mainHandler.post(() -> {
                    if (bottomNav == null) return;
                    if (count > 0) {
                        BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.nav_feed);
                        badge.setNumber(count);
                        badge.setBackgroundColor(ContextCompat.getColor(CustomerHomeActivity.this, R.color.primary_teal));
                        badge.setVisible(true);
                    } else {
                        bottomNav.removeBadge(R.id.nav_feed);
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
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)
            .commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
