package com.example.pharmafind;

import android.content.Context;
import android.content.SharedPreferences;

public class UserSessionManager {

    private static final String PREF_NAME = "PharmaFindSession";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_FULL_NAME = "fullName";
    private static final String KEY_ROLE = "role"; // "customer" or "pharmacy"
    private static final String KEY_CITY = "city";
    private static final String KEY_BARANGAY = "barangay";
    private static final String KEY_DISTRICT = "district";
    private static final String KEY_STATUS = "status"; // for pharmacy 'pending'/'approved'

    private final SharedPreferences pref;
    private final SharedPreferences.Editor editor;

    public UserSessionManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    public void createCustomerSession(int userId, String username, String fullName, String city, String barangay, String district) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putInt(KEY_USER_ID, userId);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_FULL_NAME, fullName);
        editor.putString(KEY_ROLE, "customer");
        editor.putString(KEY_CITY, city);
        editor.putString(KEY_BARANGAY, barangay);
        editor.putString(KEY_DISTRICT, district);
        editor.apply();
    }

    public void createPharmacySession(int userId, String username, String pharmacyName, String city, String barangay, String district, String status) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putInt(KEY_USER_ID, userId);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_FULL_NAME, pharmacyName);
        editor.putString(KEY_ROLE, "pharmacy");
        editor.putString(KEY_CITY, city);
        editor.putString(KEY_BARANGAY, barangay);
        editor.putString(KEY_DISTRICT, district);
        editor.putString(KEY_STATUS, status);
        editor.apply();
    }

    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public int getUserId() {
        return pref.getInt(KEY_USER_ID, -1);
    }

    public String getUsername() {
        return pref.getString(KEY_USERNAME, "");
    }

    public String getFullName() {
        return pref.getString(KEY_FULL_NAME, "");
    }

    public String getRole() {
        return pref.getString(KEY_ROLE, "");
    }

    public String getCity() {
        return pref.getString(KEY_CITY, "");
    }

    public String getBarangay() {
        return pref.getString(KEY_BARANGAY, "");
    }

    public String getDistrict() {
        return pref.getString(KEY_DISTRICT, "");
    }

    public String getStatus() {
        return pref.getString(KEY_STATUS, "approved");
    }

    public void logoutUser() {
        editor.clear();
        editor.apply();
    }
}
