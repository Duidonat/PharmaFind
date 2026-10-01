package com.example.pharmafind;

public class InquiryModel {

    private int inquiryId;
    private int customerId;
    private String customerName;
    private String medicineName;
    private String description;
    private boolean hasImage; // true if a photo is attached; the photo itself is only loaded on the detail screen
    private String city;
    private String barangay;
    private String district;
    private String status; // 'open', 'fulfilled', 'expired'
    private String createdAt;
    private int replyCount;

    public InquiryModel(int inquiryId, int customerId, String customerName, String medicineName, String description, boolean hasImage, String city, String barangay, String district, String status, String createdAt, int replyCount) {
        this.inquiryId = inquiryId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.medicineName = medicineName;
        this.description = description;
        this.hasImage = hasImage;
        this.city = city;
        this.barangay = barangay;
        this.district = district;
        this.status = status;
        this.createdAt = createdAt;
        this.replyCount = replyCount;
    }

    public int getInquiryId() { return inquiryId; }
    public int getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public String getMedicineName() { return medicineName; }
    public String getDescription() { return description; }
    public boolean hasImage() { return hasImage; }
    public String getCity() { return city; }
    public String getBarangay() { return barangay; }
    public String getDistrict() { return district; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public int getReplyCount() { return replyCount; }
}
