package com.example.pharmafind;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ActivityInquiryThread extends AppCompatActivity {

    private conSQL sqlHelper;
    private UserSessionManager sessionManager;
    private int inquiryId = -1;

    private TextView tvCustomerName, tvLocationAndDate, tvStatusBadge, tvMedicineName, tvDescription, tvRepliesHeader;
    private ImageView ivInquiryImage;
    private RecyclerView rvReplies;
    private PharmacyReplyAdapter replyAdapter;
    private List<PharmacyReplyModel> replyList;

    private MaterialCardView cardPharmacyReplyComposer;
    private EditText etReplyPrice, etReplyNote;
    private MaterialButton btnSubmitPharmacyReply;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inquiry_thread);

        sqlHelper = new conSQL();
        sessionManager = new UserSessionManager(this);
        replyList = new ArrayList<>();

        inquiryId = getIntent().getIntExtra("inquiry_id", -1);
        if (inquiryId == -1) {
            Toast.makeText(this, "Invalid Inquiry", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ImageButton btnBack = findViewById(R.id.btnBackThread);
        btnBack.setOnClickListener(v -> finish());

        tvCustomerName = findViewById(R.id.tvCustomerName);
        tvLocationAndDate = findViewById(R.id.tvLocationAndDate);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvMedicineName = findViewById(R.id.tvMedicineName);
        tvDescription = findViewById(R.id.tvDescription);
        tvRepliesHeader = findViewById(R.id.tvRepliesHeader);
        ivInquiryImage = findViewById(R.id.ivInquiryImage);

        rvReplies = findViewById(R.id.rvReplies);
        rvReplies.setLayoutManager(new LinearLayoutManager(this));
        replyAdapter = new PharmacyReplyAdapter(replyList, new PharmacyReplyAdapter.OnReplyActionListener() {
            @Override
            public void onViewProfileClick(PharmacyReplyModel reply) {
                Intent intent = new Intent(ActivityInquiryThread.this, ActivityPharmacyProfile.class);
                intent.putExtra("pharmacy_id", reply.getPharmacyId());
                startActivity(intent);
            }

            @Override
            public void onContactClick(PharmacyReplyModel reply) {
                String contact = reply.getPharmacyContact();
                if (contact != null && !contact.trim().isEmpty()) {
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + contact));
                    startActivity(intent);
                } else {
                    Toast.makeText(ActivityInquiryThread.this, "No phone contact available", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onAcceptOfferClick(PharmacyReplyModel reply) {
                markInquiryFulfilled(reply);
            }
        });
        rvReplies.setAdapter(replyAdapter);

        cardPharmacyReplyComposer = findViewById(R.id.cardPharmacyReplyComposer);
        etReplyPrice = findViewById(R.id.etReplyPrice);
        etReplyNote = findViewById(R.id.etReplyNote);
        btnSubmitPharmacyReply = findViewById(R.id.btnSubmitPharmacyReply);

        if ("pharmacy".equalsIgnoreCase(sessionManager.getRole())) {
            cardPharmacyReplyComposer.setVisibility(View.VISIBLE);
            btnSubmitPharmacyReply.setOnClickListener(v -> submitPharmacyReply());
        } else {
            cardPharmacyReplyComposer.setVisibility(View.GONE);
        }

        loadInquiryDetails();
        loadPharmacyReplies();
    }

    private void loadInquiryDetails() {
        Connection con = sqlHelper.conclass();
        if (con == null) return;

        try {
            String query = "SELECT CustomerName, MedicineName, Description, ImageUrl, City, Barangay, District, Status, CONVERT(VARCHAR, CreatedAt, 120) AS CreatedAtStr FROM dbo.Inquiries WHERE InquiryID = ?";
            PreparedStatement stmt = con.prepareStatement(query);
            stmt.setInt(1, inquiryId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String customerName = rs.getString("CustomerName");
                String medicineName = rs.getString("MedicineName");
                String description = rs.getString("Description");
                String imageUrl = rs.getString("ImageUrl");
                String city = rs.getString("City");
                String barangay = rs.getString("Barangay");
                String status = rs.getString("Status");
                String createdAt = rs.getString("CreatedAtStr");

                tvCustomerName.setText(customerName != null ? customerName : "Anonymous");
                tvLocationAndDate.setText("Barangay " + (barangay != null ? barangay : "") + " • " + (createdAt != null ? createdAt : ""));
                tvMedicineName.setText("Need: " + (medicineName != null ? medicineName : ""));
                tvDescription.setText(description != null ? description : "");
                tvStatusBadge.setText(status != null ? status.toUpperCase() : "OPEN");

                if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                    try {
                        String imageStr = imageUrl;
                        if (imageStr.contains(",")) imageStr = imageStr.split(",")[1];
                        byte[] decodedBytes = Base64.decode(imageStr, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                        if (bitmap != null) {
                            ivInquiryImage.setImageBitmap(bitmap);
                            ivInquiryImage.setVisibility(View.VISIBLE);
                        } else {
                            ivInquiryImage.setVisibility(View.GONE);
                        }
                    } catch (Exception e) {
                        ivInquiryImage.setVisibility(View.GONE);
                    }
                } else {
                    ivInquiryImage.setVisibility(View.GONE);
                }
            }

            con.close();
        } catch (SQLException e) {
            Log.e("ActivityInquiryThread", "Error loading inquiry details: " + e.getMessage());
        }
    }

    private void loadPharmacyReplies() {
        Connection con = sqlHelper.conclass();
        if (con == null) return;

        try {
            replyList.clear();
            String query = "SELECT r.ReplyID, r.InquiryID, r.PharmacyID, p.PharmacyName, r.AvailabilityStatus, r.Price, r.Note, CONVERT(VARCHAR, r.CreatedAt, 120) AS CreatedAtStr, p.City, p.Barangay, p.Contact " +
                    "FROM dbo.PharmacyReplies r " +
                    "LEFT JOIN dbo.Pharmacies p ON r.PharmacyID = p.PharmacyID " +
                    "WHERE r.InquiryID = ? " +
                    "ORDER BY r.CreatedAt ASC";

            PreparedStatement stmt = con.prepareStatement(query);
            stmt.setInt(1, inquiryId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                int replyId = rs.getInt("ReplyID");
                int pharmacyId = rs.getInt("PharmacyID");
                String pharmacyName = rs.getString("PharmacyName");
                String availStatus = rs.getString("AvailabilityStatus");
                String price = rs.getString("Price");
                String note = rs.getString("Note");
                String createdAt = rs.getString("CreatedAtStr");
                String city = rs.getString("City");
                String barangay = rs.getString("Barangay");
                String contact = rs.getString("Contact");

                replyList.add(new PharmacyReplyModel(
                        replyId, inquiryId, pharmacyId, pharmacyName, availStatus, price, note, createdAt, city, barangay, contact
                ));
            }

            con.close();

            tvRepliesHeader.setText("💬 Pharmacy Responses (" + replyList.size() + ")");
            replyAdapter.notifyDataSetChanged();

        } catch (SQLException e) {
            Log.e("ActivityInquiryThread", "Error loading pharmacy replies: " + e.getMessage());
        }
    }

    private void markInquiryFulfilled(PharmacyReplyModel reply) {
        Connection con = sqlHelper.conclass();
        if (con == null) return;

        try {
            String query = "UPDATE dbo.Inquiries SET Status = 'fulfilled' WHERE InquiryID = ?";
            PreparedStatement stmt = con.prepareStatement(query);
            stmt.setInt(1, inquiryId);
            int rows = stmt.executeUpdate();
            con.close();

            if (rows > 0) {
                Toast.makeText(this, "Pharmacy Selected! Opening Store Location...", Toast.LENGTH_LONG).show();
                if (tvStatusBadge != null) tvStatusBadge.setText("FULFILLED");

                Intent intent = new Intent(ActivityInquiryThread.this, ActivityPharmacyProfile.class);
                intent.putExtra("pharmacy_id", reply.getPharmacyId());
                startActivity(intent);
            }
        } catch (SQLException e) {
            Log.e("ActivityInquiryThread", "Error fulfilling inquiry: " + e.getMessage());
        }
    }

    private void submitPharmacyReply() {
        String price = etReplyPrice.getText().toString().trim();
        String note = etReplyNote.getText().toString().trim();

        if (price.isEmpty() && note.isEmpty()) {
            Toast.makeText(this, "Please enter price or note", Toast.LENGTH_SHORT).show();
            return;
        }

        Connection con = sqlHelper.conclass();
        if (con == null) {
            Toast.makeText(this, "Database connection error", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String query = "INSERT INTO dbo.PharmacyReplies (InquiryID, PharmacyID, PharmacyName, AvailabilityStatus, Price, Note) VALUES (?, ?, ?, 'Available', ?, ?)";
            PreparedStatement stmt = con.prepareStatement(query);
            stmt.setInt(1, inquiryId);
            stmt.setInt(2, sessionManager.getUserId());
            stmt.setString(3, sessionManager.getFullName());
            stmt.setString(4, price);
            stmt.setString(5, note);

            int rows = stmt.executeUpdate();
            if (rows > 0) {
                Toast.makeText(this, "Response posted!", Toast.LENGTH_SHORT).show();
                etReplyPrice.setText("");
                etReplyNote.setText("");
                loadPharmacyReplies();
            } else {
                Toast.makeText(this, "Failed to post response", Toast.LENGTH_SHORT).show();
            }

            con.close();
        } catch (SQLException e) {
            Log.e("ActivityInquiryThread", "Submit Reply Error: " + e.getMessage());
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
