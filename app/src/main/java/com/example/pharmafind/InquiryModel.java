package com.example.pharmafind;

public class InquiryModel {

    private int inquiryId;
    private int customerId;
    private String customerName;
    private String medicineName;
    private String description;
    private boolean hasImage;
    private String city;
    private String barangay;
    private String district;
    private String status;
    private String createdAt;
    private int replyCount;
    private double latitude;
    private double longitude;
    private double distanceKm = -1.0;

    public InquiryModel(int inquiryId, int customerId, String customerName, String medicineName, String description, boolean hasImage, String city, String barangay, String district, String status, String createdAt, int replyCount) {
        this(inquiryId, customerId, customerName, medicineName, description, hasImage, city, barangay, district, status, createdAt, replyCount, 0.0, 0.0);
    }

    public InquiryModel(int inquiryId, int customerId, String customerName, String medicineName, String description, boolean hasImage, String city, String barangay, String district, String status, String createdAt, int replyCount, double latitude, double longitude) {
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
        this.latitude = latitude;
        this.longitude = longitude;
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
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }
}
