package com.example.pharmafind;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ActivityPharmacyProfile extends AppCompatActivity {

    private conSQL sqlHelper;
    private int pharmacyId = -1;

    private TextView tvPharmacyNameTitle, tvPharmacyAddress, tvContactInfo, tvEmailInfo, tvOperatingHours, tvStatusInfo;

    private String fullAddressForMaps = "";
    private String contactPhone = "";
    private double pharmacyLat = 0.0;
    private double pharmacyLng = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pharmacy_profile);

        sqlHelper = new conSQL();
        pharmacyId = getIntent().getIntExtra("pharmacy_id", -1);

        if (pharmacyId == -1) {
            Toast.makeText(this, "Pharmacy Not Found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ImageButton btnBack = findViewById(R.id.btnBackPharmacyProfile);
        btnBack.setOnClickListener(v -> finish());

        tvPharmacyNameTitle = findViewById(R.id.tvPharmacyNameTitle);
        tvPharmacyAddress = findViewById(R.id.tvPharmacyAddress);
        tvContactInfo = findViewById(R.id.tvContactInfo);
        tvEmailInfo = findViewById(R.id.tvEmailInfo);
        tvOperatingHours = findViewById(R.id.tvOperatingHours);
        tvStatusInfo = findViewById(R.id.tvStatusInfo);

        MaterialButton btnOpenMaps = findViewById(R.id.btnOpenMaps);
        MaterialButton btnCallPharmacy = findViewById(R.id.btnCallPharmacy);

        btnOpenMaps.setOnClickListener(v -> openPharmacyOnGoogleMaps());

        btnCallPharmacy.setOnClickListener(v -> {
            if (!contactPhone.isEmpty()) {
                Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + contactPhone));
                startActivity(callIntent);
            } else {
                Toast.makeText(this, "No phone number available", Toast.LENGTH_SHORT).show();
            }
        });

        loadPharmacyDetails();
    }

    private void openPharmacyOnGoogleMaps() {
        try {
            Uri mapUri;
            if (pharmacyLat != 0.0 && pharmacyLng != 0.0) {
                // If coordinates exist, launch direct navigation / single marker mode
                mapUri = Uri.parse("google.navigation:q=" + pharmacyLat + "," + pharmacyLng);
            } else {
                // Fallback to address query
                String encodedAddress = URLEncoder.encode(fullAddressForMaps, StandardCharsets.UTF_8.name());
                mapUri = Uri.parse("geo:0,0?q=" + encodedAddress);
            }

            Intent mapIntent = new Intent(Intent.ACTION_VIEW, mapUri);
            mapIntent.setPackage("com.google.android.apps.maps");

            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                // Fallback web browser intent
                String query = (pharmacyLat != 0.0 && pharmacyLng != 0.0)
                        ? pharmacyLat + "," + pharmacyLng
                        : URLEncoder.encode(fullAddressForMaps, StandardCharsets.UTF_8.name());
                Intent browserMapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + query));
                startActivity(browserMapIntent);
            }
        } catch (Exception e) {
            Log.e("ActivityPharmacyProfile", "Map Error: " + e.getMessage());
            Toast.makeText(this, "Could not launch Google Maps", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadPharmacyDetails() {
        Connection con = sqlHelper.conclass();
        if (con == null) return;

        try {
            String query = "SELECT PharmacyName, City, Barangay, District, Email, Contact, Status, Latitude, Longitude, OperatingHours, Bio FROM dbo.Pharmacies WHERE PharmacyID = ?";
            PreparedStatement stmt = con.prepareStatement(query);
            stmt.setInt(1, pharmacyId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String name = rs.getString("PharmacyName");
                String city = rs.getString("City");
                String barangay = rs.getString("Barangay");
                String district = rs.getString("District");
                String email = rs.getString("Email");
                contactPhone = rs.getString("Contact");
                String status = rs.getString("Status");
                pharmacyLat = rs.getDouble("Latitude");
                pharmacyLng = rs.getDouble("Longitude");
                String hours = rs.getString("OperatingHours");
                String bio = rs.getString("Bio");

                if (city == null) city = "";
                if (barangay == null) barangay = "";
                if (district == null) district = "";
                if (contactPhone == null) contactPhone = "";

                tvPharmacyNameTitle.setText(name != null ? name : "Pharmacy");

                String addressStr = "Barangay " + barangay + ", " + city;
                if (!district.isEmpty()) addressStr += " (" + district + ")";
                tvPharmacyAddress.setText("📍 " + addressStr);

                fullAddressForMaps = name + " Pharmacy, Barangay " + barangay + ", " + city + ", Philippines";

                tvContactInfo.setText("Phone: " + (contactPhone.isEmpty() ? "N/A" : contactPhone));
                tvEmailInfo.setText("Email: " + (email != null ? email : "N/A"));
                tvOperatingHours.setText("Operating Hours: " + (hours != null && !hours.isEmpty() ? hours : "8:00 AM - 9:00 PM"));
                tvStatusInfo.setText("Status: " + (status != null ? status.toUpperCase() : "VERIFIED"));
            }

            con.close();
        } catch (SQLException e) {
            Log.e("ActivityPharmacyProfile", "Error loading pharmacy: " + e.getMessage());
        }
    }
}
