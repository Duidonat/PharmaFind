package com.example.pharmafind;

import android.content.Context;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.chip.ChipGroup;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FragmentCustomerNotifications extends Fragment {

    private conSQL sqlHelper;
    private UserSessionManager sessionManager;
    private InquiryAdapter inquiryAdapter;
    private final List<InquiryModel> allNotificationsList = new ArrayList<>();
    private final List<InquiryModel> displayedList = new ArrayList<>();

    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar pbCustomerNotifLoading;
    private TextView tvCustomerNotifEmptyState;
    private ChipGroup chipGroupFilter;
    private int selectedFilterId = R.id.chipCustomerAll;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sqlHelper = new conSQL();
        sessionManager = new UserSessionManager(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_customer_notifications, container, false);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshCustomerNotifs);
        pbCustomerNotifLoading = view.findViewById(R.id.pbCustomerNotifLoading);
        tvCustomerNotifEmptyState = view.findViewById(R.id.tvCustomerNotifEmptyState);

        chipGroupFilter = view.findViewById(R.id.chipGroupCustomerFilter);
        if (chipGroupFilter != null) {
            chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (!checkedIds.isEmpty()) {
                    selectedFilterId = checkedIds.get(0);
                    applyFilter();
                }
            });
        }

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeResources(R.color.primary_teal);
            swipeRefreshLayout.setOnRefreshListener(this::loadCustomerNotificationsFromDB);
        }

        RecyclerView rvNotifications = view.findViewById(R.id.rvCustomerNotifications);
        rvNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
        inquiryAdapter = new InquiryAdapter(displayedList, item -> {
            if (selectedFilterId == R.id.chipCustomerChats || (item.hasPharmacyMessage() && item.getLastPharmacyId() > 0)) {
                Intent intent = new Intent(getContext(), ActivityChat.class);
                intent.putExtra("inquiry_id", item.getInquiryId());
                intent.putExtra("receiver_id", item.getLastPharmacyId());
                intent.putExtra("receiver_name", item.getLastPharmacyName() != null && !item.getLastPharmacyName().isEmpty() ? item.getLastPharmacyName() : "Pharmacy");
                startActivity(intent);
            } else {
                Intent intent = new Intent(getContext(), ActivityInquiryThread.class);
                intent.putExtra("inquiry_id", item.getInquiryId());
                startActivity(intent);
            }
        });
        inquiryAdapter.setRadiusKm(sessionManager.getProximityRadiusKm());
        rvNotifications.setAdapter(inquiryAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCustomerNotificationsFromDB();
    }

    private void loadCustomerNotificationsFromDB() {
        if (pbCustomerNotifLoading != null) pbCustomerNotifLoading.setVisibility(View.VISIBLE);
        if (tvCustomerNotifEmptyState != null) tvCustomerNotifEmptyState.setVisibility(View.GONE);

        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) {
                mainHandler.post(() -> showNotifError("Couldn't connect. Check your internet and try again."));
                return;
            }

            try {
                List<InquiryModel> tempInquiries = new ArrayList<>();
                int customerId = sessionManager.getUserId();

                Context ctx = getContext();
                if (ctx == null) return;

                // Update last seen IDs so badge clears
                NotificationState state = new NotificationState(ctx.getApplicationContext());
                String maxReplySql = "SELECT ISNULL(MAX(r.ReplyID), 0) FROM dbo.PharmacyReplies r JOIN dbo.Inquiries i ON i.InquiryID = r.InquiryID WHERE i.CustomerID = ?";
                PreparedStatement maxReplyPs = con.prepareStatement(maxReplySql);
                maxReplyPs.setInt(1, customerId);
                ResultSet replyRs = maxReplyPs.executeQuery();
                if (replyRs.next()) state.set("customer", customerId, "reply", replyRs.getInt(1));
                maxReplyPs.close();

                String maxMsgSql = "SELECT ISNULL(MAX(MessageID), 0) FROM dbo.Messages WHERE ReceiverID = ? AND SenderRole = 'pharmacy'";
                PreparedStatement maxMsgPs = con.prepareStatement(maxMsgSql);
                maxMsgPs.setInt(1, customerId);
                ResultSet msgRs = maxMsgPs.executeQuery();
                if (msgRs.next()) state.set("customer", customerId, "msg", msgRs.getInt(1));
                maxMsgPs.close();

                // Select customer's inquiries that have pharmacy responses
                String query = "SELECT i.InquiryID, i.CustomerID, i.CustomerName, i.MedicineName, i.Description, " +
                        "CASE WHEN i.ImageUrl IS NOT NULL AND i.ImageUrl <> '' THEN 1 ELSE 0 END AS HasImage, " +
                        "i.City, i.Barangay, i.District, i.Status, CONVERT(VARCHAR, i.CreatedAt, 120) AS CreatedAtStr, " +
                        "COALESCE(i.Latitude, c.Latitude, 0.0) AS Latitude, COALESCE(i.Longitude, c.Longitude, 0.0) AS Longitude, " +
                        "(SELECT COUNT(*) FROM dbo.PharmacyReplies r WHERE r.InquiryID = i.InquiryID) AS ReplyCount " +
                        "FROM dbo.Inquiries i " +
                        "LEFT JOIN dbo.Customers c ON i.CustomerID = c.CustomerID " +
                        "WHERE i.CustomerID = ? AND LOWER(ISNULL(i.Status, 'open')) <> 'fulfilled' " +
                        "  AND (SELECT COUNT(*) FROM dbo.PharmacyReplies r WHERE r.InquiryID = i.InquiryID) > 0 " +
                        "ORDER BY i.CreatedAt DESC";

                PreparedStatement stmt = con.prepareStatement(query);
                stmt.setInt(1, customerId);

                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    int inquiryId = rs.getInt("InquiryID");
                    int custId = rs.getInt("CustomerID");
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

                    InquiryModel model = new InquiryModel(
                            inquiryId, custId, customerName, medicineName, description, hasImage, city, barangay, district, status, createdAt, replyCount, lat, lng
                    );
                    tempInquiries.add(model);
                }

                con.close();

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    allNotificationsList.clear();
                    allNotificationsList.addAll(tempInquiries);
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    if (pbCustomerNotifLoading != null) pbCustomerNotifLoading.setVisibility(View.GONE);
                    applyFilter();
                });

            } catch (SQLException e) {
                try { con.close(); } catch (SQLException ignored) {}
                Log.e("CustomerNotifications", "Error loading customer notifications: " + e.getMessage());
                mainHandler.post(() -> showNotifError("Couldn't load notifications."));
            }
        });
    }

    private void applyFilter() {
        displayedList.clear();
        for (InquiryModel item : allNotificationsList) {
            if (selectedFilterId == R.id.chipCustomerResponses) {
                if (item.getReplyCount() > 0) {
                    displayedList.add(item);
                }
            } else if (selectedFilterId == R.id.chipCustomerChats) {
                if (item.hasPharmacyMessage()) {
                    displayedList.add(item);
                }
            } else {
                displayedList.add(item);
            }
        }

        if (inquiryAdapter != null) inquiryAdapter.notifyDataSetChanged();

        if (tvCustomerNotifEmptyState != null) {
            if (displayedList.isEmpty()) {
                if (selectedFilterId == R.id.chipCustomerResponses) {
                    tvCustomerNotifEmptyState.setText("No pharmacy responses yet.");
                } else if (selectedFilterId == R.id.chipCustomerChats) {
                    tvCustomerNotifEmptyState.setText("No active chats yet.");
                } else {
                    tvCustomerNotifEmptyState.setText("No active chats or pharmacy replies yet.");
                }
                tvCustomerNotifEmptyState.setVisibility(View.VISIBLE);
            } else {
                tvCustomerNotifEmptyState.setVisibility(View.GONE);
            }
        }
    }

    private void showNotifError(String message) {
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        if (pbCustomerNotifLoading != null) pbCustomerNotifLoading.setVisibility(View.GONE);
        if (tvCustomerNotifEmptyState != null) {
            tvCustomerNotifEmptyState.setText(message);
            tvCustomerNotifEmptyState.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
