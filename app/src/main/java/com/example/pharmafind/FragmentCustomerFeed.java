package com.example.pharmafind;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FragmentCustomerFeed extends Fragment {

    private conSQL sqlHelper;
    private UserSessionManager sessionManager;
    private InquiryAdapter inquiryAdapter;
    private List<InquiryModel> allInquiriesList;
    private List<InquiryModel> displayedInquiriesList;

    private EditText etSearchFeed;
    private ChipGroup chipGroupFilter;
    private String currentSearchQuery = "";
    private String currentStatusFilter = "all"; // 'all', 'open', 'fulfilled'

    private ProgressBar pbFeedLoading;
    private TextView tvFeedEmptyState;

    private ActivityResultLauncher<String> imagePickerLauncher;
    private ImageView ivAttachedPhotoDialog;
    private FrameLayout photoPreviewContainerDialog;
    private String selectedBase64Image = "";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sqlHelper = new conSQL();
        sessionManager = new UserSessionManager(requireContext());
        allInquiriesList = new ArrayList<>();
        displayedInquiriesList = new ArrayList<>();

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        processSelectedImage(uri);
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_customer_feed, container, false);

        etSearchFeed = view.findViewById(R.id.etSearchFeed);
        chipGroupFilter = view.findViewById(R.id.chipGroupFilter);
        pbFeedLoading = view.findViewById(R.id.pbFeedLoading);
        tvFeedEmptyState = view.findViewById(R.id.tvFeedEmptyState);

        RecyclerView rvInquiries = view.findViewById(R.id.rvInquiries);
        rvInquiries.setLayoutManager(new LinearLayoutManager(getContext()));
        inquiryAdapter = new InquiryAdapter(displayedInquiriesList, item -> {
            Intent intent = new Intent(getContext(), ActivityInquiryThread.class);
            intent.putExtra("inquiry_id", item.getInquiryId());
            startActivity(intent);
        });
        rvInquiries.setAdapter(inquiryAdapter);

        FloatingActionButton fabPostInquiry = view.findViewById(R.id.fabPostInquiry);
        if (fabPostInquiry != null) {
            fabPostInquiry.setOnClickListener(v -> showCreateInquiryDialog());
        }

        // Search text watcher
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

        // Status Filter Chips
        if (chipGroupFilter != null) {
            chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.contains(R.id.chipOpen)) {
                    currentStatusFilter = "open";
                } else if (checkedIds.contains(R.id.chipFulfilled)) {
                    currentStatusFilter = "fulfilled";
                } else {
                    currentStatusFilter = "all";
                }
                filterInquiries();
            });
        }

        ensureTablesExist();
        loadInquiriesFromDB();

        return view;
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

        if (tvFeedEmptyState != null) {
            if (displayedInquiriesList.isEmpty()) {
                tvFeedEmptyState.setText("No requests found.");
                tvFeedEmptyState.setVisibility(View.VISIBLE);
            } else {
                tvFeedEmptyState.setVisibility(View.GONE);
            }
        }
    }

    private void showFeedError(String message) {
        if (pbFeedLoading != null) pbFeedLoading.setVisibility(View.GONE);
        if (tvFeedEmptyState != null) {
            tvFeedEmptyState.setText(message);
            tvFeedEmptyState.setVisibility(View.VISIBLE);
        }
    }

    private void ensureTablesExistInternal(Connection con) {
        if (con == null) return;
        try {
            Statement stmt = con.createStatement();

            // 1. Create Inquiries table if it doesn't exist at all
            String createInquiriesTable = "IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[Inquiries]') AND type in (N'U')) " +
                    "BEGIN " +
                    "CREATE TABLE dbo.Inquiries ( " +
                    "InquiryID INT IDENTITY(1,1) PRIMARY KEY, " +
                    "CustomerID INT NOT NULL, " +
                    "CustomerName NVARCHAR(100), " +
                    "MedicineName NVARCHAR(255) NOT NULL, " +
                    "Description NVARCHAR(MAX), " +
                    "ImageUrl NVARCHAR(MAX), " +
                    "City NVARCHAR(100), " +
                    "Barangay NVARCHAR(100), " +
                    "District NVARCHAR(100), " +
                    "Status NVARCHAR(50) DEFAULT 'open', " +
                    "CreatedAt DATETIME DEFAULT GETDATE() " +
                    ") " +
                    "END";
            stmt.executeUpdate(createInquiriesTable);

            // 2. Add each missing column individually so syntax is valid and independent
            String[] alterCols = {
                    "IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Inquiries' AND COLUMN_NAME = 'CustomerName') ALTER TABLE dbo.Inquiries ADD CustomerName NVARCHAR(100) NULL;",
                    "IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Inquiries' AND COLUMN_NAME = 'City') ALTER TABLE dbo.Inquiries ADD City NVARCHAR(100) NULL;",
                    "IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Inquiries' AND COLUMN_NAME = 'Barangay') ALTER TABLE dbo.Inquiries ADD Barangay NVARCHAR(100) NULL;",
                    "IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Inquiries' AND COLUMN_NAME = 'District') ALTER TABLE dbo.Inquiries ADD District NVARCHAR(100) NULL;",
                    "IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Inquiries' AND COLUMN_NAME = 'ImageUrl') ALTER TABLE dbo.Inquiries ADD ImageUrl NVARCHAR(MAX) NULL;",
                    "IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Inquiries' AND COLUMN_NAME = 'Description') ALTER TABLE dbo.Inquiries ADD Description NVARCHAR(MAX) NULL;",
                    "IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Inquiries' AND COLUMN_NAME = 'Status') ALTER TABLE dbo.Inquiries ADD Status NVARCHAR(50) DEFAULT 'open';"
            };

            for (String sql : alterCols) {
                try {
                    stmt.executeUpdate(sql);
                } catch (SQLException e) {
                    Log.e("FragmentCustomerFeed", "Col alter warning: " + e.getMessage());
                }
            }

            // 3. Create PharmacyReplies table if it doesn't exist
            String createRepliesTable = "IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[PharmacyReplies]') AND type in (N'U')) " +
                    "BEGIN " +
                    "CREATE TABLE dbo.PharmacyReplies ( " +
                    "ReplyID INT IDENTITY(1,1) PRIMARY KEY, " +
                    "InquiryID INT NOT NULL, " +
                    "PharmacyID INT NOT NULL, " +
                    "PharmacyName NVARCHAR(100), " +
                    "AvailabilityStatus NVARCHAR(50), " +
                    "Price NVARCHAR(50), " +
                    "Note NVARCHAR(MAX), " +
                    "CreatedAt DATETIME DEFAULT GETDATE() " +
                    ") " +
                    "END";
            stmt.executeUpdate(createRepliesTable);

        } catch (SQLException e) {
            Log.e("FragmentCustomerFeed", "Table Creation Error: " + e.getMessage());
        }
    }

    private void ensureTablesExist() {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) return;
            ensureTablesExistInternal(con);
            try {
                con.close();
            } catch (SQLException ignored) {}
        });
    }

    private void loadInquiriesFromDB() {
        if (pbFeedLoading != null) pbFeedLoading.setVisibility(View.VISIBLE);
        if (tvFeedEmptyState != null) tvFeedEmptyState.setVisibility(View.GONE);

        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) {
                mainHandler.post(() -> showFeedError("Couldn't connect. Check your internet and try again."));
                return;
            }

            ensureTablesExistInternal(con);

            try {
                List<InquiryModel> tempInquiries = new ArrayList<>();
                String query = "SELECT i.InquiryID, i.CustomerID, i.CustomerName, i.MedicineName, i.Description, i.ImageUrl, i.City, i.Barangay, i.District, i.Status, CONVERT(VARCHAR, i.CreatedAt, 120) AS CreatedAtStr, " +
                        "(SELECT COUNT(*) FROM dbo.PharmacyReplies r WHERE r.InquiryID = i.InquiryID) AS ReplyCount " +
                        "FROM dbo.Inquiries i " +
                        "ORDER BY i.CreatedAt DESC";

                PreparedStatement stmt = con.prepareStatement(query);
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

                    tempInquiries.add(new InquiryModel(
                            inquiryId, customerId, customerName, medicineName, description, imageUrl, city, barangay, district, status, createdAt, replyCount
                    ));
                }

                con.close();

                mainHandler.post(() -> {
                    allInquiriesList.clear();
                    allInquiriesList.addAll(tempInquiries);
                    if (pbFeedLoading != null) pbFeedLoading.setVisibility(View.GONE);
                    filterInquiries();
                });

            } catch (SQLException e) {
                Log.e("FragmentCustomerFeed", "Load Inquiries Error: " + e.getMessage());
                mainHandler.post(() -> showFeedError("Couldn't load requests. Please try again."));
            }
        });
    }

    private void showCreateInquiryDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_inquiry, null);
        dialog.setContentView(dialogView);

        selectedBase64Image = "";

        ImageButton btnClose = dialogView.findViewById(R.id.btnCloseDialog);
        TextView tvAuthorName = dialogView.findViewById(R.id.tvAuthorName);
        EditText etMedicineName = dialogView.findViewById(R.id.etMedicineName);
        EditText etDescription = dialogView.findViewById(R.id.etDescription);
        MaterialButton btnAddPhoto = dialogView.findViewById(R.id.btnAddPhoto);
        MaterialButton btnPostInquiry = dialogView.findViewById(R.id.btnPostInquiry);
        ImageButton btnRemovePhoto = dialogView.findViewById(R.id.btnRemovePhoto);

        photoPreviewContainerDialog = dialogView.findViewById(R.id.photoPreviewContainer);
        ivAttachedPhotoDialog = dialogView.findViewById(R.id.ivAttachedPhoto);

        if (tvAuthorName != null) {
            tvAuthorName.setText(sessionManager.getFullName());
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnAddPhoto != null) {
            btnAddPhoto.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        }

        if (btnRemovePhoto != null) {
            btnRemovePhoto.setOnClickListener(v -> {
                selectedBase64Image = "";
                if (photoPreviewContainerDialog != null) {
                    photoPreviewContainerDialog.setVisibility(View.GONE);
                }
            });
        }

        if (btnPostInquiry != null) {
            btnPostInquiry.setOnClickListener(v -> {
                String medicineName = etMedicineName.getText().toString().trim();
                String description = etDescription.getText().toString().trim();

                if (medicineName.isEmpty()) {
                    Toast.makeText(getContext(), "Please enter the medicine name", Toast.LENGTH_SHORT).show();
                    return;
                }

                btnPostInquiry.setEnabled(false);
                postInquiryToDB(medicineName, description, selectedBase64Image, dialog, btnPostInquiry);
            });
        }

        View parent = (View) dialogView.getParent();
        if (parent != null) {
            parent.setBackgroundResource(android.R.color.transparent);
        }

        dialog.show();
    }

    private void processSelectedImage(Uri uri) {
        try {
            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) return;

            int targetWidth = 800;
            int targetHeight = (int) (800.0 * bitmap.getHeight() / bitmap.getWidth());
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            byte[] imageBytes = baos.toByteArray();

            selectedBase64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT);

            if (ivAttachedPhotoDialog != null && photoPreviewContainerDialog != null) {
                ivAttachedPhotoDialog.setImageBitmap(scaledBitmap);
                photoPreviewContainerDialog.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.e("FragmentCustomerFeed", "Image processing failed: " + e.getMessage());
            Toast.makeText(getContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
        }
    }

    private void postInquiryToDB(String medicineName, String description, String base64Image, BottomSheetDialog dialog, MaterialButton btnPost) {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) {
                mainHandler.post(() -> {
                    if (btnPost != null) btnPost.setEnabled(true);
                    Toast.makeText(getContext(), "Database Connection Failed", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            // Ensure columns exist right before executing the insert statement
            ensureTablesExistInternal(con);

            try {
                String query = "INSERT INTO dbo.Inquiries (CustomerID, CustomerName, MedicineName, Description, ImageUrl, City, Barangay, District, Status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'open')";
                PreparedStatement stmt = con.prepareStatement(query);
                stmt.setInt(1, sessionManager.getUserId());
                stmt.setString(2, sessionManager.getFullName());
                stmt.setString(3, medicineName);
                stmt.setString(4, description);
                stmt.setString(5, base64Image);
                stmt.setString(6, sessionManager.getCity());
                stmt.setString(7, sessionManager.getBarangay());
                stmt.setString(8, sessionManager.getDistrict());

                int rows = stmt.executeUpdate();
                con.close();

                mainHandler.post(() -> {
                    if (btnPost != null) btnPost.setEnabled(true);
                    if (rows > 0) {
                        Toast.makeText(getContext(), "Medicine Request Posted!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        loadInquiriesFromDB();
                    } else {
                        Toast.makeText(getContext(), "Failed to post request", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (SQLException e) {
                Log.e("FragmentCustomerFeed", "Post Inquiry Error: " + e.getMessage());
                mainHandler.post(() -> {
                    if (btnPost != null) btnPost.setEnabled(true);
                    Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
