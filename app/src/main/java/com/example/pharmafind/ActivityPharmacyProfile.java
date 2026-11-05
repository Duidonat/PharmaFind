package com.example.pharmafind;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActivityPharmacyProfile extends AppCompatActivity {

    private conSQL sqlHelper;
    private int pharmacyId = -1;

    private TextView tvPharmacyNameTitle, tvPharmacyAddress, tvContactInfo, tvEmailInfo, tvOperatingHours, tvStatusInfo, tvPharmacyVerifiedBadge;

    private String fullAddressForMaps = "";
    private String contactPhone = "";
    private double pharmacyLat = 0.0;
    private double pharmacyLng = 0.0;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pharmacy_profile);

        sqlHelper = new conSQL();
        pharmacyId = getIntent().getIntExtra("pharmacy_id", -1);

        if (pharmacyId == -1) {
            // Toast, not UiMessage: this screen closes right after, which would take a Snackbar with it.
            Toast.makeText(this, "Pharmacy Not Found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ImageButton btnBack = findViewById(R.id.btnBackPharmacyProfile);
        btnBack.setOnClickListener(v -> finish());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (btnBack.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                android.view.ViewGroup.MarginLayoutParams params = (android.view.ViewGroup.MarginLayoutParams) btnBack.getLayoutParams();
                params.topMargin = systemBars.top + 16;
                btnBack.setLayoutParams(params);
            }
            return insets;
        });

        tvPharmacyNameTitle = findViewById(R.id.tvPharmacyNameTitle);
        tvPharmacyAddress = findViewById(R.id.tvPharmacyAddress);
        tvContactInfo = findViewById(R.id.tvContactInfo);
        tvEmailInfo = findViewById(R.id.tvEmailInfo);
        tvOperatingHours = findViewById(R.id.tvOperatingHours);
        tvStatusInfo = findViewById(R.id.tvStatusInfo);
        tvPharmacyVerifiedBadge = findViewById(R.id.tvPharmacyVerifiedBadge);

        MaterialButton btnOpenMaps = findViewById(R.id.btnOpenMaps);
        MaterialButton btnCallPharmacy = findViewById(R.id.btnCallPharmacy);
        MaterialButton btnChatPharmacy = findViewById(R.id.btnChatPharmacy);
        int inquiryId = getIntent().getIntExtra("inquiry_id", -1);

        btnOpenMaps.setOnClickListener(v -> openPharmacyOnGoogleMaps());

        btnCallPharmacy.setOnClickListener(v -> {
            if (!contactPhone.isEmpty()) {
                Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + contactPhone));
                startActivity(callIntent);
            } else {
                UiMessage.show(this, "No phone number available");
            }
        });

        if (btnChatPharmacy != null) {
            btnChatPharmacy.setOnClickListener(v -> {
                if (inquiryId != -1) {
                    Intent chatIntent = new Intent(ActivityPharmacyProfile.this, ActivityChat.class);
                    chatIntent.putExtra("inquiry_id", inquiryId);
                    chatIntent.putExtra("receiver_id", pharmacyId);
                    chatIntent.putExtra("receiver_name", tvPharmacyNameTitle.getText().toString());
                    chatIntent.putExtra("pharmacy_id", pharmacyId);
                    startActivity(chatIntent);
                } else {
                    UiMessage.show(ActivityPharmacyProfile.this, "You can chat with this pharmacy after they respond to a Medicine Request.");
                }
            });
        }

        loadPharmacyDetails();
    }

    private void openPharmacyOnGoogleMaps() {
        try {
            Uri mapUri;
            if (pharmacyLat != 0.0 && pharmacyLng != 0.0) {
                mapUri = Uri.parse("google.navigation:q=" + pharmacyLat + "," + pharmacyLng);
            } else {
                String encodedAddress = URLEncoder.encode(fullAddressForMaps, StandardCharsets.UTF_8.name());
                mapUri = Uri.parse("geo:0,0?q=" + encodedAddress);
            }

            Intent mapIntent = new Intent(Intent.ACTION_VIEW, mapUri);
            mapIntent.setPackage("com.google.android.apps.maps");

            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                String query = (pharmacyLat != 0.0 && pharmacyLng != 0.0)
                        ? pharmacyLat + "," + pharmacyLng
                        : URLEncoder.encode(fullAddressForMaps, StandardCharsets.UTF_8.name());
                Intent browserMapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + query));
                startActivity(browserMapIntent);
            }
        } catch (Exception e) {
            Log.e("ActivityPharmacyProfile", "Map Error: " + e.getMessage());
            UiMessage.show(this, "Could not launch Google Maps");
        }
    }

    private void loadPharmacyDetails() {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) return;

            try {
                // DML COMMAND 2: SELECT (Retrieves specific pharmacy record details from dbo.Pharmacies)
                String query = "SELECT PharmacyName, City, Barangay, District, Email, Contact, Status, Latitude, " +
                        "Longitude, OperatingHours, Bio FROM dbo.Pharmacies WHERE PharmacyID = ?";
                PreparedStatement stmt = con.prepareStatement(query);
                stmt.setInt(1, pharmacyId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String name = rs.getString("PharmacyName");
                    String city = rs.getString("City");
                    String barangay = rs.getString("Barangay");
                    String district = rs.getString("District");
                    String email = rs.getString("Email");
                    String phone = rs.getString("Contact");
                    String status = rs.getString("Status");
                    double lat = rs.getDouble("Latitude");
                    double lng = rs.getDouble("Longitude");
                    String hours = rs.getString("OperatingHours");
                    String bio = rs.getString("Bio");

                    if (city == null) city = "";
                    if (barangay == null) barangay = "";
                    if (district == null) district = "";
                    String finalPhone = (phone != null) ? phone : "";

                    final String finalCity = city;
                    final String finalBarangay = barangay;
                    final String finalDistrict = district;

                    mainHandler.post(() -> {
                        contactPhone = finalPhone;
                        pharmacyLat = lat;
                        pharmacyLng = lng;

                        tvPharmacyNameTitle.setText(name != null ? name : "Pharmacy");

                        String addressStr = "Barangay " + finalBarangay + ", " + finalCity;
                        if (!finalDistrict.isEmpty()) addressStr += " (" + finalDistrict + ")";
                        tvPharmacyAddress.setText(addressStr);

                        fullAddressForMaps = name + " Pharmacy, Barangay " + finalBarangay + ", " + finalCity + ", Philippines";

                        tvContactInfo.setText("Phone: " + (contactPhone.isEmpty() ? "N/A" : contactPhone));
                        tvEmailInfo.setText("Email: " + (email != null ? email : "N/A"));
                        tvOperatingHours.setText("Operating Hours: " + (hours != null && !hours.isEmpty() ? hours : "8:00 AM - 9:00 PM"));
                        tvStatusInfo.setText("Status: " + (status != null ? status.toUpperCase() : "VERIFIED"));

                        if (tvPharmacyVerifiedBadge != null) {
                            PharmacyReplyAdapter.styleVerificationBadge(tvPharmacyVerifiedBadge, "approved".equalsIgnoreCase(status), "Pending Verification");
                        }
                    });
                }

                con.close();
            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
                Log.e("ActivityPharmacyProfile", "Error loading pharmacy: " + e.getMessage());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
