package com.example.pharmafind;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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

public class ActivityChat extends AppCompatActivity {

    private conSQL sqlHelper;
    private UserSessionManager sessionManager;

    private int inquiryId = -1;
    private int receiverId = -1;
    private String receiverName = "";

    private RecyclerView rvMessages;
    private MessageAdapter messageAdapter;
    private List<MessageModel> messageList;
    private EditText etChatMessageInput;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isPolling = false;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPolling) {
                loadMessagesFromDBQuietly();
                mainHandler.postDelayed(this, 3000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_chat);

        sqlHelper = new conSQL();
        sessionManager = new UserSessionManager(this);
        messageList = new ArrayList<>();

        inquiryId = getIntent().getIntExtra("inquiry_id", -1);
        receiverId = getIntent().getIntExtra("receiver_id", -1);
        receiverName = getIntent().getStringExtra("receiver_name");

        if (receiverName == null) receiverName = "User";

        ImageButton btnBack = findViewById(R.id.btnBackChat);
        btnBack.setOnClickListener(v -> finish());

        View toolbar = findViewById(R.id.toolbarChat);
        View chatInputContainer = findViewById(R.id.llChatInputContainer);

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
            if (chatInputContainer != null) {
                int base12Px = (int) (12 * getResources().getDisplayMetrics().density);
                int bottomInset = Math.max(navBars.bottom, ime.bottom);
                chatInputContainer.setPadding(
                    chatInputContainer.getPaddingLeft(),
                    base12Px,
                    chatInputContainer.getPaddingRight(),
                    base12Px + bottomInset
                );
            }
            return insets;
        });

        TextView tvChatPartnerName = findViewById(R.id.tvChatPartnerName);
        tvChatPartnerName.setText(receiverName);

        rvMessages = findViewById(R.id.rvMessages);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);

        messageAdapter = new MessageAdapter(messageList, sessionManager.getUserId(), sessionManager.getRole());
        messageAdapter.setOnSatisfactionListener(message -> handleCustomerSatisfiedCare());
        rvMessages.setAdapter(messageAdapter);

        MaterialButton btnAskSatisfaction = findViewById(R.id.btnAskSatisfaction);
        if ("pharmacy".equalsIgnoreCase(sessionManager.getRole())) {
            if (btnAskSatisfaction != null) {
                btnAskSatisfaction.setVisibility(View.VISIBLE);
                btnAskSatisfaction.setOnClickListener(v -> sendMessageToDB("Are you satisfied with my care?"));
            }
        } else {
            if (btnAskSatisfaction != null) {
                btnAskSatisfaction.setVisibility(View.GONE);
            }
        }

        etChatMessageInput = findViewById(R.id.etChatMessageInput);
        MaterialButton btnSendMessage = findViewById(R.id.btnSendMessage);

        btnSendMessage.setOnClickListener(v -> {
            String text = etChatMessageInput.getText().toString().trim();
            if (text.isEmpty()) return;

            sendMessageToDB(text);
        });

        setupQuickReplyChips();
        ensureMessagesTableExists();
        loadMessagesFromDB();
    }

    private void setupQuickReplyChips() {
        com.google.android.material.chip.ChipGroup chipGroup = findViewById(R.id.chipGroupQuickReplies);
        if (chipGroup == null) return;

        chipGroup.removeAllViews();
        String[] replies;

        if ("pharmacy".equalsIgnoreCase(sessionManager.getRole())) {
            replies = new String[]{
                    "💊 In Stock Now",
                    "💰 Special Price Available",
                    "🕗 Open until 9 PM",
                    "📋 Prescription Required",
                    "🚗 Ready for Pickup"
            };
        } else {
            replies = new String[]{
                    "🚀 Is this medicine available?",
                    "📍 Can I pick it up now?",
                    "❓ Do I need a prescription?",
                    "💵 How much per tablet/box?",
                    "🙏 Thank you very much!"
            };
        }

        for (String text : replies) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
            chip.setText(text);
            chip.setCheckable(false);
            chip.setClickable(true);
            chip.setChipBackgroundColorResource(R.color.teal_container);
            chip.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.primary_dark));
            chip.setOnClickListener(v -> sendMessageToDB(text));
            chipGroup.addView(chip);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isPolling = true;
        mainHandler.postDelayed(pollRunnable, 3000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isPolling = false;
        mainHandler.removeCallbacks(pollRunnable);
    }

    private void handleCustomerSatisfiedCare() {
        UiMessage.show(this, "I am satisfied with my care. Closing chat & clearing storage...");
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) return;

            try {
                String deleteQuery = "DELETE FROM dbo.Messages WHERE InquiryID = ?";
                PreparedStatement stmt = con.prepareStatement(deleteQuery);
                stmt.setInt(1, inquiryId);
                stmt.executeUpdate();

                String fulfillQuery = "UPDATE dbo.Inquiries SET Status = 'fulfilled' WHERE InquiryID = ?";
                PreparedStatement fulfillStmt = con.prepareStatement(fulfillQuery);
                fulfillStmt.setInt(1, inquiryId);
                fulfillStmt.executeUpdate();

                con.close();
            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
                Log.e("ActivityChat", "Error clearing chat storage: " + e.getMessage());
            }

            mainHandler.postDelayed(this::finish, 1200);
        });
    }

    private void ensureMessagesTableExists() {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) return;

            try {
                Statement stmt = con.createStatement();
                String query = "IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[Messages]') AND type in (N'U')) " +
                        "BEGIN " +
                        "CREATE TABLE dbo.Messages ( " +
                        "MessageID INT IDENTITY(1,1) PRIMARY KEY, " +
                        "InquiryID INT NOT NULL, " +
                        "SenderID INT NOT NULL, " +
                        "SenderRole NVARCHAR(20) NOT NULL, " +
                        "SenderName NVARCHAR(100) NOT NULL, " +
                        "ReceiverID INT NOT NULL, " +
                        "MessageText NVARCHAR(MAX) NOT NULL, " +
                        "CreatedAt DATETIME DEFAULT GETDATE() " +
                        ") " +
                        "END";
                stmt.executeUpdate(query);
                con.close();
            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
                Log.e("ActivityChat", "Error creating Messages table: " + e.getMessage());
            }
        });
    }

    private void loadMessagesFromDB() {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) return;

            try {
                List<MessageModel> tempMessages = new ArrayList<>();
                int currentUserId = sessionManager.getUserId();
                String currentUserRole = sessionManager.getRole();

                // Enforce participant security: Only load messages sent by or directed to the logged-in user
                String query = "SELECT MessageID, InquiryID, SenderID, SenderRole, SenderName, ReceiverID, MessageText, CONVERT(VARCHAR, CreatedAt, 108) AS CreatedAtStr " +
                        "FROM dbo.Messages " +
                        "WHERE InquiryID = ? " +
                        "  AND ((SenderID = ? AND LOWER(SenderRole) = LOWER(?)) OR (ReceiverID = ? AND LOWER(SenderRole) <> LOWER(?))) " +
                        "ORDER BY CreatedAt ASC";

                PreparedStatement stmt = con.prepareStatement(query);
                stmt.setInt(1, inquiryId);
                stmt.setInt(2, currentUserId);
                stmt.setString(3, currentUserRole != null ? currentUserRole : "customer");
                stmt.setInt(4, currentUserId);
                stmt.setString(5, currentUserRole != null ? currentUserRole : "customer");
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    int msgId = rs.getInt("MessageID");
                    int inqId = rs.getInt("InquiryID");
                    int senderId = rs.getInt("SenderID");
                    String senderRole = rs.getString("SenderRole");
                    String senderName = rs.getString("SenderName");
                    int recId = rs.getInt("ReceiverID");
                    String text = rs.getString("MessageText");
                    String createdAt = rs.getString("CreatedAtStr");

                    tempMessages.add(new MessageModel(
                            msgId, inqId, senderId, senderRole, senderName, recId, text, createdAt
                    ));
                }

                con.close();

                mainHandler.post(() -> {
                    messageList.clear();
                    messageList.addAll(tempMessages);
                    messageAdapter.notifyDataSetChanged();
                    if (!messageList.isEmpty()) {
                        rvMessages.smoothScrollToPosition(messageList.size() - 1);
                    }
                });

            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
                Log.e("ActivityChat", "Error loading messages: " + e.getMessage());
            }
        });
    }

    private void loadMessagesFromDBQuietly() {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) return;

            try {
                List<MessageModel> tempMessages = new ArrayList<>();
                int currentUserId = sessionManager.getUserId();
                String currentUserRole = sessionManager.getRole();

                String query = "SELECT MessageID, InquiryID, SenderID, SenderRole, SenderName, ReceiverID, MessageText, CONVERT(VARCHAR, CreatedAt, 108) AS CreatedAtStr " +
                        "FROM dbo.Messages " +
                        "WHERE InquiryID = ? " +
                        "  AND ((SenderID = ? AND LOWER(SenderRole) = LOWER(?)) OR (ReceiverID = ? AND LOWER(SenderRole) <> LOWER(?))) " +
                        "ORDER BY CreatedAt ASC";

                PreparedStatement stmt = con.prepareStatement(query);
                stmt.setInt(1, inquiryId);
                stmt.setInt(2, currentUserId);
                stmt.setString(3, currentUserRole != null ? currentUserRole : "customer");
                stmt.setInt(4, currentUserId);
                stmt.setString(5, currentUserRole != null ? currentUserRole : "customer");
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    int msgId = rs.getInt("MessageID");
                    int inqId = rs.getInt("InquiryID");
                    int senderId = rs.getInt("SenderID");
                    String senderRole = rs.getString("SenderRole");
                    String senderName = rs.getString("SenderName");
                    int recId = rs.getInt("ReceiverID");
                    String text = rs.getString("MessageText");
                    String createdAt = rs.getString("CreatedAtStr");

                    tempMessages.add(new MessageModel(
                            msgId, inqId, senderId, senderRole, senderName, recId, text, createdAt
                    ));
                }

                con.close();

                mainHandler.post(() -> {
                    if (tempMessages.size() > messageList.size()) {
                        MessageModel latest = tempMessages.get(tempMessages.size() - 1);
                        boolean isMine = (latest.getSenderId() == sessionManager.getUserId())
                                && (latest.getSenderRole() != null && latest.getSenderRole().equalsIgnoreCase(sessionManager.getRole()));
                        if (!isMine && !messageList.isEmpty()) {
                            UiMessage.show(ActivityChat.this, "📩 New message from " + latest.getSenderName());
                        }
                        messageList.clear();
                        messageList.addAll(tempMessages);
                        messageAdapter.notifyDataSetChanged();
                        if (!messageList.isEmpty()) {
                            rvMessages.smoothScrollToPosition(messageList.size() - 1);
                        }
                    }
                });

            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
            }
        });
    }

    private void sendMessageToDB(String messageText) {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) {
                mainHandler.post(() -> UiMessage.show(ActivityChat.this, "Database connection error"));
                return;
            }

            try {
                String query = "INSERT INTO dbo.Messages (InquiryID, SenderID, SenderRole, SenderName, ReceiverID, MessageText) VALUES (?, ?, ?, ?, ?, ?)";
                PreparedStatement stmt = con.prepareStatement(query);
                stmt.setInt(1, inquiryId);
                stmt.setInt(2, sessionManager.getUserId());
                stmt.setString(3, sessionManager.getRole());
                stmt.setString(4, sessionManager.getFullName());
                stmt.setInt(5, receiverId);
                stmt.setString(6, messageText);

                int rows = stmt.executeUpdate();
                con.close();

                mainHandler.post(() -> {
                    if (rows > 0) {
                        etChatMessageInput.setText("");
                        loadMessagesFromDB();
                    }
                });

            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
                Log.e("ActivityChat", "Error sending message: " + e.getMessage());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
