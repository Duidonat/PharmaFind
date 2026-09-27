package com.example.pharmafind;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
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

public class FragmentCustomerTabHistory extends Fragment {

    private conSQL sqlHelper;
    private UserSessionManager sessionManager;
    private InquiryAdapter inquiryAdapter;
    private List<InquiryModel> myInquiryList;

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
        rvMyRequests.setLayoutManager(new LinearLayoutManager(getContext()));
        inquiryAdapter = new InquiryAdapter(myInquiryList, item -> {
            Intent intent = new Intent(getContext(), ActivityInquiryThread.class);
            intent.putExtra("inquiry_id", item.getInquiryId());
            startActivity(intent);
        });
        rvMyRequests.setAdapter(inquiryAdapter);

        loadMyInquiriesFromDB();

        return view;
    }

    private void loadMyInquiriesFromDB() {
        Connection con = sqlHelper.conclass();
        if (con == null) return;

        try {
            myInquiryList.clear();
            String query = "SELECT i.InquiryID, i.CustomerID, i.CustomerName, i.MedicineName, i.Description, i.ImageUrl, i.City, i.Barangay, i.District, i.Status, CONVERT(VARCHAR, i.CreatedAt, 120) AS CreatedAtStr, " +
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
                String imageUrl = rs.getString("ImageUrl");
                String city = rs.getString("City");
                String barangay = rs.getString("Barangay");
                String district = rs.getString("District");
                String status = rs.getString("Status");
                String createdAt = rs.getString("CreatedAtStr");
                int replyCount = rs.getInt("ReplyCount");

                myInquiryList.add(new InquiryModel(
                        inquiryId, customerId, customerName, medicineName, description, imageUrl, city, barangay, district, status, createdAt, replyCount
                ));
            }

            con.close();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> inquiryAdapter.notifyDataSetChanged());
            }
        } catch (SQLException e) {
            Log.e("CustomerTabHistory", "Load My Inquiries Error: " + e.getMessage());
        }
    }
}
