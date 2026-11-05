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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FragmentPharmacyNotifications extends Fragment {

    private conSQL sqlHelper;
    private UserSessionManager sessionManager;
    private InquiryAdapter inquiryAdapter;
    private List<InquiryModel> notificationsList;

    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar pbPharmacyNotifLoading;
    private TextView tvPharmacyNotifEmptyState;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sqlHelper = new conSQL();
        sessionManager = new UserSessionManager(requireContext());
        notificationsList = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_pharmacy_notifications, container, false);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshPharmacyNotifs);
        pbPharmacyNotifLoading = view.findViewById(R.id.pbPharmacyNotifLoading);
        tvPharmacyNotifEmptyState = view.findViewById(R.id.tvPharmacyNotifEmptyState);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeResources(R.color.primary_teal);
            swipeRefreshLayout.setOnRefreshListener(this::loadNotificationsFromDB);
        }

        RecyclerView rvNotifications = view.findViewById(R.id.rvPharmacyNotifications);
        rvNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
        inquiryAdapter = new InquiryAdapter(notificationsList, item -> {
            if (item.hasCustomerMessage()) {
                Intent intent = new Intent(getContext(), ActivityChat.class);
                intent.putExtra("inquiry_id", item.getInquiryId());
                intent.putExtra("receiver_id", item.getCustomerId());
                intent.putExtra("receiver_name", item.getCustomerName() != null ? item.getCustomerName() : "Customer");
                startActivity(intent);
            } else {
                Intent intent = new Intent(getContext(), ActivityInquiryThread.class);
                intent.putExtra("inquiry_id", item.getInquiryId());
                intent.putExtra("auto_open_chat", true);
                startActivity(intent);
            }
        });
        rvNotifications.setAdapter(inquiryAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadNotificationsFromDB();
    }

    private void loadNotificationsFromDB() {
        if (pbPharmacyNotifLoading != null) pbPharmacyNotifLoading.setVisibility(View.VISIBLE);
        if (tvPharmacyNotifEmptyState != null) tvPharmacyNotifEmptyState.setVisibility(View.GONE);

        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) {
                mainHandler.post(() -> showNotifError("Couldn't connect. Check your internet and try again."));
                return;
            }

            try {
                List<InquiryModel> tempInquiries = new ArrayList<>();
                String pharmacyBarangay = sessionManager.getBarangay();
                String pharmacyCity = sessionManager.getCity();

                double pharmacyLat = sessionManager.getLatitude();
                double pharmacyLng = sessionManager.getLongitude();
                double[] pCoords = LocationUtils.getCoordinates(pharmacyCity, pharmacyBarangay, pharmacyLat, pharmacyLng);
                pharmacyLat = pCoords[0];
                pharmacyLng = pCoords[1];

                // Load recent open patient inquiries & customer chat messages for pharmacy
                String query = "SELECT i.InquiryID, i.CustomerID, i.CustomerName, i.MedicineName, i.Description, " +
                        "CASE WHEN i.ImageUrl IS NOT NULL AND i.ImageUrl <> '' THEN 1 ELSE 0 END AS HasImage, " +
                        "i.City, i.Barangay, i.District, i.Status, CONVERT(VARCHAR, i.CreatedAt, 120) AS CreatedAtStr, " +
                        "COALESCE(i.Latitude, c.Latitude, 0.0) AS Latitude, COALESCE(i.Longitude, c.Longitude, 0.0) AS Longitude, " +
                        "(SELECT COUNT(*) FROM dbo.PharmacyReplies r WHERE r.InquiryID = i.InquiryID) AS ReplyCount, " +
                        "(SELECT COUNT(*) FROM dbo.Messages m WHERE m.InquiryID = i.InquiryID AND m.ReceiverID = ? AND m.SenderRole = 'customer') AS CustomerMsgCount " +
                        "FROM dbo.Inquiries i " +
                        "LEFT JOIN dbo.Customers c ON i.CustomerID = c.CustomerID " +
                        "WHERE i.InquiryID IN (SELECT InquiryID FROM dbo.Messages WHERE ReceiverID = ? AND SenderRole = 'customer') " +
                        "   OR (LOWER(i.Status) = 'open' " +
                        "       AND (LOWER(i.Barangay) LIKE '%' + LOWER(?) + '%' " +
                        "            OR LOWER(i.City) LIKE '%' + LOWER(?) + '%' " +
                        "            OR LOWER(?) LIKE '%' + LOWER(i.Barangay) + '%' " +
                        "            OR i.Barangay IS NULL OR i.Barangay = '' OR ? = '')) " +
                        "ORDER BY i.CreatedAt DESC";

                PreparedStatement stmt = con.prepareStatement(query);
                String bg = pharmacyBarangay != null ? pharmacyBarangay.trim() : "";
                String ct = pharmacyCity != null ? pharmacyCity.trim() : "";
                stmt.setInt(1, sessionManager.getUserId());
                stmt.setInt(2, sessionManager.getUserId());
                stmt.setString(3, bg);
                stmt.setString(4, ct);
                stmt.setString(5, bg);
                stmt.setString(6, bg);

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
                    int customerMsgCount = rs.getInt("CustomerMsgCount");
                    double lat = rs.getDouble("Latitude");
                    double lng = rs.getDouble("Longitude");

                    double[] cCoords = LocationUtils.getCoordinates(city, barangay, lat, lng);
                    double cLat = cCoords[0];
                    double cLng = cCoords[1];

                    InquiryModel model = new InquiryModel(
                            inquiryId, customerId, customerName, medicineName, description, hasImage, city, barangay, district, status, createdAt, replyCount, lat, lng
                    );
                    model.setHasCustomerMessage(customerMsgCount > 0);

                    if (pharmacyLat != 0.0 && pharmacyLng != 0.0 && cLat != 0.0 && cLng != 0.0) {
                        float[] results = new float[1];
                        android.location.Location.distanceBetween(pharmacyLat, pharmacyLng, cLat, cLng, results);
                        model.setDistanceKm(results[0] / 1000.0);
                    }

                    tempInquiries.add(model);
                }

                con.close();

                mainHandler.post(() -> {
                    notificationsList.clear();
                    notificationsList.addAll(tempInquiries);
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    if (pbPharmacyNotifLoading != null) pbPharmacyNotifLoading.setVisibility(View.GONE);
                    if (inquiryAdapter != null) inquiryAdapter.notifyDataSetChanged();
                    if (tvPharmacyNotifEmptyState != null) {
                        tvPharmacyNotifEmptyState.setText("No new patient notifications.");
                        tvPharmacyNotifEmptyState.setVisibility(notificationsList.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });

            } catch (SQLException e) {
                try { con.close(); } catch (SQLException ignored) {}
                Log.e("PharmacyNotifications", "Error loading notifications: " + e.getMessage());
                mainHandler.post(() -> showNotifError("Couldn't load notifications."));
            }
        });
    }

    private void showNotifError(String message) {
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        if (pbPharmacyNotifLoading != null) pbPharmacyNotifLoading.setVisibility(View.GONE);
        if (tvPharmacyNotifEmptyState != null) {
            tvPharmacyNotifEmptyState.setText(message);
            tvPharmacyNotifEmptyState.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
