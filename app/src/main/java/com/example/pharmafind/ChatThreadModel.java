package com.example.pharmafind;

public class ChatThreadModel {

    private int inquiryId;
    private int partnerId;
    private String partnerName;
    private String medicineName;
    private String lastMessage;
    private String lastMessageTime;

    public ChatThreadModel(int inquiryId, int partnerId, String partnerName, String medicineName, String lastMessage, String lastMessageTime) {
        this.inquiryId = inquiryId;
        this.partnerId = partnerId;
        this.partnerName = partnerName;
        this.medicineName = medicineName;
        this.lastMessage = lastMessage;
        this.lastMessageTime = lastMessageTime;
    }

    public int getInquiryId() { return inquiryId; }
    public int getPartnerId() { return partnerId; }
    public String getPartnerName() { return partnerName; }
    public String getMedicineName() { return medicineName; }
    public String getLastMessage() { return lastMessage; }
    public String getLastMessageTime() { return lastMessageTime; }
}
