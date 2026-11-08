package com.example.pharmafind;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FragmentCustomerTabAbout extends Fragment {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1003;

    private conSQL sqlHelper;
    private UserSessionManager sessionManager;
    private FusedLocationProviderClient fusedLocationClient;

    private TextView tvCityVal, tvBarangayVal, tvDistrictVal, tvUsernameVal, tvCustomerGpsVal;
    private TextView dialogTvPinCoords;
    private EditText dialogEtLat, dialogEtLng;

    private ActivityResultLauncher<Intent> mapPickerLauncher;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sqlHelper = new conSQL();
        sessionManager = new UserSessionManager(requireContext());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        mapPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    double lat = result.getData().getDoubleExtra("selected_lat", 0.0);
                    double lng = result.getData().getDoubleExtra("selected_lng", 0.0);
                    if (lat != 0.0 && lng != 0.0) {
                        if (dialogEtLat != null) dialogEtLat.setText(String.format(Locale.US, "%.6f", lat));
                        if (dialogEtLng != null) dialogEtLng.setText(String.format(Locale.US, "%.6f", lng));
                        if (dialogTvPinCoords != null) dialogTvPinCoords.setText("Current Pin Set: " + String.format(Locale.US, "%.5f, %.5f", lat, lng));
                        UiMessage.show(dialogTvPinCoords, "Location pin selected from Map!");
                    }
                }
            }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_customer_tab_about, container, false);

        tvCityVal = view.findViewById(R.id.tvCityVal);
        tvBarangayVal = view.findViewById(R.id.tvBarangayVal);
        tvDistrictVal = view.findViewById(R.id.tvDistrictVal);
        tvUsernameVal = view.findViewById(R.id.tvUsernameVal);
        tvCustomerGpsVal = view.findViewById(R.id.tvCustomerGpsVal);

        MaterialButton btnEdit = view.findViewById(R.id.btnEditCustomerProfile);
        if (btnEdit != null) {
            btnEdit.setOnClickListener(v -> showEditProfileDialog());
        }

        ensureCustomerColumnsExist();
        loadCustomerDetails();

        return view;
    }

    private void ensureCustomerColumnsExist() {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) return;

            try {
                Statement stmt = con.createStatement();
                String query = "IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Customers' AND COLUMN_NAME = 'Latitude') " +
                        "BEGIN " +
                        "ALTER TABLE dbo.Customers ADD Latitude FLOAT NULL, Longitude FLOAT NULL " +
                        "END";
                stmt.executeUpdate(query);
                con.close();
            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
                Log.e("CustomerTabAbout", "Error altering Customers table: " + e.getMessage());
            }
        });
    }

    private void loadCustomerDetails() {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) return;

            try {
                String query = "SELECT City, Barangay, District, Username, Latitude, Longitude FROM dbo.Customers WHERE CustomerID = ?";
                PreparedStatement stmt = con.prepareStatement(query);
                stmt.setInt(1, sessionManager.getUserId());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String city = rs.getString("City");
                    String barangay = rs.getString("Barangay");
                    String district = rs.getString("District");
                    String username = rs.getString("Username");
                    double lat = rs.getDouble("Latitude");
                    double lng = rs.getDouble("Longitude");

                    mainHandler.post(() -> {
                        sessionManager.setLocationCoordinates(lat, lng);
                        if (tvCityVal != null) tvCityVal.setText("City: " + (city != null ? city : sessionManager.getCity()));
                        if (tvBarangayVal != null) tvBarangayVal.setText("Barangay: " + (barangay != null ? barangay : sessionManager.getBarangay()));
                        if (tvDistrictVal != null) tvDistrictVal.setText("District / Area: " + (district != null ? district : sessionManager.getDistrict()));
                        if (tvUsernameVal != null) tvUsernameVal.setText("Username: " + (username != null ? username : sessionManager.getUsername()));
                        if (tvCustomerGpsVal != null) {
                            if (lat != 0.0 && lng != 0.0) {
                                tvCustomerGpsVal.setText("GPS Location Pin: Set (" + String.format(Locale.US, "%.5f", lat) + ", " + String.format(Locale.US, "%.5f", lng) + ")");
                            } else {
                                tvCustomerGpsVal.setText("GPS Location Pin: Not Set");
                            }
                        }
                    });
                }

                con.close();
            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
                Log.e("CustomerTabAbout", "Error loading details: " + e.getMessage());
            }
        });
    }

    private String selectedAvatarId = "avatar_1";

    private void showEditProfileDialog() {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_customer_profile, null);
        dialog.setContentView(dialogView);

        ImageButton btnClose = dialogView.findViewById(R.id.btnCloseEditCustomerProfile);
        EditText etCity = dialogView.findViewById(R.id.etCityCustomerEdit);
        EditText etBarangay = dialogView.findViewById(R.id.etBarangayCustomerEdit);
        EditText etDistrict = dialogView.findViewById(R.id.etDistrictCustomerEdit);
        dialogTvPinCoords = dialogView.findViewById(R.id.tvCustomerPinCoordinates);
        dialogEtLat = dialogView.findViewById(R.id.etLatCustomerEdit);
        dialogEtLng = dialogView.findViewById(R.id.etLngCustomerEdit);

        View imgAvatar1 = dialogView.findViewById(R.id.imgAvatar1);
        View imgAvatar2 = dialogView.findViewById(R.id.imgAvatar2);
        View imgAvatar3 = dialogView.findViewById(R.id.imgAvatar3);
        View imgAvatar4 = dialogView.findViewById(R.id.imgAvatar4);

        selectedAvatarId = sessionManager.getAvatar();

        View.OnClickListener avatarClickListener = v -> {
            int id = v.getId();
            if (id == R.id.imgAvatar1) selectedAvatarId = "avatar_1";
            else if (id == R.id.imgAvatar2) selectedAvatarId = "avatar_2";
            else if (id == R.id.imgAvatar3) selectedAvatarId = "avatar_3";
            else if (id == R.id.imgAvatar4) selectedAvatarId = "avatar_4";
            UiMessage.show(dialogView, "Selected Avatar Preset: " + selectedAvatarId);
        };

        if (imgAvatar1 != null) imgAvatar1.setOnClickListener(avatarClickListener);
        if (imgAvatar2 != null) imgAvatar2.setOnClickListener(avatarClickListener);
        if (imgAvatar3 != null) imgAvatar3.setOnClickListener(avatarClickListener);
        if (imgAvatar4 != null) imgAvatar4.setOnClickListener(avatarClickListener);

        MaterialButton btnUseGps = dialogView.findViewById(R.id.btnUseCustomerGPSPin);
        MaterialButton btnPickMap = dialogView.findViewById(R.id.btnPickCustomerMap);
        MaterialButton btnSave = dialogView.findViewById(R.id.btnSaveCustomerProfile);

        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        // Pre-fill dialog with current session / database values
        if (etCity != null) etCity.setText(sessionManager.getCity());
        if (etBarangay != null) etBarangay.setText(sessionManager.getBarangay());
        if (etDistrict != null) etDistrict.setText(sessionManager.getDistrict());

        double curLat = sessionManager.getLatitude();
        double curLng = sessionManager.getLongitude();
        if (curLat != 0.0 && curLng != 0.0) {
            if (dialogEtLat != null) dialogEtLat.setText(String.format(Locale.US, "%.6f", curLat));
            if (dialogEtLng != null) dialogEtLng.setText(String.format(Locale.US, "%.6f", curLng));
            if (dialogTvPinCoords != null) dialogTvPinCoords.setText("Current Pin Set: " + String.format(Locale.US, "%.5f, %.5f", curLat, curLng));
        }

        if (btnUseGps != null) {
            btnUseGps.setOnClickListener(v -> fetchCurrentLocationPin());
        }

        if (btnPickMap != null) {
            btnPickMap.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), ActivityMapPicker.class);
                String curLatStr = dialogEtLat != null ? dialogEtLat.getText().toString().trim() : "";
                String curLngStr = dialogEtLng != null ? dialogEtLng.getText().toString().trim() : "";
                if (!curLatStr.isEmpty() && !curLngStr.isEmpty()) {
                    try {
                        intent.putExtra("initial_lat", Double.parseDouble(curLatStr));
                        intent.putExtra("initial_lng", Double.parseDouble(curLngStr));
                    } catch (NumberFormatException ignored) {}
                }
                mapPickerLauncher.launch(intent);
            });
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                String city = etCity != null ? etCity.getText().toString().trim() : "";
                String barangay = etBarangay != null ? etBarangay.getText().toString().trim() : "";
                String district = etDistrict != null ? etDistrict.getText().toString().trim() : "";
                String latStr = dialogEtLat != null ? dialogEtLat.getText().toString().trim() : "";
                String lngStr = dialogEtLng != null ? dialogEtLng.getText().toString().trim() : "";

                double lat;
                double lng;
                try {
                    lat = latStr.isEmpty() ? 0.0 : Double.parseDouble(latStr);
                    lng = lngStr.isEmpty() ? 0.0 : Double.parseDouble(lngStr);
                } catch (NumberFormatException e) {
                    UiMessage.show(dialog, "Please enter a valid latitude and longitude, or leave both blank");
                    return;
                }

                btnSave.setEnabled(false);
                saveCustomerProfileToDB(city, barangay, district, lat, lng, selectedAvatarId, dialog, btnSave);
            });
        }

        View parent = (View) dialogView.getParent();
        if (parent != null) {
            parent.setBackgroundResource(android.R.color.transparent);
        }

        dialog.show();
    }

    private void fetchCurrentLocationPin() {
        if (getContext() == null) return;
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
            if (location != null) {
                double lat = location.getLatitude();
                double lng = location.getLongitude();
                if (dialogEtLat != null) dialogEtLat.setText(String.format(Locale.US, "%.6f", lat));
                if (dialogEtLng != null) dialogEtLng.setText(String.format(Locale.US, "%.6f", lng));
                UiMessage.show(dialogEtLat, "Current GPS Pin Detected!");
            } else {
                UiMessage.show(dialogEtLat, "Unable to detect GPS. Make sure location is turned on.");
            }
        });
    }

    private void saveCustomerProfileToDB(String city, String barangay, String district, double lat, double lng, String avatarId, BottomSheetDialog dialog, MaterialButton btnSave) {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) {
                mainHandler.post(() -> {
                    if (btnSave != null) btnSave.setEnabled(true);
                    UiMessage.show(dialog, "Database connection error");
                });
                return;
            }

            try {
                String query = "UPDATE dbo.Customers SET City = ?, Barangay = ?, " +
                        "District = ?, Latitude = ?, Longitude = ?, ProfilePicture = ? WHERE CustomerID = ?";
                PreparedStatement stmt = con.prepareStatement(query);
                stmt.setString(1, city);
                stmt.setString(2, barangay);
                stmt.setString(3, district);
                stmt.setDouble(4, lat);
                stmt.setDouble(5, lng);
                stmt.setString(6, avatarId);
                stmt.setInt(7, sessionManager.getUserId());

                int rows = stmt.executeUpdate();
                con.close();

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    if (btnSave != null) btnSave.setEnabled(true);
                    if (rows > 0) {
                        sessionManager.createCustomerSession(sessionManager.getUserId(), sessionManager.getUsername(), sessionManager.getFullName(), city, barangay, district);
                        sessionManager.setLocationCoordinates(lat, lng);
                        sessionManager.setAvatar(avatarId);

                        UiMessage.show(this, "Profile & Avatar Preset Updated!");
                        dialog.dismiss();
                        loadCustomerDetails();
                    } else {
                        UiMessage.show(dialog, "Update failed");
                    }
                });

            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
                Log.e("CustomerTabAbout", "Error saving profile: " + e.getMessage());
                mainHandler.post(() -> {
                    if (btnSave != null) btnSave.setEnabled(true);
                    UiMessage.show(dialog, "Error: " + e.getMessage());
                });
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    // After the user taps Allow on the system prompt, carry on with what they asked for instead of
    // making them tap the button a second time.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) return;
        boolean granted = false;
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_GRANTED) granted = true;
        }
        if (granted) {
            fetchCurrentLocationPin();
        } else if (getContext() != null) {
            UiMessage.show(dialogEtLat, "Location permission is needed to detect your GPS pin.");
        }
    }
}
