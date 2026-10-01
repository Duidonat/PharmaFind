package com.example.pharmafind;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

public class CustomerHomeActivity extends AppCompatActivity {

    private UserSessionManager session;
    private TextView tvWelcomeUser, tvUserLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_home);

        session = new UserSessionManager(this);

        tvWelcomeUser = findViewById(R.id.tvWelcomeUser);
        tvUserLocation = findViewById(R.id.tvUserLocation);
        MaterialButton btnLogout = findViewById(R.id.btnLogout);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);

        // Push the header content below the status bar (Android 15+ draws the app behind it).
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

        // Display logged-in user details
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

        // Set default fragment to Feed
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

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit();
    }
}
