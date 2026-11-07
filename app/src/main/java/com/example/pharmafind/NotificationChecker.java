package com.example.pharmafind;

import android.content.Context;
import android.location.Location;
import android.os.SystemClock;
import android.util.Log;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Looks for anything new for the logged-in account and posts a system notification for it.
 * Called from NotificationWorker (background, every ~15 min) and, while the app is open, from the
 * home screens (throttled). Both paths share one lock and one "last seen" state, so nothing is
 * announced twice.
 *
 * The first run for an account only records the current position (a silent baseline) - old
 * messages and replies are never announced as if they were new.
 */
public final class NotificationChecker {

    private static final String TAG = "NotificationChecker";

    private static final String KEY_REPLY = "reply";
    private static final String KEY_MESSAGE = "msg";
    private static final String KEY_REQUEST = "request";

    // Matches the pharmacy feed's default "Within 5 km" filter.
    private static final double REQUEST_RADIUS_KM = 5.0;
    private static final long FOREGROUND_MIN_INTERVAL_MS = 10_000;

    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final ExecutorService FOREGROUND_EXECUTOR = Executors.newSingleThreadExecutor();
    private static long lastForegroundRun = 0;

    private NotificationChecker() {}

    /** Fire-and-forget version for screens. Skips if a check ran very recently or one is in progress. */
    public static void checkAsync(Context context) {
        final Context app = context.getApplicationContext();
        FOREGROUND_EXECUTOR.execute(() -> check(app, false));
    }

    /** Blocking. Must not be called on the main thread. force = true for the background worker. */
    public static void check(Context context, boolean force) {
        final Context app = context.getApplicationContext();

        if (force) {
            LOCK.lock();
        } else {
            long now = SystemClock.elapsedRealtime();
            if (now - lastForegroundRun < FOREGROUND_MIN_INTERVAL_MS) return;
            if (!LOCK.tryLock()) return;
            lastForegroundRun = now;
        }

        Connection con = null;
        try {
            UserSessionManager session = new UserSessionManager(app);
            if (!session.isLoggedIn() || session.getUserId() < 0) return;

            con = new conSQL().conclass();
            if (con == null) return;

            NotificationHelper.createChannels(app);
            NotificationState state = new NotificationState(app);

            if ("pharmacy".equalsIgnoreCase(session.getRole())) {
                checkPharmacy(app, con, session, state);
            } else if ("customer".equalsIgnoreCase(session.getRole())) {
                checkCustomer(app, con, session, state);
            }
        } catch (Exception e) {
            Log.e(TAG, "Notification check failed: " + e.getMessage());
        } finally {
            if (con != null) {
                try { con.close(); } catch (SQLException ignored) {}
            }
            LOCK.unlock();
        }
    }

    // ------------------------------------------------------------- customer

    private static void checkCustomer(Context ctx, Connection con, UserSessionManager session,
                                      NotificationState state) throws SQLException {
        int uid = session.getUserId();
        processReplies(ctx, con, state, uid);
        processMessages(ctx, con, state, "customer", uid, "pharmacy", false);
    }

    private static void processReplies(Context ctx, Connection con, NotificationState state,
                                       int customerId) throws SQLException {
        int last = state.get("customer", customerId, KEY_REPLY);
        if (last < 0) {
            state.set("customer", customerId, KEY_REPLY, maxReplyId(con, customerId));
            return;
        }

        String sql = "SELECT r.ReplyID, r.InquiryID, r.PharmacyName, r.AvailabilityStatus, i.MedicineName " +
                "FROM dbo.PharmacyReplies r " +
                "JOIN dbo.Inquiries i ON i.InquiryID = r.InquiryID " +
                "WHERE i.CustomerID = ? AND r.ReplyID > ? " +
                "ORDER BY r.ReplyID ASC";

        Map<Integer, Group> groups = new LinkedHashMap<>();
        int maxId = last;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setInt(2, last);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    maxId = Math.max(maxId, rs.getInt("ReplyID"));
                    int inquiryId = rs.getInt("InquiryID");
                    Group g = groups.get(inquiryId);
                    if (g == null) {
                        g = new Group();
                        g.medicine = rs.getString("MedicineName");
                        groups.put(inquiryId, g);
                    }
                    g.count++;
                    g.name = rs.getString("PharmacyName");
                    g.detail = rs.getString("AvailabilityStatus");
                }
            }
        }

        if (maxId > last) state.set("customer", customerId, KEY_REPLY, maxId);

        for (Map.Entry<Integer, Group> e : groups.entrySet()) {
            Group g = e.getValue();
            NotificationHelper.notifyReplies(ctx, e.getKey(), g.medicine, g.count, g.name, g.detail);
        }
    }

    // ------------------------------------------------- messages (both roles)

    private static void processMessages(Context ctx, Connection con, NotificationState state,
                                        String role, int userId, String senderRole,
                                        boolean forPharmacy) throws SQLException {
        int last = state.get(role, userId, KEY_MESSAGE);
        if (last < 0) {
            state.set(role, userId, KEY_MESSAGE, maxMessageId(con, userId, senderRole));
            return;
        }

        String sql = "SELECT MessageID, InquiryID, SenderID, SenderName, MessageText " +
                "FROM dbo.Messages " +
                "WHERE ReceiverID = ? AND SenderRole = ? AND MessageID > ? " +
                "ORDER BY MessageID ASC";

        Map<Integer, Group> groups = new LinkedHashMap<>();
        int maxId = last;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, senderRole);
            ps.setInt(3, last);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    maxId = Math.max(maxId, rs.getInt("MessageID"));
                    int inquiryId = rs.getInt("InquiryID");
                    Group g = groups.get(inquiryId);
                    if (g == null) {
                        g = new Group();
                        groups.put(inquiryId, g);
                    }
                    g.count++;
                    g.otherId = rs.getInt("SenderID");
                    g.name = rs.getString("SenderName");
                    g.detail = rs.getString("MessageText");
                }
            }
        }

        if (maxId > last) state.set(role, userId, KEY_MESSAGE, maxId);

        for (Map.Entry<Integer, Group> e : groups.entrySet()) {
            Group g = e.getValue();
            NotificationHelper.notifyMessages(ctx, forPharmacy, e.getKey(), g.otherId, g.name, g.count, g.detail);
        }
    }

    // ------------------------------------------------------------- pharmacy

    private static void checkPharmacy(Context ctx, Connection con, UserSessionManager session,
                                      NotificationState state) throws SQLException {
        int uid = session.getUserId();

        boolean approved = syncPharmacyStatus(ctx, con, session, uid);

        processMessages(ctx, con, state, "pharmacy", uid, "customer", true);

        if (approved) {
            processNewRequests(ctx, con, session, state, uid);
        } else {
            // Not approved yet: keep the marker current so approval doesn't trigger a flood of old requests.
            state.set("pharmacy", uid, KEY_REQUEST, maxInquiryId(con));
        }
    }

    /** Detects pending -> approved/rejected, keeps the saved session in sync, returns whether approved. */
    private static boolean syncPharmacyStatus(Context ctx, Connection con, UserSessionManager session,
                                              int pharmacyId) throws SQLException {
        String current = null;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT Status FROM dbo.Pharmacies WHERE PharmacyID = ?")) {
            ps.setInt(1, pharmacyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) current = rs.getString("Status");
            }
        }
        if (current == null || current.trim().isEmpty()) current = "pending";
        current = current.trim().toLowerCase();

        String saved = session.getStatus() == null ? "pending" : session.getStatus().trim().toLowerCase();
        if (!current.equals(saved)) {
            session.setStatus(current);
            if (current.equals("approved")) NotificationHelper.notifyAccountStatus(ctx, true);
            else if (current.equals("rejected")) NotificationHelper.notifyAccountStatus(ctx, false);
        }
        return current.equals("approved");
    }

    private static void processNewRequests(Context ctx, Connection con, UserSessionManager session,
                                           NotificationState state, int pharmacyId) throws SQLException {
        int last = state.get("pharmacy", pharmacyId, KEY_REQUEST);
        if (last < 0) {
            state.set("pharmacy", pharmacyId, KEY_REQUEST, maxInquiryId(con));
            return;
        }

        String bg = session.getBarangay() == null ? "" : session.getBarangay().trim();
        String ct = session.getCity() == null ? "" : session.getCity().trim();
        double[] pharmacyCoords = LocationUtils.getCoordinates(ct, bg, session.getLatitude(), session.getLongitude());

        // Same area test as the pharmacy feed, then a distance check below.
        String sql = "SELECT i.InquiryID, i.MedicineName, i.City, i.Barangay, " +
                "COALESCE(i.Latitude, c.Latitude, 0.0) AS Lat, COALESCE(i.Longitude, c.Longitude, 0.0) AS Lng " +
                "FROM dbo.Inquiries i " +
                "LEFT JOIN dbo.Customers c ON c.CustomerID = i.CustomerID " +
                "WHERE i.InquiryID > ? AND LOWER(i.Status) = 'open' " +
                "  AND (LOWER(i.Barangay) LIKE '%' + LOWER(?) + '%' " +
                "       OR LOWER(i.City) LIKE '%' + LOWER(?) + '%' " +
                "       OR LOWER(?) LIKE '%' + LOWER(i.Barangay) + '%' " +
                "       OR i.Barangay IS NULL OR i.Barangay = '' OR ? = '') " +
                "ORDER BY i.InquiryID ASC";

        int maxId = last;
        int count = 0;
        int newestId = -1;
        String newestMedicine = null;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, last);
            ps.setString(2, bg);
            ps.setString(3, ct);
            ps.setString(4, bg);
            ps.setString(5, bg);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int inquiryId = rs.getInt("InquiryID");
                    maxId = Math.max(maxId, inquiryId);

                    double[] c = LocationUtils.getCoordinates(
                            rs.getString("City"), rs.getString("Barangay"),
                            rs.getDouble("Lat"), rs.getDouble("Lng"));

                    boolean known = pharmacyCoords[0] != 0.0 && pharmacyCoords[1] != 0.0
                            && c[0] != 0.0 && c[1] != 0.0;
                    if (known) {
                        float[] result = new float[1];
                        Location.distanceBetween(pharmacyCoords[0], pharmacyCoords[1], c[0], c[1], result);
                        if (result[0] / 1000.0 > REQUEST_RADIUS_KM) continue;
                    }
                    // Unknown distance: notify anyway rather than silently miss a customer.

                    count++;
                    newestId = inquiryId;
                    newestMedicine = rs.getString("MedicineName");
                }
            }
        }

        if (maxId > last) state.set("pharmacy", pharmacyId, KEY_REQUEST, maxId);
        if (count > 0) NotificationHelper.notifyNewRequests(ctx, count, newestId, newestMedicine);
    }

    // ------------------------------------------------------------ baselines

    private static int maxReplyId(Connection con, int customerId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT ISNULL(MAX(r.ReplyID), 0) FROM dbo.PharmacyReplies r " +
                "JOIN dbo.Inquiries i ON i.InquiryID = r.InquiryID WHERE i.CustomerID = ?")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static int maxMessageId(Connection con, int receiverId, String senderRole) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT ISNULL(MAX(MessageID), 0) FROM dbo.Messages WHERE ReceiverID = ? AND SenderRole = ?")) {
            ps.setInt(1, receiverId);
            ps.setString(2, senderRole);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static int maxInquiryId(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT ISNULL(MAX(InquiryID), 0) FROM dbo.Inquiries");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Collects several rows about the same inquiry into one notification. */
    private static class Group {
        String medicine;
        String name;
        String detail;
        int otherId;
        int count;
    }
}
