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
import android.widget.Toast;
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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FragmentPharmacyTabAbout extends Fragment {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1002;

    private conSQL sqlHelper;
    private UserSessionManager sessionManager;
    private FusedLocationProviderClient fusedLocationClient;

    private TextView tvPharmacyNameVal, tvCityVal, tvBarangayVal, tvDistrictVal, tvContactVal, tvHoursVal, tvGpsVal, tvBioVal;
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
                        Toast.makeText(getContext(), "Store location pin selected from Map!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_pharmacy_tab_about, container, false);

        tvPharmacyNameVal = view.findViewById(R.id.tvPharmacyNameVal);
        tvCityVal = view.findViewById(R.id.tvCityVal);
        tvBarangayVal = view.findViewById(R.id.tvBarangayVal);
        tvDistrictVal = view.findViewById(R.id.tvDistrictVal);
        tvContactVal = view.findViewById(R.id.tvContactVal);
        tvHoursVal = view.findViewById(R.id.tvHoursVal);
        tvGpsVal = view.findViewById(R.id.tvGpsVal);
        tvBioVal = view.findViewById(R.id.tvBioVal);

        // Pre-populate immediately from session so registered details show up instantly
        if (tvPharmacyNameVal != null) tvPharmacyNameVal.setText("Pharmacy Name: " + sessionManager.getFullName());
        if (tvCityVal != null) tvCityVal.setText("City: " + sessionManager.getCity());
        if (tvBarangayVal != null) tvBarangayVal.setText("Barangay: " + sessionManager.getBarangay());
        if (tvDistrictVal != null) tvDistrictVal.setText("District / Area: " + sessionManager.getDistrict());

        MaterialButton btnEdit = view.findViewById(R.id.btnEditPharmacyProfileTab);
        if (btnEdit != null) {
            btnEdit.setOnClickListener(v -> showEditProfileDialog());
        }

        loadPharmacyDetails();

        return view;
    }

    private void loadPharmacyDetails() {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) return;

            try {
                String query = "SELECT PharmacyName, City, Barangay, District, Contact, Bio, OperatingHours, Latitude, Longitude FROM dbo.Pharmacies WHERE PharmacyID = ?";
                PreparedStatement stmt = con.prepareStatement(query);
                stmt.setInt(1, sessionManager.getUserId());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String name = rs.getString("PharmacyName");
                    String city = rs.getString("City");
                    String barangay = rs.getString("Barangay");
                    String district = rs.getString("District");
                    String contact = rs.getString("Contact");
                    String bio = rs.getString("Bio");
                    String hours = rs.getString("OperatingHours");
                    double lat = rs.getDouble("Latitude");
                    double lng = rs.getDouble("Longitude");

                    mainHandler.post(() -> {
                        String finalName = (name != null && !name.trim().isEmpty()) ? name : sessionManager.getFullName();
                        String finalCity = (city != null && !city.trim().isEmpty()) ? city : sessionManager.getCity();
                        String finalBarangay = (barangay != null && !barangay.trim().isEmpty()) ? barangay : sessionManager.getBarangay();
                        String finalDistrict = (district != null && !district.trim().isEmpty()) ? district : sessionManager.getDistrict();

                        if (tvPharmacyNameVal != null) tvPharmacyNameVal.setText("Pharmacy Name: " + (!finalName.isEmpty() ? finalName : "Not set"));
                        if (tvCityVal != null) tvCityVal.setText("City: " + (!finalCity.isEmpty() ? finalCity : "Not set"));
                        if (tvBarangayVal != null) tvBarangayVal.setText("Barangay: " + (!finalBarangay.isEmpty() ? finalBarangay : "Not set"));
                        if (tvDistrictVal != null) tvDistrictVal.setText("District / Area: " + (!finalDistrict.isEmpty() ? finalDistrict : "Not set"));
                        if (tvContactVal != null) tvContactVal.setText("Contact: " + (contact != null && !contact.trim().isEmpty() ? contact : "Not set"));
                        if (tvHoursVal != null) tvHoursVal.setText("Operating Hours: " + (hours != null && !hours.trim().isEmpty() ? hours : "8:00 AM - 9:00 PM"));
                        if (tvGpsVal != null) {
                            if (lat != 0.0 && lng != 0.0) {
                                tvGpsVal.setText("GPS Location Pin: Set (" + lat + ", " + lng + ")");
                            } else {
                                tvGpsVal.setText("GPS Location Pin: Not Set");
                            }
                        }
                        if (tvBioVal != null) tvBioVal.setText(bio != null && !bio.isEmpty() ? bio : "No bio set.");
                    });
                }

                con.close();
            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
                Log.e("PharmacyTabAbout", "Error loading details: " + e.getMessage());
            }
        });
    }

    private void showEditProfileDialog() {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_pharmacy_profile, null);
        dialog.setContentView(dialogView);

        ImageButton btnClose = dialogView.findViewById(R.id.btnCloseEditProfile);
        EditText etName = dialogView.findViewById(R.id.etPharmacyNameEdit);
        EditText etBio = dialogView.findViewById(R.id.etBioEdit);
        EditText etHours = dialogView.findViewById(R.id.etHoursEdit);
        EditText etContact = dialogView.findViewById(R.id.etContactEdit);
        dialogTvPinCoords = dialogView.findViewById(R.id.tvPinCoordinates);
        dialogEtLat = dialogView.findViewById(R.id.etLatEdit);
        dialogEtLng = dialogView.findViewById(R.id.etLngEdit);

        MaterialButton btnUseGps = dialogView.findViewById(R.id.btnUseCurrentGPSPin);
        MaterialButton btnPickMap = dialogView.findViewById(R.id.btnPickPharmacyMap);
        MaterialButton btnSave = dialogView.findViewById(R.id.btnSavePharmacyProfile);

        MaterialButton btnOpeningTime = dialogView.findViewById(R.id.btnOpeningTime);
        MaterialButton btnClosingTime = dialogView.findViewById(R.id.btnClosingTime);
        com.google.android.material.chip.ChipGroup chipGroupDays = dialogView.findViewById(R.id.chipGroupDays);

        final String[] openTimeStr = {"8:00 AM"};
        final String[] closeTimeStr = {"9:00 PM"};

        Runnable updateHoursSummary = () -> {
            if (etHours == null) return;
            String daysLabel = "Mon - Sun";
            if (chipGroupDays != null) {
                int checked = chipGroupDays.getCheckedChipId();
                if (checked == R.id.chipMonSat) daysLabel = "Mon - Sat";
                else if (checked == R.id.chipMonFri) daysLabel = "Mon - Fri";
                else if (checked == R.id.chip247) {
                    etHours.setText("24/7 Open Daily");
                    return;
                }
            }
            etHours.setText(openTimeStr[0] + " - " + closeTimeStr[0] + " (" + daysLabel + ")");
        };

        if (btnOpeningTime != null) {
            btnOpeningTime.setOnClickListener(v -> {
                android.app.TimePickerDialog tpd = new android.app.TimePickerDialog(requireContext(), (view, hourOfDay, minute) -> {
                    String amPm = hourOfDay >= 12 ? "PM" : "AM";
                    int hour12 = hourOfDay % 12;
                    if (hour12 == 0) hour12 = 12;
                    openTimeStr[0] = String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm);
                    btnOpeningTime.setText("Opening: " + openTimeStr[0]);
                    updateHoursSummary.run();
                }, 8, 0, false);
                tpd.show();
            });
        }

        if (btnClosingTime != null) {
            btnClosingTime.setOnClickListener(v -> {
                android.app.TimePickerDialog tpd = new android.app.TimePickerDialog(requireContext(), (view, hourOfDay, minute) -> {
                    String amPm = hourOfDay >= 12 ? "PM" : "AM";
                    int hour12 = hourOfDay % 12;
                    if (hour12 == 0) hour12 = 12;
                    closeTimeStr[0] = String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm);
                    btnClosingTime.setText("Closing: " + closeTimeStr[0]);
                    updateHoursSummary.run();
                }, 21, 0, false);
                tpd.show();
            });
        }

        if (chipGroupDays != null) {
            chipGroupDays.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.contains(R.id.chipCustomDates)) {
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    new android.app.DatePickerDialog(requireContext(), (view1, year1, month1, day1) -> {
                        String startDate = String.format(Locale.US, "%04d-%02d-%02d", year1, month1 + 1, day1);
                        new android.app.DatePickerDialog(requireContext(), (view2, year2, month2, day2) -> {
                            String endDate = String.format(Locale.US, "%04d-%02d-%02d", year2, month2 + 1, day2);
                            if (etHours != null) {
                                etHours.setText(openTimeStr[0] + " - " + closeTimeStr[0] + " (" + startDate + " to " + endDate + ")");
                            }
                        }, year1, month1, day1).show();
                    }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
                } else {
                    updateHoursSummary.run();
                }
            });
        }

        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        loadCurrentProfileDetailsIntoDialog(etName, etBio, etHours, etContact, dialogEtLat, dialogEtLng, dialogTvPinCoords);

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
                String name = etName.getText().toString().trim();
                String bio = etBio.getText().toString().trim();
                String hours = etHours.getText().toString().trim();
                String contact = etContact.getText().toString().trim();
                String latStr = dialogEtLat.getText().toString().trim();
                String lngStr = dialogEtLng.getText().toString().trim();

                double lat;
                double lng;
                try {
                    lat = latStr.isEmpty() ? 0.0 : Double.parseDouble(latStr);
                    lng = lngStr.isEmpty() ? 0.0 : Double.parseDouble(lngStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(getContext(), "Please enter a valid latitude and longitude, or leave both blank", Toast.LENGTH_SHORT).show();
                    return;
                }

                btnSave.setEnabled(false);
                savePharmacyProfileToDB(name, bio, hours, contact, lat, lng, dialog, btnSave);
            });
        }

        View parent = (View) dialogView.getParent();
        if (parent != null) {
            parent.setBackgroundResource(android.R.color.transparent);
        }

        dialog.show();
    }

    private void loadCurrentProfileDetailsIntoDialog(EditText etName, EditText etBio, EditText etHours, EditText etContact, EditText etLat, EditText etLng, TextView tvPinCoords) {
        executor.execute(() -> {
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

                    mainHandler.post(() -> {
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
                    });
                }

                con.close();
            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
                Log.e("PharmacyTabAbout", "Error loading profile details: " + e.getMessage());
            }
        });
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
                Toast.makeText(getContext(), "Current GPS Pin Detected!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Unable to detect GPS. Make sure location is turned on.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void savePharmacyProfileToDB(String name, String bio, String hours, String contact, double lat, double lng, BottomSheetDialog dialog, MaterialButton btnSave) {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) {
                mainHandler.post(() -> {
                    if (btnSave != null) btnSave.setEnabled(true);
                    Toast.makeText(getContext(), "Database connection error", Toast.LENGTH_SHORT).show();
                });
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
                con.close();

                mainHandler.post(() -> {
                    if (btnSave != null) btnSave.setEnabled(true);
                    if (rows > 0) {
                        sessionManager.createPharmacySession(sessionManager.getUserId(), sessionManager.getUsername(), name, sessionManager.getCity(), sessionManager.getBarangay(), sessionManager.getDistrict(), sessionManager.getStatus());
                        Toast.makeText(getContext(), "Profile & Location Pin Updated!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        loadPharmacyDetails();
                    } else {
                        Toast.makeText(getContext(), "Update failed", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
                Log.e("PharmacyTabAbout", "Error saving profile: " + e.getMessage());
                mainHandler.post(() -> {
                    if (btnSave != null) btnSave.setEnabled(true);
                    Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(getContext(), "Location permission is needed to detect your GPS pin.", Toast.LENGTH_SHORT).show();
        }
    }
}
