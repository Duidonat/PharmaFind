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
    private ChatThreadAdapter chatThreadAdapter;
    private final List<ChatThreadModel> chatList = new ArrayList<>();

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
        chatThreadAdapter = new ChatThreadAdapter(chatList, item -> {
            Intent intent = new Intent(getContext(), ActivityChat.class);
            intent.putExtra("inquiry_id", item.getInquiryId());
            intent.putExtra("receiver_id", item.getPartnerId());
            intent.putExtra("receiver_name", item.getPartnerName() != null ? item.getPartnerName() : "User");
            startActivity(intent);
        });
        rvChats.setAdapter(chatThreadAdapter);

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
                List<ChatThreadModel> tempChats = new ArrayList<>();
                int currentUserId = sessionManager.getUserId();
                String userRole = sessionManager.getRole();

                String query;
                if ("pharmacy".equalsIgnoreCase(userRole)) {
                    // Pharmacy Query: Retrieves messages involving this pharmacy, grouped by InquiryID, ordered by LATEST message timestamp
                    query = "SELECT m.InquiryID, " +
                            "MAX(m.CreatedAt) AS LastMessageTime, " +
                            "CONVERT(VARCHAR, MAX(m.CreatedAt), 108) AS TimeStr, " +
                            "(SELECT TOP 1 MessageText FROM dbo.Messages WHERE InquiryID = m.InquiryID ORDER BY CreatedAt DESC) AS LastMessageText, " +
                            "i.CustomerID, " +
                            "i.CustomerName, " +
                            "i.MedicineName " +
                            "FROM dbo.Messages m " +
                            "JOIN dbo.Inquiries i ON i.InquiryID = m.InquiryID " +
                            "WHERE (m.SenderID = ? AND m.SenderRole = 'pharmacy') " +
                            "   OR (m.ReceiverID = ? AND m.SenderRole = 'customer') " +
                            "GROUP BY m.InquiryID, i.CustomerID, i.CustomerName, i.MedicineName " +
                            "ORDER BY LastMessageTime DESC";
                } else {
                    // Customer Query: Retrieves messages for inquiries posted by this customer, ordered by LATEST message timestamp
                    query = "SELECT m.InquiryID, " +
                            "MAX(m.CreatedAt) AS LastMessageTime, " +
                            "CONVERT(VARCHAR, MAX(m.CreatedAt), 108) AS TimeStr, " +
                            "(SELECT TOP 1 MessageText FROM dbo.Messages WHERE InquiryID = m.InquiryID ORDER BY CreatedAt DESC) AS LastMessageText, " +
                            "(SELECT TOP 1 SenderID FROM dbo.Messages WHERE InquiryID = m.InquiryID AND SenderRole = 'pharmacy' ORDER BY CreatedAt DESC) AS LastPharmacyID, " +
                            "(SELECT TOP 1 SenderName FROM dbo.Messages WHERE InquiryID = m.InquiryID AND SenderRole = 'pharmacy' ORDER BY CreatedAt DESC) AS LastPharmacyName, " +
                            "i.CustomerID, " +
                            "i.MedicineName " +
                            "FROM dbo.Messages m " +
                            "JOIN dbo.Inquiries i ON i.InquiryID = m.InquiryID " +
                            "WHERE i.CustomerID = ? " +
                            "GROUP BY m.InquiryID, i.CustomerID, i.MedicineName " +
                            "ORDER BY LastMessageTime DESC";
                }

                PreparedStatement stmt = con.prepareStatement(query);
                if ("pharmacy".equalsIgnoreCase(userRole)) {
                    stmt.setInt(1, currentUserId);
                    stmt.setInt(2, currentUserId);
                } else {
                    stmt.setInt(1, currentUserId);
                }

                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    int inquiryId = rs.getInt("InquiryID");
                    String medicineName = rs.getString("MedicineName");
                    String lastMsg = rs.getString("LastMessageText");
                    String timeStr = rs.getString("TimeStr");

                    int partnerId;
                    String partnerName;

                    if ("pharmacy".equalsIgnoreCase(userRole)) {
                        partnerId = rs.getInt("CustomerID");
                        partnerName = rs.getString("CustomerName");
                    } else {
                        partnerId = rs.getInt("LastPharmacyID");
                        partnerName = rs.getString("LastPharmacyName");
                    }

                    if (partnerName == null || partnerName.trim().isEmpty()) {
                        partnerName = "pharmacy".equalsIgnoreCase(userRole) ? "Patient" : "Pharmacy Store";
                    }

                    ChatThreadModel model = new ChatThreadModel(
                            inquiryId, partnerId, partnerName, medicineName, lastMsg, timeStr
                    );

                    tempChats.add(model);
                }

                con.close();

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    chatList.clear();
                    chatList.addAll(tempChats);
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    if (pbChatsLoading != null) pbChatsLoading.setVisibility(View.GONE);
                    if (chatThreadAdapter != null) chatThreadAdapter.notifyDataSetChanged();
                    if (tvChatsEmptyState != null) {
                        tvChatsEmptyState.setText("No active chat conversations yet.");
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
