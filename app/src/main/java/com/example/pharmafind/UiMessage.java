package com.example.pharmafind;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.google.android.material.snackbar.Snackbar;

// Short feedback messages (saved, failed, please fill in...) shown as a Snackbar instead of a Toast.
// A Snackbar lives inside a window, so pass the screen the user is looking at: the open bottom sheet
// (Dialog) while it is showing, otherwise the Activity or Fragment. Do not use it right before the
// screen closes or switches to another sheet - the message would go with it (use a Toast there).
final class UiMessage {

    private UiMessage() {}

    static void show(View anchor, String message) {
        snack(anchor, message, Snackbar.LENGTH_SHORT);
    }

    static void showLong(View anchor, String message) {
        snack(anchor, message, Snackbar.LENGTH_LONG);
    }

    static void show(Activity activity, String message) {
        show(activity.findViewById(android.R.id.content), message);
    }

    static void showLong(Activity activity, String message) {
        showLong(activity.findViewById(android.R.id.content), message);
    }

    static void show(Fragment fragment, String message) {
        show(fragment.getView(), message);
    }

    static void showLong(Fragment fragment, String message) {
        showLong(fragment.getView(), message);
    }

    static void show(Dialog dialog, String message) {
        show(dialog.findViewById(android.R.id.content), message);
    }

    static void showLong(Dialog dialog, String message) {
        showLong(dialog.findViewById(android.R.id.content), message);
    }

    private static void snack(View anchor, String message, int duration) {
        // The screen may already be gone by the time a database call comes back.
        if (anchor == null || !anchor.isAttachedToWindow()) return;

        Snackbar snackbar = Snackbar.make(anchor, message, duration);

        // Lift it above the bottom menu on the two home screens (the id is not found in a sheet or on other screens).
        View root = anchor.getRootView();
        View nav = root.findViewById(R.id.bottomNavigation);
        if (nav == null) nav = root.findViewById(R.id.pharmacyBottomNavigation);
        if (nav != null && nav.getVisibility() == View.VISIBLE) snackbar.setAnchorView(nav);

        // Database error messages can be long; the default stops after two lines.
        TextView text = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
        if (text != null) text.setMaxLines(4);

        snackbar.show();
    }
}
