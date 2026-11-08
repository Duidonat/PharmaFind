# PharmaFind - Medicine Inquiry & Pharmacy Locator Platform

**PharmaFind** is an Android mobile application designed to connect patients directly with nearby pharmacies for medicine availability inquiries, real-time price quotes, direct patient-pharmacy messaging, and location-based store navigation.

---

## 🚀 System Features & Architecture

### 1. 👥 Dual Role Access Control (RBAC)
- **Customer Mode**: Post medicine inquiries with optional prescription photo attachments, receive price/stock quotes from pharmacies, accept offers, and initiate direct chats.
- **Pharmacy Mode**: Browse patient requests within a customizable distance radius ($2\text{ km} - 50\text{ km}$), submit stock quotes (*In Stock*, *Available*, *Limited Stock*), and communicate directly with patients.

### 2. 📍 Location & Proximity Engine
- **Map Pin Picker (`ActivityMapPicker`)**: Allows customers and pharmacies to select exact GPS coordinates visually on Google Maps.
- **Distance Calculation (`LocationUtils`)**: Calculates exact distance (in km) between patient and pharmacy coordinates.
  - **Primary source**: device GPS coordinates stored in the session — works for any city, not just one hardcoded list.
  - **Fallback 1**: Android `Geocoder` resolves city + barangay to coordinates.
  - **Fallback 2**: legacy hardcoded Lipa City barangay table (kept so existing users aren't regressed).
- **Google Maps Navigation**: One-tap turn-by-turn navigation link directly to the selected pharmacy (`ActivityPharmacyProfile`).
- **Configurable proximity radius**: Pharmacies can set their own nearby-request radius (1–50 km, default 5 km) in the session settings. The radius is used by both the notification checker and the pharmacy notifications feed.

### 3. 🔔 Categorized Notification & System Alert Engine
- **Four dedicated Android notification channels** (`NotificationHelper`):
  | Channel | Purpose | Importance |
  |---|---|---|
  | `pharmafind_urgent` | Nearby customer requests (new inquiry within radius) | HIGH (sound + heads-up) |
  | `pharmafind_chats` | New direct messages | DEFAULT |
  | `pharmafind_replies` | Pharmacy responses to your medicine request | DEFAULT |
  | `pharmafind_general` | System reminders | LOW (no sound) |
  Users can silence or re-prioritize each channel independently in Android Settings → Apps → PharmaFind → Notifications.
- **Filtered Notification Screen**:
  - **All**: View all alerts including pharmacy responses, nearby requests, and active chats.
  - **Pharmacy Responses / Nearby Requests**: Customer view shows pharmacy price quotes; Pharmacy view shows urgent customer inquiries posted within the configured radius.
  - **Chats**: View direct message conversations.
- **Background Notification Service (`NotificationChecker`, `NotificationHelper`, `NotificationState`)**:
  - Periodically checks the database for new responses, chat messages, or nearby requests and issues status bar notifications.
  - Now scheduled through **WorkManager** (`NotificationWorker`, `NotificationScheduler.schedulePeriodicCheck`) instead of a 5-second `Handler.postDelayed` loop. WorkManager survives app restarts, respects Doze/App Standby, applies exponential backoff on failure, and only runs when the device has network connectivity.
  - Check interval: every 15 minutes (5-minute flex window).
- **Dynamic Badge Counts**: Real-time badge indicators on the bottom navigation bar for unread notifications.

### 4. 🖼️ Prescription Photo & Inspection
- **Secure Image Upload**: Patients can attach prescription photos to their medicine requests.
- **Privacy Enforcement**: Prescription photos are visible strictly to the requesting patient and responding pharmacies.
- **Full-Screen Viewer**: Tap any prescription image in `ActivityInquiryThread` to inspect it in full-screen view.

### 5. 💬 Real-Time Direct Messaging
- **Inquiry-Linked Chat (`ActivityChat`)**: Direct two-way messaging between customer and pharmacy.
- **Patient Satisfaction & Storage Auto-Cleanup**: Once care is completed or satisfied, the chat storage automatically cleans up and marks the inquiry as fulfilled.

---

## 🔧 Configuration

### Proximity Radius
The pharmacy nearby-request radius defaults to **5 km**. It can be changed programmatically via:

```java
sessionManager.setProximityRadiusKm(10.0); // km
double current = sessionManager.getProximityRadiusKm();
```

The value is persisted in `SharedPreferences` and read by `NotificationChecker` (background notifications) and `FragmentPharmacyNotifications` (pharmacy notifications tab).

### Notification Channels
Channels are created on first use in `NotificationHelper.createChannels(context)`. They are registered on app start in both `CustomerHomeActivity` and `PharmacyHomeActivity`.

---

## 🗄️ Database Setup

Run [`database/pharmafind_setup.sql`](database/pharmafind_setup.sql) once in **SQL Server Management Studio** (or your MSSQL remote host, e.g., Somee).

### Core Tables:
1. `dbo.Customers` - Customer accounts, credentials, and location coordinates.
2. `dbo.Pharmacies` - Pharmacy profiles, business permits, operating hours, and location coordinates.
3. `dbo.Inquiries` - Medicine requests posted by customers.
4. `dbo.PharmacyReplies` - Stock availability responses and price quotes posted by pharmacies.
5. `dbo.Messages` - Direct chat messages between customers and pharmacies.

The database configuration (host, username, password) lives in `conSQL.java`.

> ⚠️ **Security note**: Database credentials are currently embedded in `conSQL.java` and ship inside the APK. For production deployment, move credential storage to a backend proxy or encrypted configuration so they are not recoverable from the compiled app.

---

## 🛠️ Project Structure & Key Files

| Module / File | Description |
| :--- | :--- |
| `CustomerHomeActivity.java` | Customer main layout & bottom navigation host. Schedules periodic notification checks via WorkManager. |
| `PharmacyHomeActivity.java` | Pharmacy main layout & bottom navigation host. Schedules periodic notification checks via WorkManager. |
| `FragmentCustomerFeed.java` | Customer request feed with search & distance radius filters ($2\text{ km} - 50\text{ km}$). |
| `FragmentPharmacyFeed.java` | Pharmacy request feed for local medicine inquiries. |
| `FragmentCustomerNotifications.java` | Customer categorized notification screen (All, Responses, Chats). |
| `FragmentPharmacyNotifications.java` | Pharmacy categorized notification screen (All, Nearby < radius, Chats). Uses the configured proximity radius. |
| `ActivityInquiryThread.java` | Full request thread view with prescription photo viewer and pharmacy responses. |
| `ActivityChat.java` | Direct messaging interface between patient and pharmacy. |
| `ActivityPharmacyProfile.java` | Detailed pharmacy profile with Google Maps navigation & contact options. |
| `ActivityMapPicker.java` | Interactive Google Maps coordinate picker for profile & request location setup. |
| `NotificationChecker.java` | Background checker for system status bar notifications (replies, messages, nearby requests). |
| `NotificationHelper.java` | Android notification channel management + channel-specific send helpers. |
| `NotificationState.java` | Tracks last-seen notification IDs per role/user/type in SharedPreferences. |
| `NotificationScheduler.java` | Entry point: one-off sync (`sync`) + periodic WorkManager scheduling (`schedulePeriodicCheck`). |
| `NotificationWorker.java` | WorkManager `Worker` that runs `NotificationChecker.checkAsync` on a 15-minute cycle with network constraint and exponential backoff. |
| `UserSessionManager.java` | Session management (login, role, location, remember-me, proximity radius). |
| `LocationUtils.java` | GPS-first coordinate resolution with Geocoder and legacy Lipa City fallback. |
| `conSQL.java` | SQL Server connection helper (JDBC via jTDS). |
| `PasswordUtils.java` | Password hashing utility (SHA-256). |

---

## 📱 Build & Run

### Requirements
- Android Studio (Giraffe or newer)
- Android SDK compileSdk 36, minSdk 24
- Java 11 (source/target compatibility)
- A SQL Server database configured via `database/pharmafind_setup.sql`

### Steps
1. Open the project in Android Studio.
2. Update `conSQL.java` with your database host, username, and password.
3. Run [`database/pharmafind_setup.sql`](database/pharmafind_setup.sql) against your SQL Server instance.
4. Connect a device or start an emulator with Google Play Services (needed for Google Maps).
5. Click **Run** in Android Studio.

### Permissions
The app declares `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, and `POST_NOTIFICATIONS`. Location and notification permissions are requested at runtime where required.

---

## 🔐 Security & Privacy

- Passwords are hashed with SHA-256 before storage/comparison (`PasswordUtils`).
- Prescription photos are only fetched for the requesting customer and responding pharmacies (`ActivityInquiryThread`).
- Location data is stored in `SharedPreferences` and used only for distance-based request filtering.
- Notification content never includes sensitive personal data beyond names and medicine names.

---

## 📄 License

MIT License — see `LICENSE` if present.
