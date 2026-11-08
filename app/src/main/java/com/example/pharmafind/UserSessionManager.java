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
    private static final String KEY_LATITUDE = "latitude";
    private static final String KEY_LONGITUDE = "longitude";
    private static final String KEY_REMEMBER_ME = "rememberMe";
    private static final String KEY_PROXIMITY_RADIUS_KM = "proximity_radius_km";
    private static final String KEY_AVATAR = "avatar";

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

    public void setLocationCoordinates(double lat, double lng) {
        editor.putFloat(KEY_LATITUDE, (float) lat);
        editor.putFloat(KEY_LONGITUDE, (float) lng);
        editor.apply();
    }

    public double getLatitude() {
        return pref.getFloat(KEY_LATITUDE, 0.0f);
    }

    public double getLongitude() {
        return pref.getFloat(KEY_LONGITUDE, 0.0f);
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

    public void setStatus(String status) {
        editor.putString(KEY_STATUS, status);
        editor.apply();
    }

    /**
     * Configured proximity radius in kilometers for nearby request discovery.
     * Default is 5 km. Valid range is 1-50 km.
     */
    public double getProximityRadiusKm() {
        return pref.getFloat(KEY_PROXIMITY_RADIUS_KM, 5.0f);
    }

    public void setProximityRadiusKm(double radiusKm) {
        editor.putFloat(KEY_PROXIMITY_RADIUS_KM, (float) radiusKm);
        editor.apply();
    }

    public String getAvatar() {
        return pref.getString(KEY_AVATAR, "avatar_1");
    }

    public void setAvatar(String avatarId) {
        editor.putString(KEY_AVATAR, avatarId != null ? avatarId : "avatar_1");
        editor.apply();
    }

    public void logoutUser() {
        editor.clear();
        editor.apply();
    }

    // Called once right after login, from the "Remember me" checkbox.
    public void setRememberMe(boolean remember) {
        editor.putBoolean(KEY_REMEMBER_ME, remember);
        editor.apply();
    }

    // Defaults to true so a session created before this existed stays logged in, same as before.
    public boolean getRememberMe() {
        return pref.getBoolean(KEY_REMEMBER_ME, true);
    }

    // Called once when the app process starts. If the last login had "Remember me" unchecked,
    // the session should only last until the app is fully closed - log it out here instead of
    // letting the user land back on their home screen.
    public void clearIfNotRemembered() {
        if (isLoggedIn() && !getRememberMe()) {
            logoutUser();
        }
    }
}
