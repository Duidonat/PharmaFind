package com.example.pharmafind;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers the last reply / message / request ID each account has already been notified about,
 * so the same event is never announced twice. Kept per account (role + id) so switching accounts
 * on one phone does not mix them up. -1 means "never checked" and triggers a silent baseline.
 */
public class NotificationState {

    private static final String PREF_NAME = "PharmaFindNotifState";
    private static final String KEY_PERMISSION_ASKED = "permissionAsked";

    private final SharedPreferences pref;

    public NotificationState(Context context) {
        pref = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static String key(String role, int userId, String name) {
        return role + "_" + userId + "_" + name;
    }

    public int get(String role, int userId, String name) {
        return pref.getInt(key(role, userId, name), -1);
    }

    public void set(String role, int userId, String name, int value) {
        pref.edit().putInt(key(role, userId, name), value).apply();
    }

    public boolean wasPermissionAsked() {
        return pref.getBoolean(KEY_PERMISSION_ASKED, false);
    }

    public void setPermissionAsked() {
        pref.edit().putBoolean(KEY_PERMISSION_ASKED, true).apply();
    }
}
