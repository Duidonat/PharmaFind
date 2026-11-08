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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FragmentChats extends Fragment {

    private conSQL sqlHelper;
    private UserSessionManager sessionManager;
    private InquiryAdapter inquiryAdapter;
    private final List<InquiryModel> chatList = new ArrayList<>();

    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar pbChatsLoading;
    private TextView tvChatsEmptyState;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sqlHelper = new conSQL();
        Context ctx = getContext();
        if (ctx != null) {
            sessionManager = new UserSessionManager(ctx.getApplicationContext());
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chats, container, false);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshChats);
        pbChatsLoading = view.findViewById(R.id.pbChatsLoading);
        tvChatsEmptyState = view.findViewById(R.id.tvChatsEmptyState);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeResources(R.color.primary_teal);
            swipeRefreshLayout.setOnRefreshListener(this::loadChatsFromDB);
        }

        RecyclerView rvChats = view.findViewById(R.id.rvChatsList);
        rvChats.setLayoutManager(new LinearLayoutManager(getContext()));
        inquiryAdapter = new InquiryAdapter(chatList, item -> {
            Intent intent = new Intent(getContext(), ActivityChat.class);
            intent.putExtra("inquiry_id", item.getInquiryId());

            if ("pharmacy".equalsIgnoreCase(sessionManager.getRole())) {
                intent.putExtra("receiver_id", item.getCustomerId());
                intent.putExtra("receiver_name", item.getCustomerName() != null ? item.getCustomerName() : "Customer");
            } else {
                int pId = item.getLastPharmacyId() > 0 ? item.getLastPharmacyId() : item.getCustomerId();
                String pName = item.getLastPharmacyName() != null && !item.getLastPharmacyName().isEmpty()
                        ? item.getLastPharmacyName()
                        : "Pharmacy";
                intent.putExtra("receiver_id", pId);
                intent.putExtra("receiver_name", pName);
            }
            startActivity(intent);
        });
        rvChats.setAdapter(inquiryAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadChatsFromDB();
    }

    private void loadChatsFromDB() {
        if (pbChatsLoading != null) pbChatsLoading.setVisibility(View.VISIBLE);
        if (tvChatsEmptyState != null) tvChatsEmptyState.setVisibility(View.GONE);

        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) {
                mainHandler.post(() -> {
                    if (isAdded()) showChatsError("Couldn't connect to database.");
                });
                return;
            }

            try {
                List<InquiryModel> tempChats = new ArrayList<>();
                int currentUserId = sessionManager.getUserId();
                String userRole = sessionManager.getRole();

                String query;
                if ("pharmacy".equalsIgnoreCase(userRole)) {
                    query = "SELECT i.InquiryID, i.CustomerID, i.CustomerName, i.MedicineName, i.Description, " +
                            "CASE WHEN i.ImageUrl IS NOT NULL AND i.ImageUrl <> '' THEN 1 ELSE 0 END AS HasImage, " +
                            "i.City, i.Barangay, i.District, i.Status, CONVERT(VARCHAR, i.CreatedAt, 120) AS CreatedAtStr, " +
                            "COALESCE(i.Latitude, c.Latitude, 0.0) AS Latitude, COALESCE(i.Longitude, c.Longitude, 0.0) AS Longitude, " +
                            "(SELECT COUNT(*) FROM dbo.PharmacyReplies r WHERE r.InquiryID = i.InquiryID) AS ReplyCount " +
                            "FROM dbo.Inquiries i " +
                            "LEFT JOIN dbo.Customers c ON i.CustomerID = c.CustomerID " +
                            "WHERE i.InquiryID IN (SELECT DISTINCT InquiryID FROM dbo.Messages WHERE (SenderID = ? AND SenderRole = 'pharmacy') OR (ReceiverID = ? AND SenderRole = 'pharmacy')) " +
                            "ORDER BY i.CreatedAt DESC";
                } else {
                    query = "SELECT i.InquiryID, i.CustomerID, i.CustomerName, i.MedicineName, i.Description, " +
                            "CASE WHEN i.ImageUrl IS NOT NULL AND i.ImageUrl <> '' THEN 1 ELSE 0 END AS HasImage, " +
                            "i.City, i.Barangay, i.District, i.Status, CONVERT(VARCHAR, i.CreatedAt, 120) AS CreatedAtStr, " +
                            "COALESCE(i.Latitude, c.Latitude, 0.0) AS Latitude, COALESCE(i.Longitude, c.Longitude, 0.0) AS Longitude, " +
                            "(SELECT COUNT(*) FROM dbo.PharmacyReplies r WHERE r.InquiryID = i.InquiryID) AS ReplyCount, " +
                            "(SELECT TOP 1 m.SenderID FROM dbo.Messages m WHERE m.InquiryID = i.InquiryID AND m.ReceiverID = ? AND m.SenderRole = 'pharmacy' ORDER BY m.CreatedAt DESC) AS LastPharmacyID, " +
                            "(SELECT TOP 1 m.SenderName FROM dbo.Messages m WHERE m.InquiryID = i.InquiryID AND m.ReceiverID = ? AND m.SenderRole = 'pharmacy' ORDER BY m.CreatedAt DESC) AS LastPharmacyName " +
                            "FROM dbo.Inquiries i " +
                            "LEFT JOIN dbo.Customers c ON i.CustomerID = c.CustomerID " +
                            "WHERE i.CustomerID = ? " +
                            "  AND i.InquiryID IN (SELECT DISTINCT InquiryID FROM dbo.Messages) " +
                            "ORDER BY i.CreatedAt DESC";
                }

                PreparedStatement stmt = con.prepareStatement(query);
                if ("pharmacy".equalsIgnoreCase(userRole)) {
                    stmt.setInt(1, currentUserId);
                    stmt.setInt(2, currentUserId);
                } else {
                    stmt.setInt(1, currentUserId);
                    stmt.setInt(2, currentUserId);
                    stmt.setInt(3, currentUserId);
                }

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

                    InquiryModel model = new InquiryModel(
                            inquiryId, customerId, customerName, medicineName, description, hasImage, city, barangay, district, status, createdAt, replyCount, lat, lng
                    );
                    model.setHasCustomerMessage(true);
                    model.setHasPharmacyMessage(true);

                    if (!"pharmacy".equalsIgnoreCase(userRole)) {
                        int lastPharmId = rs.getInt("LastPharmacyID");
                        String lastPharmName = rs.getString("LastPharmacyName");
                        model.setLastPharmacyId(lastPharmId);
                        model.setLastPharmacyName(lastPharmName);
                    }

                    tempChats.add(model);
                }

                con.close();

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    chatList.clear();
                    chatList.addAll(tempChats);
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    if (pbChatsLoading != null) pbChatsLoading.setVisibility(View.GONE);
                    if (inquiryAdapter != null) inquiryAdapter.notifyDataSetChanged();
                    if (tvChatsEmptyState != null) {
                        tvChatsEmptyState.setText("No active chat communications yet.");
                        tvChatsEmptyState.setVisibility(chatList.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });

            } catch (SQLException e) {
                try { con.close(); } catch (SQLException ignored) {}
                Log.e("FragmentChats", "Error loading chats: " + e.getMessage());
                mainHandler.post(() -> {
                    if (isAdded()) showChatsError("Couldn't load chats.");
                });
            }
        });
    }

    private void showChatsError(String message) {
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        if (pbChatsLoading != null) pbChatsLoading.setVisibility(View.GONE);
        if (tvChatsEmptyState != null) {
            tvChatsEmptyState.setText(message);
            tvChatsEmptyState.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
