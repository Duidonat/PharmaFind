package com.example.pharmafind;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Resolves geographic coordinates for distance-based request filtering.
 *
 * Resolution order:
 *  1. If non-zero {@code currentLat}/{@code currentLng} are provided, use them directly
 *     (these come from the device's last known GPS location stored in the session).
 *  2. Otherwise, fall back to {@link Geocoder} using {@code city} + {@code barangay}.
 *  3. If geocoding fails or returns nothing, fall back to the hardcoded Lipa City
 *     barangay table so existing users are not regressed.
 *  4. Last resort: {@code (0.0, 0.0)}.
 */
public class LocationUtils {

    private static final String TAG = "LocationUtils";

    /**
     * Approximate GPS coordinates for Lipa City & neighboring barangays.
     * Used only as a last-resort fallback when neither GPS nor geocoding is available.
     */
    private static final String[][] LIPA_COORDS = {
            {"marawoy", "13.9416", "121.1631"},
            {"lodlod", "13.9231", "121.1415"},
            {"tambo", "13.9620", "121.1620"},
            {"sabang", "13.9310", "121.1712"},
            {"balintawak", "13.9450", "121.1550"},
            {"inosloban", "13.9750", "121.1680"},
            {"inosluban", "13.9750", "121.1680"},
            {"bugtong", "13.9820", "121.1720"},
            {"dagatan", "13.9050", "121.1500"},
            {"sampaguita", "13.9100", "121.1600"},
            {"banaybanay", "13.9000", "121.1300"},
            {"banay-banay", "13.9000", "121.1300"},
            {"antipolo", "13.9550", "121.1850"},
            {"mataasnakahoy", "13.9650", "121.1080"},
            {"mataas na kahoy", "13.9650", "121.1080"},
            {"san jose", "13.8800", "121.1000"},
            {"malvar", "14.0400", "121.1600"},
            {"tanauan", "14.0800", "121.1500"},
            {"batangas", "13.7565", "121.0583"},
            {"poblacion", "13.9410", "121.1620"},
            {"lipa city", "13.9410", "121.1620"},
    };

    /**
     * Resolve coordinates for a location.
     *
     * @param context         Android context (needed for Geocoder)
     * @param city            city name (used for geocoding fallback)
     * @param barangay        barangay name (used for geocoding fallback + table lookup)
     * @param currentLat      device's last known latitude (0.0 if unknown)
     * @param currentLng      device's last known longitude (0.0 if unknown)
     * @return double[]{latitude, longitude}; {@code (0.0, 0.0)} if unresolved
     */
    public static double[] getCoordinates(String city, String barangay,
                                          double currentLat, double currentLng) {
        return getCoordinates(null, city, barangay, currentLat, currentLng);
    }

    public static double[] getCoordinates(Context context, String city, String barangay,
                                          double currentLat, double currentLng) {

        // 1. Use device GPS location first - most accurate, works everywhere
        if (currentLat != 0.0 && currentLng != 0.0) {
            return new double[]{currentLat, currentLng};
        }

        // 2. Fall back to geocoding using city + barangay
        double[] geo = geocode(context, city, barangay);
        if (geo[0] != 0.0 && geo[1] != 0.0) {
            return geo;
        }

        // 3. Last resort: hardcoded Lipa City table (legacy behavior)
        if (barangay != null) {
            String bg = barangay.trim().toLowerCase();
            for (String[] row : LIPA_COORDS) {
                if (bg.contains(row[0])) {
                    try {
                        return new double[]{
                                Double.parseDouble(row[1]),
                                Double.parseDouble(row[2])
                        };
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        return new double[]{0.0, 0.0};
    }

    /**
     * Attempt to geocode a city + barangay using Android's {@link Geocoder}.
     */
    private static double[] geocode(Context context, String city, String barangay) {
        if (context == null) return new double[]{0.0, 0.0};

        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        String query = (barangay != null ? barangay + ", " : "")
                + (city != null ? city + ", " : "")
                + "Philippines";

        try {
            List<Address> addresses = geocoder.getFromLocationName(query, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                double lat = addr.getLatitude();
                double lng = addr.getLongitude();
                if (lat != 0.0 && lng != 0.0) {
                    return new double[]{lat, lng};
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Geocoder I/O error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Geocoder invalid argument: " + e.getMessage());
        }

        return new double[]{0.0, 0.0};
    }
}
