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

    private ProgressBar pbPharmacyFeedLoading;
    private TextView tvPharmacyFeedEmptyState;

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

        RecyclerView rvInquiries = view.findViewById(R.id.rvPharmacyInquiries);
        rvInquiries.setLayoutManager(new LinearLayoutManager(getContext()));
        inquiryAdapter = new InquiryAdapter(displayedInquiriesList, item -> {
            Intent intent = new Intent(getContext(), ActivityInquiryThread.class);
            intent.putExtra("inquiry_id", item.getInquiryId());
            startActivity(intent);
        });
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

        if (chipGroupFilter != null) {
            chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.contains(R.id.chipPharmacyOpen)) {
                    currentStatusFilter = "open";
                } else if (checkedIds.contains(R.id.chipPharmacyFulfilled)) {
                    currentStatusFilter = "fulfilled";
                } else {
                    currentStatusFilter = "all";
                }
                filterInquiries();
            });
        }

        return view;
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

                String query = "SELECT i.InquiryID, i.CustomerID, i.CustomerName, i.MedicineName, i.Description, CASE WHEN i.ImageUrl IS NOT NULL AND i.ImageUrl <> '' THEN 1 ELSE 0 END AS HasImage, i.City, i.Barangay, i.District, i.Status, CONVERT(VARCHAR, i.CreatedAt, 120) AS CreatedAtStr, " +
                        "(SELECT COUNT(*) FROM dbo.PharmacyReplies r WHERE r.InquiryID = i.InquiryID) AS ReplyCount " +
                        "FROM dbo.Inquiries i " +
                        "WHERE LOWER(i.Barangay) LIKE '%' + LOWER(?) + '%' " +
                        "   OR LOWER(i.City) LIKE '%' + LOWER(?) + '%' " +
                        "   OR LOWER(?) LIKE '%' + LOWER(i.Barangay) + '%' " +
                        "   OR i.Barangay IS NULL OR i.Barangay = '' OR ? = '' " +
                        "ORDER BY i.CreatedAt DESC";

                PreparedStatement stmt = con.prepareStatement(query);
                String bg = pharmacyBarangay != null ? pharmacyBarangay.trim() : "";
                String ct = pharmacyCity != null ? pharmacyCity.trim() : "";
                stmt.setString(1, bg);
                stmt.setString(2, ct);
                stmt.setString(3, bg);
                stmt.setString(4, bg);

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

                    tempInquiries.add(new InquiryModel(
                            inquiryId, customerId, customerName, medicineName, description, hasImage, city, barangay, district, status, createdAt, replyCount
                    ));
                }

                con.close();

                mainHandler.post(() -> {
                    allInquiriesList.clear();
                    allInquiriesList.addAll(tempInquiries);
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
