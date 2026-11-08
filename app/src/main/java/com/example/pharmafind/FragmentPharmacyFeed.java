package com.example.pharmafind;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.ChipGroup;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FragmentPharmacyFeed extends Fragment {

    private conSQL sqlHelper;
    private UserSessionManager sessionManager;
    private InquiryAdapter inquiryAdapter;
    private List<InquiryModel> allInquiriesList;
    private List<InquiryModel> displayedInquiriesList;

    private EditText etSearchFeed;
    private ChipGroup chipGroupFilter;
    private String currentSearchQuery = "";
    private String currentStatusFilter = "all";
    private double selectedRadiusKm = 5.0;

    private ProgressBar pbPharmacyFeedLoading;
    private TextView tvPharmacyFeedEmptyState;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sqlHelper = new conSQL();
        sessionManager = new UserSessionManager(requireContext());
        allInquiriesList = new ArrayList<>();
        displayedInquiriesList = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_pharmacy_feed, container, false);

        etSearchFeed = view.findViewById(R.id.etSearchPharmacyFeed);
        chipGroupFilter = view.findViewById(R.id.chipGroupPharmacyFilter);
        pbPharmacyFeedLoading = view.findViewById(R.id.pbPharmacyFeedLoading);
        tvPharmacyFeedEmptyState = view.findViewById(R.id.tvPharmacyFeedEmptyState);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshPharmacyFeed);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeResources(R.color.primary_teal);
            swipeRefreshLayout.setOnRefreshListener(this::loadLocalInquiriesFromDB);
        }

        RecyclerView rvInquiries = view.findViewById(R.id.rvPharmacyInquiries);
        rvInquiries.setLayoutManager(new LinearLayoutManager(getContext()));
        inquiryAdapter = new InquiryAdapter(displayedInquiriesList, item -> {
            Intent intent = new Intent(getContext(), ActivityInquiryThread.class);
            intent.putExtra("inquiry_id", item.getInquiryId());
            startActivity(intent);
        });
        inquiryAdapter.setRadiusKm(sessionManager.getProximityRadiusKm());
        rvInquiries.setAdapter(inquiryAdapter);

        if (etSearchFeed != null) {
            etSearchFeed.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentSearchQuery = s.toString().trim().toLowerCase();
                    filterInquiries();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        com.google.android.material.chip.Chip chipPharmacyWithin5km = view.findViewById(R.id.chipPharmacyWithin5km);
        if (chipPharmacyWithin5km != null) {
            chipPharmacyWithin5km.setOnClickListener(v -> showRadiusSelectorDialog(chipPharmacyWithin5km));
        }

        if (chipGroupFilter != null) {
            chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.contains(R.id.chipPharmacyOpen)) {
                    currentStatusFilter = "open";
                } else if (checkedIds.contains(R.id.chipPharmacyFulfilled)) {
                    currentStatusFilter = "fulfilled";
                } else if (checkedIds.contains(R.id.chipPharmacyWithin5km)) {
                    currentStatusFilter = "within5km";
                } else {
                    currentStatusFilter = "all";
                }
                filterInquiries();
            });
        }

        return view;
    }

    private void showRadiusSelectorDialog(com.google.android.material.chip.Chip chip) {
        if (getContext() == null) return;
        String[] options = {"2 km", "3 km", "5 km", "10 km", "20 km", "50 km"};
        final double[] values = {2.0, 3.0, 5.0, 10.0, 20.0, 50.0};

        int checkedItem = 2; // default 5 km
        for (int i = 0; i < values.length; i++) {
            if (Math.abs(values[i] - selectedRadiusKm) < 0.1) {
                checkedItem = i;
                break;
            }
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Request Search Radius")
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    selectedRadiusKm = values[which];
                    if (chip != null) {
                        chip.setText(String.format(java.util.Locale.US, "Within %.0f km ⚙", selectedRadiusKm));
                    }
                    currentStatusFilter = "within5km";
                    if (chipGroupFilter != null) {
                        chipGroupFilter.check(R.id.chipPharmacyWithin5km);
                    }
                    filterInquiries();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload every time this screen becomes visible again (e.g. returning from a
        // request thread), so reply counts and statuses don't stay stale.
        loadLocalInquiriesFromDB();
    }

    private void filterInquiries() {
        displayedInquiriesList.clear();

        for (InquiryModel item : allInquiriesList) {
            boolean matchesSearch = currentSearchQuery.isEmpty() ||
                    (item.getMedicineName() != null && item.getMedicineName().toLowerCase().contains(currentSearchQuery)) ||
                    (item.getDescription() != null && item.getDescription().toLowerCase().contains(currentSearchQuery)) ||
                    (item.getBarangay() != null && item.getBarangay().toLowerCase().contains(currentSearchQuery));

            boolean matchesStatus = true;
            if ("open".equalsIgnoreCase(currentStatusFilter)) {
                matchesStatus = "open".equalsIgnoreCase(item.getStatus());
            } else if ("fulfilled".equalsIgnoreCase(currentStatusFilter)) {
                matchesStatus = "fulfilled".equalsIgnoreCase(item.getStatus());
            } else if ("within5km".equalsIgnoreCase(currentStatusFilter)) {
                if (item.getDistanceKm() >= 0.0) {
                    matchesStatus = item.getDistanceKm() <= selectedRadiusKm;
                } else {
                    String userBg = sessionManager.getBarangay();
                    matchesStatus = (userBg != null && item.getBarangay() != null && item.getBarangay().equalsIgnoreCase(userBg));
                }
            }

            if (matchesSearch && matchesStatus) {
                displayedInquiriesList.add(item);
            }
        }

        if (inquiryAdapter != null) {
            inquiryAdapter.notifyDataSetChanged();
        }

        if (tvPharmacyFeedEmptyState != null) {
            if (displayedInquiriesList.isEmpty()) {
                tvPharmacyFeedEmptyState.setText("No requests found.");
                tvPharmacyFeedEmptyState.setVisibility(View.VISIBLE);
            } else {
                tvPharmacyFeedEmptyState.setVisibility(View.GONE);
            }
        }
    }

    private void showFeedError(String message) {
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        if (pbPharmacyFeedLoading != null) pbPharmacyFeedLoading.setVisibility(View.GONE);
        if (tvPharmacyFeedEmptyState != null) {
            tvPharmacyFeedEmptyState.setText(message);
            tvPharmacyFeedEmptyState.setVisibility(View.VISIBLE);
        }
    }

    private void loadLocalInquiriesFromDB() {
        if (pbPharmacyFeedLoading != null) pbPharmacyFeedLoading.setVisibility(View.VISIBLE);
        if (tvPharmacyFeedEmptyState != null) tvPharmacyFeedEmptyState.setVisibility(View.GONE);

        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) {
                mainHandler.post(() -> showFeedError("Couldn't connect. Check your internet and try again."));
                return;
            }

            try {
                List<InquiryModel> tempInquiries = new ArrayList<>();
                String pharmacyBarangay = sessionManager.getBarangay();
                String pharmacyCity = sessionManager.getCity();

                double pharmacyLat = sessionManager.getLatitude();
                double pharmacyLng = sessionManager.getLongitude();

                if ((pharmacyLat == 0.0 || pharmacyLng == 0.0) && sessionManager.getUserId() != -1) {
                    try {
                        String pSql = "SELECT Latitude, Longitude FROM dbo.Pharmacies WHERE PharmacyID = ?";
                        PreparedStatement pStmt = con.prepareStatement(pSql);
                        pStmt.setInt(1, sessionManager.getUserId());
                        ResultSet pRs = pStmt.executeQuery();
                        if (pRs.next()) {
                            pharmacyLat = pRs.getDouble("Latitude");
                            pharmacyLng = pRs.getDouble("Longitude");
                            if (pharmacyLat != 0.0 && pharmacyLng != 0.0) {
                                sessionManager.setLocationCoordinates(pharmacyLat, pharmacyLng);
                            }
                        }
                        pRs.close();
                        pStmt.close();
                    } catch (SQLException ignored) {}
                }

                double[] pCoords = LocationUtils.getCoordinates(pharmacyCity, pharmacyBarangay, pharmacyLat, pharmacyLng);
                pharmacyLat = pCoords[0];
                pharmacyLng = pCoords[1];

                String query = "SELECT i.InquiryID, i.CustomerID, i.CustomerName, i.MedicineName, i.Description, CASE WHEN i.ImageUrl IS NOT NULL AND i.ImageUrl <> '' THEN 1 ELSE 0 END AS HasImage, i.City, i.Barangay, i.District, i.Status, CONVERT(VARCHAR, i.CreatedAt, 120) AS CreatedAtStr, " +
                        "COALESCE(i.Latitude, c.Latitude, 0.0) AS Latitude, COALESCE(i.Longitude, c.Longitude, 0.0) AS Longitude, " +
                        "(SELECT COUNT(*) FROM dbo.PharmacyReplies r WHERE r.InquiryID = i.InquiryID) AS ReplyCount " +
                        "FROM dbo.Inquiries i " +
                        "LEFT JOIN dbo.Customers c ON i.CustomerID = c.CustomerID " +
                        "WHERE LOWER(i.Barangay) LIKE '%' + LOWER(?) + '%' " +
                        "   OR LOWER(i.City) LIKE '%' + LOWER(?) + '%' " +
                        "   OR LOWER(?) LIKE '%' + LOWER(i.Barangay) + '%' " +
                        "   OR i.Barangay IS NULL OR i.Barangay = '' OR ? = '' " +
                        "   OR LOWER(?) LIKE '%' + LOWER(i.City) + '%' " +
                        "ORDER BY i.CreatedAt DESC";

                PreparedStatement stmt = con.prepareStatement(query);
                String bg = pharmacyBarangay != null ? pharmacyBarangay.trim() : "";
                String ct = pharmacyCity != null ? pharmacyCity.trim() : "";
                stmt.setString(1, bg);
                stmt.setString(2, ct);
                stmt.setString(3, bg);
                stmt.setString(4, bg);
                stmt.setString(5, ct);

                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    int inquiryId = rs.getInt("InquiryID");
                    int customerId = rs.getInt("CustomerID");
                    String customerName = rs.getString("CustomerName");
                    String medicineName = rs.getString("MedicineName");
                    String description = rs.getString("Description");
                    boolean hasImage = rs.getInt("HasImage") == 1;
                    String city = rs.getString("City");
                    String barangay = rs.getString("Barangay");
                    String district = rs.getString("District");
                    String status = rs.getString("Status");
                    String createdAt = rs.getString("CreatedAtStr");
                    int replyCount = rs.getInt("ReplyCount");
                    double lat = rs.getDouble("Latitude");
                    double lng = rs.getDouble("Longitude");

                    double[] cCoords = LocationUtils.getCoordinates(city, barangay, lat, lng);
                    double cLat = cCoords[0];
                    double cLng = cCoords[1];

                    InquiryModel model = new InquiryModel(
                            inquiryId, customerId, customerName, medicineName, description, hasImage, city, barangay, district, status, createdAt, replyCount, lat, lng
                    );

                    if (pharmacyLat != 0.0 && pharmacyLng != 0.0 && cLat != 0.0 && cLng != 0.0) {
                        float[] results = new float[1];
                        android.location.Location.distanceBetween(pharmacyLat, pharmacyLng, cLat, cLng, results);
                        model.setDistanceKm(results[0] / 1000.0);
                    }

                    tempInquiries.add(model);
                }

                con.close();

                mainHandler.post(() -> {
                    if (!isAdded() || getContext() == null) return;
                    allInquiriesList.clear();
                    allInquiriesList.addAll(tempInquiries);
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    if (pbPharmacyFeedLoading != null) pbPharmacyFeedLoading.setVisibility(View.GONE);
                    filterInquiries();
                });

            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
                Log.e("FragmentPharmacyFeed", "Error loading local inquiries: " + e.getMessage());
                mainHandler.post(() -> showFeedError("Couldn't load requests. Please try again."));
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
