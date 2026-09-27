package com.example.pharmafind;

public class MessageModel {

    private int messageId;
    private int inquiryId;
    private int senderId;
    private String senderRole; // "customer" or "pharmacy"
    private String senderName;
    private int receiverId;
    private String messageText;
    private String createdAt;

    public MessageModel(int messageId, int inquiryId, int senderId, String senderRole, String senderName, int receiverId, String messageText, String createdAt) {
        this.messageId = messageId;
        this.inquiryId = inquiryId;
        this.senderId = senderId;
        this.senderRole = senderRole;
        this.senderName = senderName;
        this.receiverId = receiverId;
        this.messageText = messageText;
        this.createdAt = createdAt;
    }

    public int getMessageId() { return messageId; }
    public int getInquiryId() { return inquiryId; }
    public int getSenderId() { return senderId; }
    public String getSenderRole() { return senderRole; }
    public String getSenderName() { return senderName; }
    public int getReceiverId() { return receiverId; }
    public String getMessageText() { return messageText; }
    public String getCreatedAt() { return createdAt; }
}
