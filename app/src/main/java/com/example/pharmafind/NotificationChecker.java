package com.example.pharmafind;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationChecker {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void checkAsync(Context context) {
        if (context == null) return;
        final Context appContext = context.getApplicationContext();

        executor.execute(() -> {
            UserSessionManager session = new UserSessionManager(appContext);
            if (!session.isLoggedIn()) return;

            conSQL sqlHelper = new conSQL();
            Connection con = sqlHelper.conclass();
            if (con == null) return;

            NotificationState state = new NotificationState(appContext);
            String role = session.getRole();
            int userId = session.getUserId();

            try {
                if ("customer".equalsIgnoreCase(role)) {
                    int lastReply = state.get("customer", userId, "reply");
                    if (lastReply >= 0) {
                        String query = "SELECT TOP 1 r.ReplyID, r.InquiryID, r.PharmacyName, i.MedicineName " +
                                "FROM dbo.PharmacyReplies r " +
                                "JOIN dbo.Inquiries i ON i.InquiryID = r.InquiryID " +
                                "WHERE i.CustomerID = ? AND r.ReplyID > ? AND LOWER(ISNULL(i.Status, 'open')) <> 'fulfilled' " +
                                "ORDER BY r.ReplyID DESC";
                        PreparedStatement stmt = con.prepareStatement(query);
                        stmt.setInt(1, userId);
                        stmt.setInt(2, lastReply);
                        ResultSet rs = stmt.executeQuery();
                        if (rs.next()) {
                            int newReplyId = rs.getInt("ReplyID");
                            int inqId = rs.getInt("InquiryID");
                            String pName = rs.getString("PharmacyName");
                            String med = rs.getString("MedicineName");

                            Intent intent = new Intent(appContext, ActivityInquiryThread.class);
                            intent.putExtra("inquiry_id", inqId);
                            NotificationHelper.sendReplyNotification(
                                    appContext,
                                    1001,
                                    "Pharmacy Responded!",
                                    (pName != null ? pName : "A pharmacy") + " responded to your request for " + (med != null ? med : "medicine"),
                                    intent
                            );
                            state.set("customer", userId, "reply", newReplyId);
                        }
                        stmt.close();
                    }

                    int lastMsg = state.get("customer", userId, "msg");
                    if (lastMsg >= 0) {
                        String query = "SELECT TOP 1 m.MessageID, m.InquiryID, m.SenderID, m.SenderName, m.MessageText " +
                                "FROM dbo.Messages m " +
                                "JOIN dbo.Inquiries i ON i.InquiryID = m.InquiryID " +
                                "WHERE m.ReceiverID = ? AND m.SenderRole = 'pharmacy' AND m.MessageID > ? AND LOWER(ISNULL(i.Status, 'open')) <> 'fulfilled' " +
                                "ORDER BY m.MessageID DESC";
                        PreparedStatement stmt = con.prepareStatement(query);
                        stmt.setInt(1, userId);
                        stmt.setInt(2, lastMsg);
                        ResultSet rs = stmt.executeQuery();
                        if (rs.next()) {
                            int newMsgId = rs.getInt("MessageID");
                            int inqId = rs.getInt("InquiryID");
                            int pId = rs.getInt("SenderID");
                            String pName = rs.getString("SenderName");
                            String text = rs.getString("MessageText");

                            Intent intent = new Intent(appContext, ActivityChat.class);
                            intent.putExtra("inquiry_id", inqId);
                            intent.putExtra("receiver_id", pId);
                            intent.putExtra("receiver_name", pName != null ? pName : "Pharmacy");
                            NotificationHelper.sendChatNotification(
                                    appContext,
                                    1002,
                                    "New Message from Pharmacy",
                                    (pName != null ? pName : "Pharmacy") + ": " + text,
                                    intent
                            );
                            state.set("customer", userId, "msg", newMsgId);
                        }
                        stmt.close();
                    }
                } else if ("pharmacy".equalsIgnoreCase(role)) {
                    int lastMsg = state.get("pharmacy", userId, "msg");
                    if (lastMsg >= 0) {
                        String query = "SELECT TOP 1 m.MessageID, m.InquiryID, m.SenderID, m.SenderName, m.MessageText " +
                                "FROM dbo.Messages m " +
                                "JOIN dbo.Inquiries i ON i.InquiryID = m.InquiryID " +
                                "WHERE m.ReceiverID = ? AND m.SenderRole = 'customer' AND m.MessageID > ? AND LOWER(ISNULL(i.Status, 'open')) <> 'fulfilled' " +
                                "ORDER BY m.MessageID DESC";
                        PreparedStatement stmt = con.prepareStatement(query);
                        stmt.setInt(1, userId);
                        stmt.setInt(2, lastMsg);
                        ResultSet rs = stmt.executeQuery();
                        if (rs.next()) {
                            int newMsgId = rs.getInt("MessageID");
                            int inqId = rs.getInt("InquiryID");
                            int custId = rs.getInt("SenderID");
                            String cName = rs.getString("SenderName");
                            String text = rs.getString("MessageText");

                            Intent intent = new Intent(appContext, ActivityChat.class);
                            intent.putExtra("inquiry_id", inqId);
                            intent.putExtra("receiver_id", custId);
                            intent.putExtra("receiver_name", cName != null ? cName : "Customer");
                            NotificationHelper.sendChatNotification(
                                    appContext,
                                    2001,
                                    "New Customer Message",
                                    (cName != null ? cName : "Customer") + ": " + text,
                                    intent
                            );
                            state.set("pharmacy", userId, "msg", newMsgId);
                        }
                        stmt.close();
                    }

                    int lastInq = state.get("pharmacy", userId, "inq");
                    double pLat = session.getLatitude();
                    double pLng = session.getLongitude();
                    double[] pCoords = LocationUtils.getCoordinates(appContext, session.getCity(), session.getBarangay(), pLat, pLng);
                    pLat = pCoords[0];
                    pLng = pCoords[1];
                    double radiusKm = session.getProximityRadiusKm();

                    String inqQuery = "SELECT TOP 5 i.InquiryID, i.MedicineName, i.City, i.Barangay, " +
                            "COALESCE(i.Latitude, c.Latitude, 0.0) AS Latitude, COALESCE(i.Longitude, c.Longitude, 0.0) AS Longitude " +
                            "FROM dbo.Inquiries i " +
                            "LEFT JOIN dbo.Customers c ON i.CustomerID = c.CustomerID " +
                            "WHERE LOWER(ISNULL(i.Status, 'open')) <> 'fulfilled' " +
                            (lastInq >= 0 ? "AND i.InquiryID > ? " : "") +
                            "ORDER BY i.InquiryID DESC";
                    PreparedStatement inqStmt = con.prepareStatement(inqQuery);
                    if (lastInq >= 0) inqStmt.setInt(1, lastInq);
                    ResultSet inqRs = inqStmt.executeQuery();
                    int maxInqFound = lastInq;
                    while (inqRs.next()) {
                        int inqId = inqRs.getInt("InquiryID");
                        if (inqId > maxInqFound) maxInqFound = inqId;
                        if (lastInq >= 0) {
                            String med = inqRs.getString("MedicineName");
                            String city = inqRs.getString("City");
                            String barangay = inqRs.getString("Barangay");
                            double cLat = inqRs.getDouble("Latitude");
                            double cLng = inqRs.getDouble("Longitude");
                            double[] cCoords = LocationUtils.getCoordinates(appContext, city, barangay, cLat, cLng);

                            boolean isNearby = false;
                            double distanceKm = -1.0;
                            if (pLat != 0.0 && pLng != 0.0 && cCoords[0] != 0.0 && cCoords[1] != 0.0) {
                                float[] results = new float[1];
                                android.location.Location.distanceBetween(pLat, pLng, cCoords[0], cCoords[1], results);
                                distanceKm = results[0] / 1000.0;
                                if (distanceKm <= radiusKm) isNearby = true;
                            } else if (session.getBarangay() != null && session.getBarangay().equalsIgnoreCase(barangay)) {
                                isNearby = true;
                            }

                            if (isNearby) {
                                Intent intent = new Intent(appContext, ActivityInquiryThread.class);
                                intent.putExtra("inquiry_id", inqId);
                                String distStr = distanceKm >= 0.0 ? String.format(java.util.Locale.US, " (%.1f km away)", distanceKm) : "";
                                NotificationHelper.sendUrgentNotification(
                                        appContext,
                                        2002,
                                        "⚡ Nearby Customer Request!",
                                        "A customer nearby inquired for " + (med != null ? med : "medicine") + distStr,
                                        intent
                                );
                                break;
                            }
                        }
                    }
                    if (maxInqFound > lastInq) {
                        state.set("pharmacy", userId, "inq", maxInqFound);
                    }
                    inqStmt.close();
                }

                con.close();
            } catch (SQLException e) {
                try { con.close(); } catch (SQLException ignored) {}
                Log.e("NotificationChecker", "Error checking background notifications: " + e.getMessage());
            }
        });
    }
}
