# PharmaFind - Android Application

PharmaFind is an Android application designed to connect customers with local pharmacies for medicine inquiries and availability tracking.

## Overview of Added Functions

### Authentication & Navigation
- **`showLoginDialog()`**: Opens a bottom sheet dialog where users can enter their username and password.
- **`performLogin(username, password, dialog)`**: Authenticates user credentials against the `dbo.Customers` and `dbo.Pharmacies` database tables, saves the session, and redirects users to their respective home activities.
- **`showRegisterSelectionDialog()`**: Displays a modal prompt allowing users to choose whether they want to register as a Customer or a Pharmacy.

### Customer Registration
- **`showRegisterCustomerDialog()`**: Displays the registration form for customers, including an interactive `DatePickerDialog` for selecting birth dates.
- **`registerCustomer(...)`**: Saves the customer profile details (name, address, age, birth date, contact information, and credentials) into the database.

### Pharmacy Registration
- **`showRegisterPharmacyDialog()`**: Displays the sign-up form for pharmacies to register their store details.
- **`registerPharmacy(...)`**: Registers a new pharmacy into the database with an initial `pending` status awaiting administrator approval.

### Session Management
- **`createCustomerSession(...)` & `createPharmacySession(...)`**: Persists user session parameters such as user ID, role, full name, address, and status in `SharedPreferences`.
- **`isLoggedIn()`, `getRole()`, and `logoutUser()`**: Handles user authentication state persistence across application launches and session termination.
