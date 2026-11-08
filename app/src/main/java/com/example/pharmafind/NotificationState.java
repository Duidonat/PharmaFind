package com.example.pharmafind;

import android.content.Context;
import android.content.SharedPreferences;

public class NotificationState {
    private static final String PREF_NAME = "PharmaFindNotifState";
    private final SharedPreferences prefs;

    public NotificationState(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public int get(String role, int userId, String type) {
        String key = "notif_" + role + "_" + userId + "_" + type;
        return prefs.getInt(key, -1);
    }

    public void set(String role, int userId, String type, int value) {
        String key = "notif_" + role + "_" + userId + "_" + type;
        prefs.edit().putInt(key, value).apply();
    }
}
