package com.example.pharmafind;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PharmacyHomeActivity extends AppCompatActivity {

    private conSQL sqlHelper;
    private UserSessionManager sessionManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pharmacy_home);

        sqlHelper = new conSQL();
        sessionManager = new UserSessionManager(this);

        TextView tvPharmacyWelcome = findViewById(R.id.tvPharmacyWelcome);
        TextView tvPharmacyLocation = findViewById(R.id.tvPharmacyLocation);
        MaterialButton btnLogout = findViewById(R.id.btnPharmacyLogout);
        BottomNavigationView bottomNav = findViewById(R.id.pharmacyBottomNavigation);

        // Push the header content below the status bar (Android 15+ draws the app behind it).
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

        // Default tab: Patient Requests Feed
        loadFragment(new FragmentPharmacyFeed());

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_pharmacy_feed) {
                loadFragment(new FragmentPharmacyFeed());
                return true;
            } else if (itemId == R.id.nav_pharmacy_profile) {
                loadFragment(new FragmentPharmacyProfile());
                return true;
            }
            return false;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.pharmacyFragmentContainer, fragment)
                .commit();
    }

    private void ensurePharmacyColumnsExist() {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) return;

            try {
                Statement stmt = con.createStatement();
                String query = "IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Pharmacies' AND COLUMN_NAME = 'Latitude') " +
                        "BEGIN " +
                        "ALTER TABLE dbo.Pharmacies ADD Latitude FLOAT, Longitude FLOAT, OperatingHours NVARCHAR(255), Bio NVARCHAR(MAX) " +
                        "END";
                stmt.executeUpdate(query);
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
