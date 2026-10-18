package com.example.pharmafind;

import android.app.Application;

public class PharmaFindApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Runs once per app process start - the right place to end a session that was only
        // meant to last until the app was closed (see UserSessionManager.setRememberMe).
        new UserSessionManager(this).clearIfNotRemembered();
    }
}
