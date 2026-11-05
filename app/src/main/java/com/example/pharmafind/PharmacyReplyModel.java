package com.example.pharmafind;

public class PharmacyReplyModel {

    private int replyId;
    private int inquiryId;
    private int pharmacyId;
    private String pharmacyName;
    private String availabilityStatus;
    private String price;
    private String note;
    private String createdAt;
    private String pharmacyCity;
    private String pharmacyBarangay;
    private String pharmacyContact;
    private String pharmacyStatus;
    private double latitude;
    private double longitude;
    private double distanceKm = -1.0;

    public PharmacyReplyModel(int replyId, int inquiryId, int pharmacyId, String pharmacyName, String availabilityStatus, String price, String note, String createdAt, String pharmacyCity, String pharmacyBarangay, String pharmacyContact, String pharmacyStatus) {
        this(replyId, inquiryId, pharmacyId, pharmacyName, availabilityStatus, price, note, createdAt, pharmacyCity, pharmacyBarangay, pharmacyContact, pharmacyStatus, 0.0, 0.0);
    }

    public PharmacyReplyModel(int replyId, int inquiryId, int pharmacyId, String pharmacyName, String availabilityStatus, String price, String note, String createdAt, String pharmacyCity, String pharmacyBarangay, String pharmacyContact, String pharmacyStatus, double latitude, double longitude) {
        this.replyId = replyId;
        this.inquiryId = inquiryId;
        this.pharmacyId = pharmacyId;
        this.pharmacyName = pharmacyName;
        this.availabilityStatus = availabilityStatus;
        this.price = price;
        this.note = note;
        this.createdAt = createdAt;
        this.pharmacyCity = pharmacyCity;
        this.pharmacyBarangay = pharmacyBarangay;
        this.pharmacyContact = pharmacyContact;
        this.pharmacyStatus = pharmacyStatus;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public int getReplyId() { return replyId; }
    public int getInquiryId() { return inquiryId; }
    public int getPharmacyId() { return pharmacyId; }
    public String getPharmacyName() { return pharmacyName; }
    public String getAvailabilityStatus() { return availabilityStatus; }
    public String getPrice() { return price; }
    public String getNote() { return note; }
    public String getCreatedAt() { return createdAt; }
    public String getPharmacyCity() { return pharmacyCity; }
    public String getPharmacyBarangay() { return pharmacyBarangay; }
    public String getPharmacyContact() { return pharmacyContact; }
    public String getPharmacyStatus() { return pharmacyStatus; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }
}
