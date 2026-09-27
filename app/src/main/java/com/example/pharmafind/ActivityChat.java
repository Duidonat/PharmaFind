package com.example.pharmafind;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        TextView tvChatPartnerName = findViewById(R.id.tvChatPartnerName);
        tvChatPartnerName.setText(receiverName);

        rvMessages = findViewById(R.id.rvMessages);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);

        messageAdapter = new MessageAdapter(messageList, sessionManager.getUserId());
        rvMessages.setAdapter(messageAdapter);

        etChatMessageInput = findViewById(R.id.etChatMessageInput);
        MaterialButton btnSendMessage = findViewById(R.id.btnSendMessage);

        btnSendMessage.setOnClickListener(v -> {
            String text = etChatMessageInput.getText().toString().trim();
            if (text.isEmpty()) return;

            sendMessageToDB(text);
        });

        ensureMessagesTableExists();
        loadMessagesFromDB();
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
                String query = "SELECT MessageID, InquiryID, SenderID, SenderRole, SenderName, ReceiverID, MessageText, CONVERT(VARCHAR, CreatedAt, 108) AS CreatedAtStr " +
                        "FROM dbo.Messages " +
                        "WHERE InquiryID = ? " +
                        "ORDER BY CreatedAt ASC";

                PreparedStatement stmt = con.prepareStatement(query);
                stmt.setInt(1, inquiryId);
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
                Log.e("ActivityChat", "Error loading messages: " + e.getMessage());
            }
        });
    }

    private void sendMessageToDB(String messageText) {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) {
                mainHandler.post(() -> Toast.makeText(ActivityChat.this, "Database connection error", Toast.LENGTH_SHORT).show());
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
