package com.example.pharmafind;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FragmentPharmacyTabHistory extends Fragment {

    private conSQL sqlHelper;
    private UserSessionManager sessionManager;
    private InquiryAdapter inquiryAdapter;
    private List<InquiryModel> repliedInquiryList;
    private ProgressBar pbPharmacyHistoryLoading;
    private TextView tvPharmacyHistoryEmptyState;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sqlHelper = new conSQL();
        sessionManager = new UserSessionManager(requireContext());
        repliedInquiryList = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_pharmacy_tab_history, container, false);

        RecyclerView rvRepliedRequests = view.findViewById(R.id.rvRepliedRequests);
        pbPharmacyHistoryLoading = view.findViewById(R.id.pbPharmacyHistoryLoading);
        tvPharmacyHistoryEmptyState = view.findViewById(R.id.tvPharmacyHistoryEmptyState);
        rvRepliedRequests.setLayoutManager(new LinearLayoutManager(getContext()));
        inquiryAdapter = new InquiryAdapter(repliedInquiryList, item -> {
            Intent intent = new Intent(getContext(), ActivityInquiryThread.class);
            intent.putExtra("inquiry_id", item.getInquiryId());
            startActivity(intent);
        });
        rvRepliedRequests.setAdapter(inquiryAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload every time this screen becomes visible again (e.g. returning from a
        // request thread), so reply counts and statuses don't stay stale.
        loadRepliedInquiriesFromDB();
    }

    private void loadRepliedInquiriesFromDB() {
        if (pbPharmacyHistoryLoading != null) pbPharmacyHistoryLoading.setVisibility(View.VISIBLE);
        if (tvPharmacyHistoryEmptyState != null) tvPharmacyHistoryEmptyState.setVisibility(View.GONE);

        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) {
                mainHandler.post(() -> showHistoryError("Couldn't connect. Check your internet and try again."));
                return;
            }

            try {
                List<InquiryModel> tempInquiries = new ArrayList<>();
                String query = "SELECT DISTINCT i.InquiryID, i.CustomerID, i.CustomerName, i.MedicineName, i.Description, CASE WHEN i.ImageUrl IS NOT NULL AND i.ImageUrl <> '' THEN 1 ELSE 0 END AS HasImage, i.City, i.Barangay, i.District, i.Status, CONVERT(VARCHAR, i.CreatedAt, 120) AS CreatedAtStr, " +
                        "(SELECT COUNT(*) FROM dbo.PharmacyReplies r2 WHERE r2.InquiryID = i.InquiryID) AS ReplyCount " +
                        "FROM dbo.Inquiries i " +
                        "INNER JOIN dbo.PharmacyReplies r ON i.InquiryID = r.InquiryID " +
                        "WHERE r.PharmacyID = ? " +
                        "ORDER BY CreatedAtStr DESC";

                PreparedStatement stmt = con.prepareStatement(query);
                stmt.setInt(1, sessionManager.getUserId());
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
                    repliedInquiryList.clear();
                    repliedInquiryList.addAll(tempInquiries);
                    if (inquiryAdapter != null) {
                        inquiryAdapter.notifyDataSetChanged();
                    }
                    if (pbPharmacyHistoryLoading != null) pbPharmacyHistoryLoading.setVisibility(View.GONE);
                    if (tvPharmacyHistoryEmptyState != null) {
                        tvPharmacyHistoryEmptyState.setText("You haven't responded to any requests yet.");
                        tvPharmacyHistoryEmptyState.setVisibility(repliedInquiryList.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });

            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
                Log.e("PharmacyTabHistory", "Load Replied Inquiries Error: " + e.getMessage());
                mainHandler.post(() -> showHistoryError("Couldn't load your responses. Please try again."));
            }
        });
    }

    private void showHistoryError(String message) {
        if (pbPharmacyHistoryLoading != null) pbPharmacyHistoryLoading.setVisibility(View.GONE);
        if (tvPharmacyHistoryEmptyState != null) {
            tvPharmacyHistoryEmptyState.setText(message);
            tvPharmacyHistoryEmptyState.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
