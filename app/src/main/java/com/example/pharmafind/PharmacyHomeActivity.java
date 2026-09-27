package com.example.pharmafind;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PharmacyHomeActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private conSQL sqlHelper;
    private UserSessionManager sessionManager;
    private InquiryAdapter inquiryAdapter;
    private List<InquiryModel> localInquiryList;
    private FusedLocationProviderClient fusedLocationClient;

    private EditText dialogEtLat, dialogEtLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pharmacy_home);

        sqlHelper = new conSQL();
        sessionManager = new UserSessionManager(this);
        localInquiryList = new ArrayList<>();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        TextView tvPharmacyWelcome = findViewById(R.id.tvPharmacyWelcome);
        TextView tvPharmacyLocation = findViewById(R.id.tvPharmacyLocation);
        MaterialButton btnEditProfile = findViewById(R.id.btnEditPharmacyProfile);
        MaterialButton btnLogout = findViewById(R.id.btnPharmacyLogout);

        tvPharmacyWelcome.setText("Welcome, " + sessionManager.getFullName());
        tvPharmacyLocation.setText("📍 Barangay " + sessionManager.getBarangay() + ", " + sessionManager.getCity());

        btnEditProfile.setOnClickListener(v -> showEditProfileDialog());

        btnLogout.setOnClickListener(v -> {
            sessionManager.logoutUser();
            startActivity(new Intent(PharmacyHomeActivity.this, MainActivity.class));
            finish();
        });

        RecyclerView rvPharmacyInquiries = findViewById(R.id.rvPharmacyInquiries);
        rvPharmacyInquiries.setLayoutManager(new LinearLayoutManager(this));
        inquiryAdapter = new InquiryAdapter(localInquiryList, item -> {
            Intent intent = new Intent(PharmacyHomeActivity.this, ActivityInquiryThread.class);
            intent.putExtra("inquiry_id", item.getInquiryId());
            startActivity(intent);
        });
        rvPharmacyInquiries.setAdapter(inquiryAdapter);

        ensurePharmacyColumnsExist();
        loadLocalInquiries();
    }

    private void ensurePharmacyColumnsExist() {
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
            Log.e("PharmacyHomeActivity", "Error adding columns: " + e.getMessage());
        }
    }

    private void showEditProfileDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_pharmacy_profile, null);
        dialog.setContentView(dialogView);

        ImageButton btnClose = dialogView.findViewById(R.id.btnCloseEditProfile);
        EditText etName = dialogView.findViewById(R.id.etPharmacyNameEdit);
        EditText etBio = dialogView.findViewById(R.id.etBioEdit);
        EditText etHours = dialogView.findViewById(R.id.etHoursEdit);
        EditText etContact = dialogView.findViewById(R.id.etContactEdit);
        TextView tvPinCoordinates = dialogView.findViewById(R.id.tvPinCoordinates);
        dialogEtLat = dialogView.findViewById(R.id.etLatEdit);
        dialogEtLng = dialogView.findViewById(R.id.etLngEdit);

        MaterialButton btnUseGps = dialogView.findViewById(R.id.btnUseCurrentGPSPin);
        MaterialButton btnSave = dialogView.findViewById(R.id.btnSavePharmacyProfile);

        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        // Load existing profile details from DB
        loadCurrentProfileDetails(etName, etBio, etHours, etContact, dialogEtLat, dialogEtLng, tvPinCoordinates);

        if (btnUseGps != null) {
            btnUseGps.setOnClickListener(v -> fetchCurrentLocationPin());
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                String name = etName.getText().toString().trim();
                String bio = etBio.getText().toString().trim();
                String hours = etHours.getText().toString().trim();
                String contact = etContact.getText().toString().trim();
                String latStr = dialogEtLat.getText().toString().trim();
                String lngStr = dialogEtLng.getText().toString().trim();

                double lat = latStr.isEmpty() ? 0.0 : Double.parseDouble(latStr);
                double lng = lngStr.isEmpty() ? 0.0 : Double.parseDouble(lngStr);

                savePharmacyProfileToDB(name, bio, hours, contact, lat, lng, dialog);
            });
        }

        View parent = (View) dialogView.getParent();
        if (parent != null) {
            parent.setBackgroundResource(android.R.color.transparent);
        }

        dialog.show();
    }

    private void loadCurrentProfileDetails(EditText etName, EditText etBio, EditText etHours, EditText etContact, EditText etLat, EditText etLng, TextView tvPinCoords) {
        Connection con = sqlHelper.conclass();
        if (con == null) return;

        try {
            String query = "SELECT PharmacyName, Contact, Bio, OperatingHours, Latitude, Longitude FROM dbo.Pharmacies WHERE PharmacyID = ?";
            PreparedStatement stmt = con.prepareStatement(query);
            stmt.setInt(1, sessionManager.getUserId());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String name = rs.getString("PharmacyName");
                String contact = rs.getString("Contact");
                String bio = rs.getString("Bio");
                String hours = rs.getString("OperatingHours");
                double lat = rs.getDouble("Latitude");
                double lng = rs.getDouble("Longitude");

                if (etName != null) etName.setText(name != null ? name : "");
                if (etContact != null) etContact.setText(contact != null ? contact : "");
                if (etBio != null) etBio.setText(bio != null ? bio : "");
                if (etHours != null) etHours.setText(hours != null ? hours : "");

                if (lat != 0.0 && lng != 0.0) {
                    if (etLat != null) etLat.setText(String.valueOf(lat));
                    if (etLng != null) etLng.setText(String.valueOf(lng));
                    if (tvPinCoords != null) tvPinCoords.setText("Current Pin Set: " + lat + ", " + lng);
                } else {
                    if (tvPinCoords != null) tvPinCoords.setText("Current Pin: Not Set");
                }
            }

            con.close();
        } catch (SQLException e) {
            Log.e("PharmacyHomeActivity", "Error loading profile details: " + e.getMessage());
        }
    }

    private void fetchCurrentLocationPin() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                double lat = location.getLatitude();
                double lng = location.getLongitude();
                if (dialogEtLat != null) dialogEtLat.setText(String.format(Locale.US, "%.6f", lat));
                if (dialogEtLng != null) dialogEtLng.setText(String.format(Locale.US, "%.6f", lng));
                Toast.makeText(this, "Current GPS Pin Detected!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Unable to detect GPS. Make sure location is turned on.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchCurrentLocationPin();
        }
    }

    private void savePharmacyProfileToDB(String name, String bio, String hours, String contact, double lat, double lng, BottomSheetDialog dialog) {
        Connection con = sqlHelper.conclass();
        if (con == null) {
            Toast.makeText(this, "Database connection error", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String query = "UPDATE dbo.Pharmacies SET PharmacyName = ?, Bio = ?, OperatingHours = ?, Contact = ?, Latitude = ?, Longitude = ? WHERE PharmacyID = ?";
            PreparedStatement stmt = con.prepareStatement(query);
            stmt.setString(1, name);
            stmt.setString(2, bio);
            stmt.setString(3, hours);
            stmt.setString(4, contact);
            stmt.setDouble(5, lat);
            stmt.setDouble(6, lng);
            stmt.setInt(7, sessionManager.getUserId());

            int rows = stmt.executeUpdate();
            if (rows > 0) {
                Toast.makeText(this, "Profile & Location Pin Updated!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show();
            }

            con.close();
        } catch (SQLException e) {
            Log.e("PharmacyHomeActivity", "Error saving profile: " + e.getMessage());
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadLocalInquiries() {
        Connection con = sqlHelper.conclass();
        if (con == null) return;

        try {
            localInquiryList.clear();
            String pharmacyBarangay = sessionManager.getBarangay();
            String pharmacyCity = sessionManager.getCity();

            String query = "SELECT i.InquiryID, i.CustomerID, i.CustomerName, i.MedicineName, i.Description, i.ImageUrl, i.City, i.Barangay, i.District, i.Status, CONVERT(VARCHAR, i.CreatedAt, 120) AS CreatedAtStr, " +
                    "(SELECT COUNT(*) FROM dbo.PharmacyReplies r WHERE r.InquiryID = i.InquiryID) AS ReplyCount " +
                    "FROM dbo.Inquiries i " +
                    "WHERE LOWER(i.Barangay) = LOWER(?) OR LOWER(i.City) = LOWER(?) " +
                    "ORDER BY i.CreatedAt DESC";

            PreparedStatement stmt = con.prepareStatement(query);
            stmt.setString(1, pharmacyBarangay != null ? pharmacyBarangay : "");
            stmt.setString(2, pharmacyCity != null ? pharmacyCity : "");

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                int inquiryId = rs.getInt("InquiryID");
                int customerId = rs.getInt("CustomerID");
                String customerName = rs.getString("CustomerName");
                String medicineName = rs.getString("MedicineName");
                String description = rs.getString("Description");
                String imageUrl = rs.getString("ImageUrl");
                String city = rs.getString("City");
                String barangay = rs.getString("Barangay");
                String district = rs.getString("District");
                String status = rs.getString("Status");
                String createdAt = rs.getString("CreatedAtStr");
                int replyCount = rs.getInt("ReplyCount");

                localInquiryList.add(new InquiryModel(
                        inquiryId, customerId, customerName, medicineName, description, imageUrl, city, barangay, district, status, createdAt, replyCount
                ));
            }

            con.close();
            inquiryAdapter.notifyDataSetChanged();

        } catch (SQLException e) {
            Log.e("PharmacyHomeActivity", "Error loading local inquiries: " + e.getMessage());
        }
    }
}
