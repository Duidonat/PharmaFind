package com.example.pharmafind;

public class PharmacyReplyModel {

    private int replyId;
    private int inquiryId;
    private int pharmacyId;
    private String pharmacyName;
    private String availabilityStatus; // 'Available', 'Limited Stock', 'Out of Stock'
    private String price;
    private String note;
    private String createdAt;
    private String pharmacyCity;
    private String pharmacyBarangay;
    private String pharmacyContact;

    public PharmacyReplyModel(int replyId, int inquiryId, int pharmacyId, String pharmacyName, String availabilityStatus, String price, String note, String createdAt, String pharmacyCity, String pharmacyBarangay, String pharmacyContact) {
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
}
