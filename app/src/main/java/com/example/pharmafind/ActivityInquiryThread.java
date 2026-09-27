package com.example.pharmafind;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_inquiry_thread);

        sqlHelper = new conSQL();
        sessionManager = new UserSessionManager(this);
        replyList = new ArrayList<>();

        View toolbar = findViewById(R.id.toolbarThread);
        cardPharmacyReplyComposer = findViewById(R.id.cardPharmacyReplyComposer);
        View composerContainer = findViewById(R.id.llComposerContainer);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            if (toolbar != null) {
                toolbar.setPadding(
                    toolbar.getPaddingLeft(),
                    statusBars.top,
                    toolbar.getPaddingRight(),
                    toolbar.getPaddingBottom()
                );
            }
            if (composerContainer != null) {
                int bottomInset = Math.max(navBars.bottom, ime.bottom);
                composerContainer.setPadding(
                    composerContainer.getPaddingLeft(),
                    composerContainer.getPaddingTop(),
                    composerContainer.getPaddingRight(),
                    bottomInset + 16
                );
            }
            return insets;
        });

        ensurePharmacyRepliesColumnsExist();

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

            @Override
            public void onReplyTextClick(PharmacyReplyModel reply) {
                focusReplyAndOpenKeyboard(etReplyPrice);
            }
        });
        rvReplies.setAdapter(replyAdapter);

        etReplyPrice = findViewById(R.id.etReplyPrice);
        etReplyNote = findViewById(R.id.etReplyNote);
        btnSubmitPharmacyReply = findViewById(R.id.btnSubmitPharmacyReply);

        if ("pharmacy".equalsIgnoreCase(sessionManager.getRole())) {
            cardPharmacyReplyComposer.setVisibility(View.VISIBLE);
            btnSubmitPharmacyReply.setOnClickListener(v -> submitPharmacyReply());

            // Make clicking details/text focus the reply composer and open digital keyboard
            View.OnClickListener openKeyboardListener = v -> focusReplyAndOpenKeyboard(etReplyPrice);
            tvMedicineName.setOnClickListener(openKeyboardListener);
            tvDescription.setOnClickListener(openKeyboardListener);
            if (cardPharmacyReplyComposer != null) cardPharmacyReplyComposer.setOnClickListener(openKeyboardListener);
            if (etReplyPrice != null) etReplyPrice.setOnClickListener(openKeyboardListener);
            if (etReplyNote != null) etReplyNote.setOnClickListener(v -> focusReplyAndOpenKeyboard(etReplyNote));
        } else {
            cardPharmacyReplyComposer.setVisibility(View.GONE);
        }

        loadInquiryDetails();
        loadPharmacyReplies();
    }

    private void focusReplyAndOpenKeyboard(EditText field) {
        if ("pharmacy".equalsIgnoreCase(sessionManager.getRole())) {
            if (cardPharmacyReplyComposer != null) {
                cardPharmacyReplyComposer.setVisibility(View.VISIBLE);
            }
            final EditText target = (field != null) ? field : etReplyPrice;
            if (target != null) {
                target.requestFocus();
                target.postDelayed(() -> {
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(target, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                    }
                }, 100);
            }
        }
    }

    private void loadInquiryDetails() {
        executor.execute(() -> {
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

                    Bitmap decodedBitmap = null;
                    if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                        try {
                            String imageStr = imageUrl;
                            if (imageStr.contains(",")) imageStr = imageStr.split(",")[1];
                            byte[] decodedBytes = Base64.decode(imageStr, Base64.DEFAULT);
                            decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                        } catch (Exception ignored) {}
                    }

                    Bitmap finalBitmap = decodedBitmap;
                    mainHandler.post(() -> {
                        tvCustomerName.setText(customerName != null ? customerName : "Anonymous");
                        tvLocationAndDate.setText("Barangay " + (barangay != null ? barangay : "") + " • " + (createdAt != null ? createdAt : ""));
                        tvMedicineName.setText("Need: " + (medicineName != null ? medicineName : ""));
                        tvDescription.setText(description != null ? description : "");
                        tvStatusBadge.setText(status != null ? status.toUpperCase() : "OPEN");

                        if (finalBitmap != null) {
                            ivInquiryImage.setImageBitmap(finalBitmap);
                            ivInquiryImage.setVisibility(View.VISIBLE);
                        } else {
                            ivInquiryImage.setVisibility(View.GONE);
                        }
                    });
                }

                con.close();
            } catch (SQLException e) {
                Log.e("ActivityInquiryThread", "Error loading inquiry details: " + e.getMessage());
            }
        });
    }

    private void loadPharmacyReplies() {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) return;

            try {
                List<PharmacyReplyModel> tempReplies = new ArrayList<>();
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

                    tempReplies.add(new PharmacyReplyModel(
                            replyId, inquiryId, pharmacyId, pharmacyName, availStatus, price, note, createdAt, city, barangay, contact
                    ));
                }

                con.close();

                mainHandler.post(() -> {
                    replyList.clear();
                    replyList.addAll(tempReplies);
                    tvRepliesHeader.setText("💬 Pharmacy Responses (" + replyList.size() + ")");
                    replyAdapter.notifyDataSetChanged();
                });

            } catch (SQLException e) {
                Log.e("ActivityInquiryThread", "Error loading pharmacy replies: " + e.getMessage());
            }
        });
    }

    private void markInquiryFulfilled(PharmacyReplyModel reply) {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) return;

            try {
                String query = "UPDATE dbo.Inquiries SET Status = 'fulfilled' WHERE InquiryID = ?";
                PreparedStatement stmt = con.prepareStatement(query);
                stmt.setInt(1, inquiryId);
                int rows = stmt.executeUpdate();
                con.close();

                mainHandler.post(() -> {
                    if (rows > 0) {
                        Toast.makeText(ActivityInquiryThread.this, "Pharmacy Selected! Opening Store Location...", Toast.LENGTH_LONG).show();
                        if (tvStatusBadge != null) tvStatusBadge.setText("FULFILLED");

                        Intent intent = new Intent(ActivityInquiryThread.this, ActivityPharmacyProfile.class);
                        intent.putExtra("pharmacy_id", reply.getPharmacyId());
                        startActivity(intent);
                    }
                });

            } catch (SQLException e) {
                Log.e("ActivityInquiryThread", "Error fulfilling inquiry: " + e.getMessage());
            }
        });
    }

    private void ensurePharmacyRepliesColumnsExist() {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) return;

            try {
                Statement stmt = con.createStatement();
                String createTableQuery = "IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[PharmacyReplies]') AND type in (N'U')) " +
                        "BEGIN " +
                        "CREATE TABLE dbo.PharmacyReplies ( " +
                        "ReplyID INT IDENTITY(1,1) PRIMARY KEY, " +
                        "InquiryID INT NOT NULL, " +
                        "PharmacyID INT NOT NULL, " +
                        "PharmacyName NVARCHAR(100) NULL, " +
                        "AvailabilityStatus NVARCHAR(50) NULL, " +
                        "Price NVARCHAR(50) NULL, " +
                        "Note NVARCHAR(MAX) NULL, " +
                        "CreatedAt DATETIME DEFAULT GETDATE() " +
                        ") " +
                        "END";
                stmt.executeUpdate(createTableQuery);

                String alterColQuery = "IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'PharmacyReplies' AND COLUMN_NAME = 'PharmacyName') " +
                        "BEGIN " +
                        "ALTER TABLE dbo.PharmacyReplies ADD PharmacyName NVARCHAR(100) NULL " +
                        "END";
                stmt.executeUpdate(alterColQuery);

                con.close();
            } catch (SQLException e) {
                Log.e("ActivityInquiryThread", "Error checking PharmacyReplies table columns: " + e.getMessage());
            }
        });
    }

    private void submitPharmacyReply() {
        String price = etReplyPrice.getText().toString().trim();
        String note = etReplyNote.getText().toString().trim();

        com.google.android.material.chip.ChipGroup chipGroupAvailability = findViewById(R.id.chipGroupAvailability);
        String selectedAvailability = "In Stock";
        if (chipGroupAvailability != null) {
            int checkedId = chipGroupAvailability.getCheckedChipId();
            if (checkedId == R.id.chipAvailable) {
                selectedAvailability = "Available";
            } else if (checkedId == R.id.chipLimitedStock) {
                selectedAvailability = "Limited Stock";
            }
        }
        final String availabilityStatus = selectedAvailability;

        if (price.isEmpty() && note.isEmpty()) {
            Toast.makeText(this, "Please enter price or note", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmitPharmacyReply.setEnabled(false);

        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) {
                mainHandler.post(() -> {
                    btnSubmitPharmacyReply.setEnabled(true);
                    Toast.makeText(ActivityInquiryThread.this, "Database connection error", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            int rows = 0;
            // Strategy 1: Insert with PharmacyName and AvailabilityStatus
            try {
                String query = "INSERT INTO dbo.PharmacyReplies (InquiryID, PharmacyID, PharmacyName, AvailabilityStatus, Price, Note) VALUES (?, ?, ?, ?, ?, ?)";
                PreparedStatement stmt = con.prepareStatement(query);
                stmt.setInt(1, inquiryId);
                stmt.setInt(2, sessionManager.getUserId());
                stmt.setString(3, sessionManager.getFullName());
                stmt.setString(4, availabilityStatus);
                stmt.setString(5, price);
                stmt.setString(6, note);
                rows = stmt.executeUpdate();
                stmt.close();
            } catch (SQLException e1) {
                Log.w("ActivityInquiryThread", "Strategy 1 insert failed: " + e1.getMessage());
                // Strategy 2: Fallback without PharmacyName
                try {
                    String query2 = "INSERT INTO dbo.PharmacyReplies (InquiryID, PharmacyID, AvailabilityStatus, Price, Note) VALUES (?, ?, ?, ?, ?)";
                    PreparedStatement stmt = con.prepareStatement(query2);
                    stmt.setInt(1, inquiryId);
                    stmt.setInt(2, sessionManager.getUserId());
                    stmt.setString(3, availabilityStatus);
                    stmt.setString(4, price);
                    stmt.setString(5, note);
                    rows = stmt.executeUpdate();
                    stmt.close();
                } catch (SQLException e2) {
                    Log.w("ActivityInquiryThread", "Strategy 2 insert failed: " + e2.getMessage());
                    // Strategy 3: Minimal insert (InquiryID, PharmacyID, Price, Note)
                    try {
                        String query3 = "INSERT INTO dbo.PharmacyReplies (InquiryID, PharmacyID, Price, Note) VALUES (?, ?, ?, ?)";
                        PreparedStatement stmt = con.prepareStatement(query3);
                        stmt.setInt(1, inquiryId);
                        stmt.setInt(2, sessionManager.getUserId());
                        stmt.setString(3, price);
                        stmt.setString(4, note);
                        rows = stmt.executeUpdate();
                        stmt.close();
                    } catch (SQLException e3) {
                        Log.e("ActivityInquiryThread", "Strategy 3 insert failed: " + e3.getMessage());
                    }
                }
            }

            try {
                con.close();
            } catch (SQLException ignored) {}

            final int affectedRows = rows;
            mainHandler.post(() -> {
                btnSubmitPharmacyReply.setEnabled(true);
                if (affectedRows > 0) {
                    Toast.makeText(ActivityInquiryThread.this, "Response posted!", Toast.LENGTH_SHORT).show();
                    etReplyPrice.setText("");
                    etReplyNote.setText("");
                    loadPharmacyReplies();
                } else {
                    Toast.makeText(ActivityInquiryThread.this, "Failed to post response. Please check database constraint.", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
