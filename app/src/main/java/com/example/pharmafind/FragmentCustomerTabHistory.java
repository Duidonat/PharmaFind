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

public class FragmentCustomerTabHistory extends Fragment {

    private conSQL sqlHelper;
    private UserSessionManager sessionManager;
    private InquiryAdapter inquiryAdapter;
    private List<InquiryModel> myInquiryList;
    private ProgressBar pbHistoryLoading;
    private TextView tvHistoryEmptyState;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sqlHelper = new conSQL();
        sessionManager = new UserSessionManager(requireContext());
        myInquiryList = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_customer_tab_history, container, false);

        RecyclerView rvMyRequests = view.findViewById(R.id.rvMyRequests);
        pbHistoryLoading = view.findViewById(R.id.pbHistoryLoading);
        tvHistoryEmptyState = view.findViewById(R.id.tvHistoryEmptyState);
        rvMyRequests.setLayoutManager(new LinearLayoutManager(getContext()));
        inquiryAdapter = new InquiryAdapter(myInquiryList, item -> {
            Intent intent = new Intent(getContext(), ActivityInquiryThread.class);
            intent.putExtra("inquiry_id", item.getInquiryId());
            startActivity(intent);
        });
        rvMyRequests.setAdapter(inquiryAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload every time this screen becomes visible again (e.g. returning from a
        // request thread), so reply counts and statuses don't stay stale.
        loadMyInquiriesFromDB();
    }

    private void loadMyInquiriesFromDB() {
        if (pbHistoryLoading != null) pbHistoryLoading.setVisibility(View.VISIBLE);
        if (tvHistoryEmptyState != null) tvHistoryEmptyState.setVisibility(View.GONE);

        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) {
                mainHandler.post(() -> showHistoryError("Couldn't connect. Check your internet and try again."));
                return;
            }

            try {
                List<InquiryModel> tempInquiries = new ArrayList<>();
                String query = "SELECT i.InquiryID, i.CustomerID, i.CustomerName, i.MedicineName, i.Description, CASE WHEN i.ImageUrl IS NOT NULL AND i.ImageUrl <> '' THEN 1 ELSE 0 END AS HasImage, i.City, i.Barangay, i.District, i.Status, CONVERT(VARCHAR, i.CreatedAt, 120) AS CreatedAtStr, " +
                        "(SELECT COUNT(*) FROM dbo.PharmacyReplies r WHERE r.InquiryID = i.InquiryID) AS ReplyCount " +
                        "FROM dbo.Inquiries i " +
                        "WHERE i.CustomerID = ? " +
                        "ORDER BY i.CreatedAt DESC";

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
                    myInquiryList.clear();
                    myInquiryList.addAll(tempInquiries);
                    if (inquiryAdapter != null) {
                        inquiryAdapter.notifyDataSetChanged();
                    }
                    if (pbHistoryLoading != null) pbHistoryLoading.setVisibility(View.GONE);
                    if (tvHistoryEmptyState != null) {
                        tvHistoryEmptyState.setText("No requests posted yet.");
                        tvHistoryEmptyState.setVisibility(myInquiryList.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });

            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
                Log.e("CustomerTabHistory", "Load My Inquiries Error: " + e.getMessage());
                mainHandler.post(() -> showHistoryError("Couldn't load your requests. Please try again."));
            }
        });
    }

    private void showHistoryError(String message) {
        if (pbHistoryLoading != null) pbHistoryLoading.setVisibility(View.GONE);
        if (tvHistoryEmptyState != null) {
            tvHistoryEmptyState.setText(message);
            tvHistoryEmptyState.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
