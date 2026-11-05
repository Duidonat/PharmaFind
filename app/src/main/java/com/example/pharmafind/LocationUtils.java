package com.example.pharmafind;

public class LocationUtils {

    public static double[] getCoordinates(String city, String barangay, double currentLat, double currentLng) {
        if (currentLat != 0.0 && currentLng != 0.0) {
            return new double[]{currentLat, currentLng};
        }
        if (barangay == null) return new double[]{0.0, 0.0};
        String bg = barangay.trim().toLowerCase();

        // Approximate GPS coordinates for Lipa City & neighboring barangays
        if (bg.contains("marawoy")) return new double[]{13.9416, 121.1631};
        if (bg.contains("lodlod")) return new double[]{13.9231, 121.1415};
        if (bg.contains("tambo")) return new double[]{13.9620, 121.1620};
        if (bg.contains("sabang")) return new double[]{13.9310, 121.1712};
        if (bg.contains("balintawak")) return new double[]{13.9450, 121.1550};
        if (bg.contains("inosloban") || bg.contains("inosluban")) return new double[]{13.9750, 121.1680};
        if (bg.contains("bugtong")) return new double[]{13.9820, 121.1720};
        if (bg.contains("dagatan")) return new double[]{13.9050, 121.1500};
        if (bg.contains("sampaguita")) return new double[]{13.9100, 121.1600};
        if (bg.contains("banaybanay") || bg.contains("banay-banay")) return new double[]{13.9000, 121.1300};
        if (bg.contains("antipolo")) return new double[]{13.9550, 121.1850};
        if (bg.contains("mataasnakahoy") || bg.contains("mataas na kahoy")) return new double[]{13.9650, 121.1080};
        if (bg.contains("san jose")) return new double[]{13.8800, 121.1000};
        if (bg.contains("malvar")) return new double[]{14.0400, 121.1600};
        if (bg.contains("tanauan")) return new double[]{14.0800, 121.1500};
        if (bg.contains("batangas")) return new double[]{13.7565, 121.0583};
        if (bg.contains("poblacion") || bg.contains("lipa city")) return new double[]{13.9410, 121.1620};

        return new double[]{0.0, 0.0};
    }
}
