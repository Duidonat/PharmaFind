package com.example.pharmafind;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private conSQL sqlHelper;
    private UserSessionManager sessionManager;
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private BottomSheetDialog currentLoginDialog;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new UserSessionManager(this);
        if (sessionManager.isLoggedIn()) {
            NotificationScheduler.sync(this);
            if ("customer".equalsIgnoreCase(sessionManager.getRole())) {
                startActivity(new Intent(MainActivity.this, CustomerHomeActivity.class));
                finish();
                return;
            } else if ("pharmacy".equalsIgnoreCase(sessionManager.getRole())) {
                startActivity(new Intent(MainActivity.this, PharmacyHomeActivity.class));
                finish();
                return;
            }
        }

        EdgeToEdge.enable(this);
        // This screen is light, so use dark status/navigation bar icons. Otherwise they follow the
        // phone's dark mode and turn white, disappearing against the light background.
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(true);
        setContentView(R.layout.activity_main);

        sqlHelper = new conSQL();

        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            // Keep the layout's own padding (24dp) and add the status/navigation bar space on top of it.
            // Replacing it outright left the buttons touching the screen edges.
            int basePadding = mainView.getPaddingLeft();
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(basePadding + systemBars.left, basePadding + systemBars.top, basePadding + systemBars.right, basePadding + systemBars.bottom);
                return insets;
            });
        }

        // Initialize buttons
        MaterialButton btnLoginTop = findViewById(R.id.btnLoginTop);
        MaterialButton btnRegister = findViewById(R.id.btnRegister);
        MaterialButton btnGetStarted = findViewById(R.id.btnGetStarted);
        TextView tvLoginBottom = findViewById(R.id.tvLoginBottom);

        View.OnClickListener loginClickListener = v -> showLoginDialog();
        View.OnClickListener registerClickListener = v -> showRegisterSelectionDialog();

        if (btnLoginTop != null) btnLoginTop.setOnClickListener(loginClickListener);
        if (btnRegister != null) btnRegister.setOnClickListener(registerClickListener);
        if (btnGetStarted != null) btnGetStarted.setOnClickListener(loginClickListener);
        if (tvLoginBottom != null) tvLoginBottom.setOnClickListener(loginClickListener);

        // Configure Google Sign-In options with your Firebase Web Client ID
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("578469341951-ld77iuqeug6mt5s1b2u3q201mk59j9cd.apps.googleusercontent.com")
                .requestEmail()
                .requestProfile()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null) {
                            String email = account.getEmail();
                            String name = account.getDisplayName();
                            performGoogleAuthentication(email, name, currentLoginDialog);
                        }
                    } catch (ApiException e) {
                        Log.e("MainActivity", "Google Sign-In ApiException: " + e.getStatusCode());
                        UiMessage.show(currentLoginDialog, "Google Sign-In Account Selected: " + e.getMessage());
                    } catch (Exception e) {
                        Log.e("MainActivity", "Google Sign-In Exception: " + e.getMessage());
                        UiMessage.show(currentLoginDialog, "Google Sign-In error: " + e.getMessage());
                    }
                }
            }
        );
    }

    private void showLoginDialog() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_login, null);
        bottomSheetDialog.setContentView(dialogView);

        EditText etUsername = dialogView.findViewById(R.id.etUsername);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);
        android.widget.CheckBox cbRememberMe = dialogView.findViewById(R.id.cbRememberMe);
        MaterialButton btnLoginSubmit = dialogView.findViewById(R.id.btnLoginSubmit);
        MaterialButton btnGoogleSignIn = dialogView.findViewById(R.id.btnGoogleSignIn);
        TextView tvForgotPassword = dialogView.findViewById(R.id.tvForgotPassword);

        if (btnGoogleSignIn != null) {
            btnGoogleSignIn.setOnClickListener(v -> {
                currentLoginDialog = bottomSheetDialog;
                if (mGoogleSignInClient != null && googleSignInLauncher != null) {
                    Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                    googleSignInLauncher.launch(signInIntent);
                } else {
                    UiMessage.show(bottomSheetDialog, "Initializing Google Sign-In...");
                }
            });
        }

        if (tvForgotPassword != null) {
            tvForgotPassword.setOnClickListener(v -> {
                bottomSheetDialog.dismiss();
                showResetPasswordDialog();
            });
        }

        if (btnLoginSubmit != null) {
            btnLoginSubmit.setOnClickListener(v -> {
                String username = etUsername != null ? etUsername.getText().toString().trim() : "";
                String password = etPassword != null ? etPassword.getText().toString().trim() : "";

                if (username.isEmpty() || password.isEmpty()) {
                    UiMessage.show(bottomSheetDialog, "Please enter username and password");
                    return;
                }

                boolean rememberMe = cbRememberMe != null && cbRememberMe.isChecked();

                btnLoginSubmit.setEnabled(false);
                UiMessage.show(bottomSheetDialog, "Logging in...");
                performLogin(username, password, rememberMe, bottomSheetDialog, btnLoginSubmit);
            });
        }

        View parent = (View) dialogView.getParent();
        if (parent != null) {
            parent.setBackgroundResource(android.R.color.transparent);
        }

        bottomSheetDialog.show();
    }

    private void performLogin(String username, String password, boolean rememberMe, BottomSheetDialog dialog, MaterialButton btnSubmit) {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) {
                mainHandler.post(() -> {
                    if (btnSubmit != null) btnSubmit.setEnabled(true);
                    UiMessage.show(dialog, "Failed to connect to database. Please check connection.");
                });
                return;
            }

            try {
                String hashed = PasswordUtils.hashPassword(password);

                // Check Customers table
                String customerQuery = "SELECT CustomerID, FirstName, Surname, City, Barangay, District, Latitude, Longitude FROM dbo.Customers WHERE Username = ? AND (Password = ? OR Password = ?)";
                PreparedStatement customerStmt = con.prepareStatement(customerQuery);
                customerStmt.setString(1, username);
                customerStmt.setString(2, hashed);
                customerStmt.setString(3, password);
                ResultSet customerRs = customerStmt.executeQuery();

                if (customerRs.next()) {
                    int id = customerRs.getInt("CustomerID");
                    String name = customerRs.getString("FirstName") + " " + customerRs.getString("Surname");
                    String city = customerRs.getString("City");
                    String barangay = customerRs.getString("Barangay");
                    String district = customerRs.getString("District");
                    double lat = customerRs.getDouble("Latitude");
                    double lng = customerRs.getDouble("Longitude");
                    if (city == null) city = "";
                    if (barangay == null) barangay = "";
                    if (district == null) district = "";

                    sessionManager.createCustomerSession(id, username, name, city, barangay, district);
                    sessionManager.setLocationCoordinates(lat, lng);
                    sessionManager.setRememberMe(rememberMe);
                    NotificationScheduler.sync(MainActivity.this);
                    NotificationScheduler.schedulePeriodicCheck(MainActivity.this);

                    con.close();

                    mainHandler.post(() -> {
                        // Toast, not UiMessage: this screen closes right after, which would take a Snackbar with it.
                        Toast.makeText(MainActivity.this, "Welcome Customer: " + name, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        Intent intent = new Intent(MainActivity.this, CustomerHomeActivity.class);
                        startActivity(intent);
                        finish();
                    });
                    return;
                }

                // Check Pharmacies table
                String pharmacyQuery = "SELECT PharmacyID, PharmacyName, City, Barangay, District, Status, Latitude, Longitude FROM dbo.Pharmacies WHERE Username = ? AND (Password = ? OR Password = ?)";
                PreparedStatement pharmacyStmt = con.prepareStatement(pharmacyQuery);
                pharmacyStmt.setString(1, username);
                pharmacyStmt.setString(2, hashed);
                pharmacyStmt.setString(3, password);
                ResultSet pharmacyRs = pharmacyStmt.executeQuery();

                if (pharmacyRs.next()) {
                    int id = pharmacyRs.getInt("PharmacyID");
                    String name = pharmacyRs.getString("PharmacyName");
                    String city = pharmacyRs.getString("City");
                    String barangay = pharmacyRs.getString("Barangay");
                    String district = pharmacyRs.getString("District");
                    String status = pharmacyRs.getString("Status");
                    double lat = pharmacyRs.getDouble("Latitude");
                    double lng = pharmacyRs.getDouble("Longitude");
                    if (city == null) city = "";
                    if (barangay == null) barangay = "";
                    if (district == null) district = "";
                    if (status == null) status = "pending";

                    sessionManager.createPharmacySession(id, username, name, city, barangay, district, status);
                    sessionManager.setLocationCoordinates(lat, lng);
                    sessionManager.setRememberMe(rememberMe);
                    NotificationScheduler.sync(MainActivity.this);
                    NotificationScheduler.schedulePeriodicCheck(MainActivity.this);

                    con.close();

                    mainHandler.post(() -> {
                        // Toast, not UiMessage: this screen closes right after, which would take a Snackbar with it.
                        Toast.makeText(MainActivity.this, "Welcome Pharmacy: " + name, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        Intent intent = new Intent(MainActivity.this, PharmacyHomeActivity.class);
                        startActivity(intent);
                        finish();
                    });
                    return;
                }

                con.close();

                mainHandler.post(() -> {
                    if (btnSubmit != null) btnSubmit.setEnabled(true);
                    UiMessage.show(dialog, "Invalid Username or Password");
                });

            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
                Log.e("MainActivity", "Login Error: " + e.getMessage());
                mainHandler.post(() -> {
                    if (btnSubmit != null) btnSubmit.setEnabled(true);
                    UiMessage.show(dialog, "Error: " + e.getMessage());
                });
            }
        });
    }

    private void performGoogleAuthentication(String googleEmail, String googleName, BottomSheetDialog dialog) {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) {
                mainHandler.post(() -> UiMessage.show(dialog, "Database connection error"));
                return;
            }

            try {
                // Check Customers by Email
                String cSql = "SELECT CustomerID, Username, FirstName, Surname, City, Barangay, District, Latitude, Longitude FROM dbo.Customers WHERE Email = ? OR Username = ?";
                PreparedStatement cStmt = con.prepareStatement(cSql);
                cStmt.setString(1, googleEmail);
                cStmt.setString(2, googleEmail);
                ResultSet cRs = cStmt.executeQuery();

                if (cRs.next()) {
                    int id = cRs.getInt("CustomerID");
                    String username = cRs.getString("Username");
                    String name = cRs.getString("FirstName") + " " + cRs.getString("Surname");
                    String city = cRs.getString("City");
                    String barangay = cRs.getString("Barangay");
                    String district = cRs.getString("District");
                    double lat = cRs.getDouble("Latitude");
                    double lng = cRs.getDouble("Longitude");
                    if (city == null) city = "";
                    if (barangay == null) barangay = "";
                    if (district == null) district = "";

                    sessionManager.createCustomerSession(id, username != null ? username : googleEmail, name, city, barangay, district);
                    sessionManager.setLocationCoordinates(lat, lng);
                    sessionManager.setRememberMe(true);
                    NotificationScheduler.sync(MainActivity.this);

                    con.close();

                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this, "Google Sign-In: Welcome " + name, Toast.LENGTH_SHORT).show();
                        if (dialog != null) dialog.dismiss();
                        startActivity(new Intent(MainActivity.this, CustomerHomeActivity.class));
                        finish();
                    });
                    return;
                }

                // Check Pharmacies by Email
                String pSql = "SELECT PharmacyID, Username, PharmacyName, City, Barangay, District, Status, Latitude, Longitude FROM dbo.Pharmacies WHERE Email = ? OR Username = ?";
                PreparedStatement pStmt = con.prepareStatement(pSql);
                pStmt.setString(1, googleEmail);
                pStmt.setString(2, googleEmail);
                ResultSet pRs = pStmt.executeQuery();

                if (pRs.next()) {
                    int id = pRs.getInt("PharmacyID");
                    String username = pRs.getString("Username");
                    String name = pRs.getString("PharmacyName");
                    String city = pRs.getString("City");
                    String barangay = pRs.getString("Barangay");
                    String district = pRs.getString("District");
                    String status = pRs.getString("Status");
                    double lat = pRs.getDouble("Latitude");
                    double lng = pRs.getDouble("Longitude");
                    if (city == null) city = "";
                    if (barangay == null) barangay = "";
                    if (district == null) district = "";
                    if (status == null) status = "approved";

                    sessionManager.createPharmacySession(id, username != null ? username : googleEmail, name, city, barangay, district, status);
                    sessionManager.setLocationCoordinates(lat, lng);
                    sessionManager.setRememberMe(true);
                    NotificationScheduler.sync(MainActivity.this);

                    con.close();

                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this, "Google Sign-In: Welcome " + name, Toast.LENGTH_SHORT).show();
                        if (dialog != null) dialog.dismiss();
                        startActivity(new Intent(MainActivity.this, PharmacyHomeActivity.class));
                        finish();
                    });
                    return;
                }

                con.close();

                // If user doesn't exist yet, auto-register a new Customer account with Google Email
                mainHandler.post(() -> {
                    if (dialog != null) dialog.dismiss();
                    showRegisterCustomerDialog();
                    Toast.makeText(MainActivity.this, "Google Email verified (" + googleEmail + "). Complete your profile location to finish!", Toast.LENGTH_LONG).show();
                });

            } catch (SQLException e) {
                try { con.close(); } catch (SQLException ignored) {}
                Log.e("MainActivity", "Google Auth Error: " + e.getMessage());
            }
        });
    }

    private void showResetPasswordDialog() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_reset_password, null);
        bottomSheetDialog.setContentView(dialogView);

        EditText etResetUsername = dialogView.findViewById(R.id.etResetUsername);
        EditText etResetEmail = dialogView.findViewById(R.id.etResetEmail);
        EditText etResetNewPassword = dialogView.findViewById(R.id.etResetNewPassword);
        EditText etResetConfirmPassword = dialogView.findViewById(R.id.etResetConfirmPassword);
        MaterialButton btnSubmitReset = dialogView.findViewById(R.id.btnSubmitReset);

        if (btnSubmitReset != null) {
            btnSubmitReset.setOnClickListener(v -> {
                String username = etResetUsername.getText().toString().trim();
                String email = etResetEmail.getText().toString().trim();
                String newPassword = etResetNewPassword.getText().toString().trim();
                String confirmPassword = etResetConfirmPassword.getText().toString().trim();

                if (username.isEmpty() || email.isEmpty() || newPassword.isEmpty()) {
                    UiMessage.show(bottomSheetDialog, "Please fill in all fields");
                    return;
                }

                if (!newPassword.equals(confirmPassword)) {
                    UiMessage.show(bottomSheetDialog, "Passwords do not match");
                    return;
                }

                btnSubmitReset.setEnabled(false);
                UiMessage.show(bottomSheetDialog, "Checking your details...");
                resetPassword(username, email, newPassword, bottomSheetDialog, btnSubmitReset);
            });
        }

        View parent = (View) dialogView.getParent();
        if (parent != null) {
            parent.setBackgroundResource(android.R.color.transparent);
        }

        bottomSheetDialog.show();
    }

    private void resetPassword(String username, String email, String newPassword, BottomSheetDialog dialog, MaterialButton btnSubmit) {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) {
                mainHandler.post(() -> {
                    if (btnSubmit != null) btnSubmit.setEnabled(true);
                    UiMessage.show(dialog, "Failed to connect to database. Please check connection.");
                });
                return;
            }

            try {
                // Check Customers table first (same order as login)
                String findCustomerQuery = "SELECT CustomerID FROM dbo.Customers WHERE Username = ? AND Email = ?";
                PreparedStatement findCustomerStmt = con.prepareStatement(findCustomerQuery);
                findCustomerStmt.setString(1, username);
                findCustomerStmt.setString(2, email);
                ResultSet customerRs = findCustomerStmt.executeQuery();

                String hashedNewPassword = PasswordUtils.hashPassword(newPassword);

                if (customerRs.next()) {
                    int id = customerRs.getInt("CustomerID");
                    String updateQuery = "UPDATE dbo.Customers SET Password = ? WHERE CustomerID = ?";
                    PreparedStatement updateStmt = con.prepareStatement(updateQuery);
                    updateStmt.setString(1, hashedNewPassword);
                    updateStmt.setInt(2, id);
                    updateStmt.executeUpdate();

                    con.close();

                    mainHandler.post(() -> {
                        // Toast, not UiMessage: another sheet opens right after, which would hide a Snackbar.
                        Toast.makeText(MainActivity.this, "Password reset! Please log in with your new password.", Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                        showLoginDialog();
                    });
                    return;
                }

                // Check Pharmacies table
                String findPharmacyQuery = "SELECT PharmacyID FROM dbo.Pharmacies WHERE Username = ? AND Email = ?";
                PreparedStatement findPharmacyStmt = con.prepareStatement(findPharmacyQuery);
                findPharmacyStmt.setString(1, username);
                findPharmacyStmt.setString(2, email);
                ResultSet pharmacyRs = findPharmacyStmt.executeQuery();

                if (pharmacyRs.next()) {
                    int id = pharmacyRs.getInt("PharmacyID");
                    String updateQuery = "UPDATE dbo.Pharmacies SET Password = ? WHERE PharmacyID = ?";
                    PreparedStatement updateStmt = con.prepareStatement(updateQuery);
                    updateStmt.setString(1, hashedNewPassword);
                    updateStmt.setInt(2, id);
                    updateStmt.executeUpdate();

                    con.close();

                    mainHandler.post(() -> {
                        // Toast, not UiMessage: another sheet opens right after, which would hide a Snackbar.
                        Toast.makeText(MainActivity.this, "Password reset! Please log in with your new password.", Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                        showLoginDialog();
                    });
                    return;
                }

                con.close();

                mainHandler.post(() -> {
                    if (btnSubmit != null) btnSubmit.setEnabled(true);
                    UiMessage.showLong(dialog, "We couldn't verify those details. Check your username and email.");
                });

            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
                Log.e("MainActivity", "Reset Password Error: " + e.getMessage());
                mainHandler.post(() -> {
                    if (btnSubmit != null) btnSubmit.setEnabled(true);
                    UiMessage.show(dialog, "Error: " + e.getMessage());
                });
            }
        });
    }

    private void showRegisterSelectionDialog() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_register_selection, null);
        bottomSheetDialog.setContentView(dialogView);

        MaterialButton btnCustomer = dialogView.findViewById(R.id.btnRegisterCustomer);
        MaterialButton btnPharmacy = dialogView.findViewById(R.id.btnRegisterPharmacy);
        TextView tvLogin = dialogView.findViewById(R.id.tvLoginFromRegister);

        if (btnCustomer != null) {
            btnCustomer.setOnClickListener(v -> {
                bottomSheetDialog.dismiss();
                showRegisterCustomerDialog();
            });
        }

        if (btnPharmacy != null) {
            btnPharmacy.setOnClickListener(v -> {
                bottomSheetDialog.dismiss();
                showRegisterPharmacyDialog();
            });
        }

        if (tvLogin != null) {
            tvLogin.setOnClickListener(v -> {
                bottomSheetDialog.dismiss();
                showLoginDialog();
            });
        }

        View parent = (View) dialogView.getParent();
        if (parent != null) {
            parent.setBackgroundResource(android.R.color.transparent);
        }

        bottomSheetDialog.show();
    }

    private void showRegisterPharmacyDialog() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_register_pharmacy, null);
        bottomSheetDialog.setContentView(dialogView);

        EditText etPharmacyName = dialogView.findViewById(R.id.etPharmacyName);
        EditText etCity = dialogView.findViewById(R.id.etCity);
        EditText etBarangay = dialogView.findViewById(R.id.etBarangay);
        EditText etDistrict = dialogView.findViewById(R.id.etDistrict);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        EditText etContact = dialogView.findViewById(R.id.etContact);
        EditText etUsername = dialogView.findViewById(R.id.etUsername);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);
        EditText etConfirmPassword = dialogView.findViewById(R.id.etConfirmPassword);
        MaterialButton btnSubmit = dialogView.findViewById(R.id.btnSubmitRegisterPharmacy);

        TextView tvCheckLength = dialogView.findViewById(R.id.tvCheckLengthPharmacy);
        TextView tvCheckUppercase = dialogView.findViewById(R.id.tvCheckUppercasePharmacy);
        TextView tvCheckLowercase = dialogView.findViewById(R.id.tvCheckLowercasePharmacy);
        TextView tvCheckSpecial = dialogView.findViewById(R.id.tvCheckSpecialPharmacy);

        setupRealtimePasswordChecklist(etPassword, tvCheckLength, tvCheckUppercase, tvCheckLowercase, tvCheckSpecial);
        clearErrorsWhileTyping(etPharmacyName, etCity, etBarangay, etEmail, etContact, etUsername, etPassword, etConfirmPassword);

        btnSubmit.setOnClickListener(v -> {
            String pharmacyName = etPharmacyName.getText().toString().trim();
            String city = etCity.getText().toString().trim();
            String barangay = etBarangay.getText().toString().trim();
            String district = etDistrict != null ? etDistrict.getText().toString().trim() : "";
            String email = etEmail.getText().toString().trim();
            String contact = etContact.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();

            boolean valid = true;
            valid &= checkRequired(etPharmacyName, pharmacyName, "Enter the pharmacy name");
            valid &= checkRequired(etCity, city, "Enter your city");
            valid &= checkRequired(etBarangay, barangay, "Enter your barangay");
            valid &= checkContactAndAccount(etEmail, etContact, etUsername, etPassword, etConfirmPassword, email, contact, username, password, confirmPassword);
            if (!valid) {
                UiMessage.show(bottomSheetDialog, "Please fix the highlighted fields");
                scrollToFirstError(etPharmacyName, etCity, etBarangay, etEmail, etContact, etUsername, etPassword, etConfirmPassword);
                return;
            }

            btnSubmit.setEnabled(false);
            UiMessage.show(bottomSheetDialog, "Registering Pharmacy...");
            registerPharmacy(pharmacyName, city, barangay, district, email, contact, username, password, bottomSheetDialog, btnSubmit);
        });

        View parent = (View) dialogView.getParent();
        if (parent != null) {
            parent.setBackgroundResource(android.R.color.transparent);
        }

        bottomSheetDialog.show();
    }

    private void registerPharmacy(String name, String city, String barangay, String district, String email, String contact, String username, String password, BottomSheetDialog dialog, MaterialButton btnSubmit) {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) {
                mainHandler.post(() -> {
                    if (btnSubmit != null) btnSubmit.setEnabled(true);
                    UiMessage.show(dialog, "Database Connection Failed.");
                });
                return;
            }

            try {
                String hashedPassword = PasswordUtils.hashPassword(password);
                String query = "INSERT INTO dbo.Pharmacies (PharmacyName, City, Barangay, District, Email, Contact, Username, Password, Status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending')";
                PreparedStatement stmt = con.prepareStatement(query);
                stmt.setString(1, name);
                stmt.setString(2, city);
                stmt.setString(3, barangay);
                stmt.setString(4, district);
                stmt.setString(5, email);
                stmt.setString(6, contact);
                stmt.setString(7, username);
                stmt.setString(8, hashedPassword);

                int rows = stmt.executeUpdate();
                con.close();

                mainHandler.post(() -> {
                    if (rows > 0) {
                        UiMessage.showLong(MainActivity.this, "Pharmacy Registration Successful! Pending admin verification.");
                        dialog.dismiss();
                    } else {
                        if (btnSubmit != null) btnSubmit.setEnabled(true);
                        UiMessage.show(dialog, "Registration failed.");
                    }
                });
            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
                Log.e("MainActivity", "Register Pharmacy Error: " + e.getMessage());
                mainHandler.post(() -> {
                    if (btnSubmit != null) btnSubmit.setEnabled(true);
                    UiMessage.showLong(dialog, isDuplicateKeyError(e) ? "That username or email is already registered. Try another one." : "Error: " + e.getMessage());
                });
            }
        });
    }

    private void showRegisterCustomerDialog() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_register_customer, null);
        bottomSheetDialog.setContentView(dialogView);

        EditText etFirstName = dialogView.findViewById(R.id.etFirstName);
        EditText etSurname = dialogView.findViewById(R.id.etSurname);
        EditText etMiddleInitial = dialogView.findViewById(R.id.etMiddleInitial);
        EditText etCity = dialogView.findViewById(R.id.etCity);
        EditText etBarangay = dialogView.findViewById(R.id.etBarangay);
        EditText etDistrict = dialogView.findViewById(R.id.etDistrict);
        EditText etAge = dialogView.findViewById(R.id.etAge);
        EditText etBirthDate = dialogView.findViewById(R.id.etBirthDate);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        EditText etContact = dialogView.findViewById(R.id.etContact);
        EditText etUsername = dialogView.findViewById(R.id.etUsername);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);
        EditText etConfirmPassword = dialogView.findViewById(R.id.etConfirmPassword);
        MaterialButton btnSubmit = dialogView.findViewById(R.id.btnSubmitRegister);

        TextView tvCheckLength = dialogView.findViewById(R.id.tvCheckLength);
        TextView tvCheckUppercase = dialogView.findViewById(R.id.tvCheckUppercase);
        TextView tvCheckLowercase = dialogView.findViewById(R.id.tvCheckLowercase);
        TextView tvCheckSpecial = dialogView.findViewById(R.id.tvCheckSpecial);

        setupRealtimePasswordChecklist(etPassword, tvCheckLength, tvCheckUppercase, tvCheckLowercase, tvCheckSpecial);

        // Date Picker Calendar setup for Birth Date
        if (etBirthDate != null) {
            etBirthDate.setOnClickListener(v -> {
                Calendar calendar = Calendar.getInstance();
                int year = calendar.get(Calendar.YEAR) - 20; // Default to 20 years ago
                int month = calendar.get(Calendar.MONTH);
                int day = calendar.get(Calendar.DAY_OF_MONTH);

                DatePickerDialog datePickerDialog = new DatePickerDialog(
                    this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String formattedDate = String.format(Locale.US, "%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay);
                        etBirthDate.setText(formattedDate);
                    },
                    year, month, day
                );
                datePickerDialog.show();
            });
        }

        clearErrorsWhileTyping(etFirstName, etSurname, etAge, etBirthDate, etCity, etBarangay, etEmail, etContact, etUsername, etPassword, etConfirmPassword);

        btnSubmit.setOnClickListener(v -> {
            String firstName = etFirstName.getText().toString().trim();
            String surname = etSurname.getText().toString().trim();
            String middleInitial = etMiddleInitial.getText().toString().trim();
            String city = etCity.getText().toString().trim();
            String barangay = etBarangay.getText().toString().trim();
            String district = etDistrict != null ? etDistrict.getText().toString().trim() : "";
            String ageStr = etAge.getText().toString().trim();
            String birthDate = etBirthDate != null ? etBirthDate.getText().toString().trim() : "";
            String email = etEmail.getText().toString().trim();
            String contact = etContact.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();

            boolean valid = true;
            valid &= checkRequired(etFirstName, firstName, "Enter your first name");
            valid &= checkRequired(etSurname, surname, "Enter your surname");
            valid &= checkAge(etAge, ageStr);
            valid &= checkRequired(etBirthDate, birthDate, "Pick your birth date");
            valid &= checkRequired(etCity, city, "Enter your city");
            valid &= checkRequired(etBarangay, barangay, "Enter your barangay");
            valid &= checkContactAndAccount(etEmail, etContact, etUsername, etPassword, etConfirmPassword, email, contact, username, password, confirmPassword);
            if (!valid) {
                UiMessage.show(bottomSheetDialog, "Please fix the highlighted fields");
                scrollToFirstError(etFirstName, etSurname, etAge, etBirthDate, etCity, etBarangay, etEmail, etContact, etUsername, etPassword, etConfirmPassword);
                return;
            }

            int age = Integer.parseInt(ageStr);

            btnSubmit.setEnabled(false);
            UiMessage.show(bottomSheetDialog, "Registering Customer...");
            registerCustomer(firstName, surname, middleInitial, city, barangay, district, age, birthDate, email, contact, username, password, bottomSheetDialog, btnSubmit);
        });

        View parent = (View) dialogView.getParent();
        if (parent != null) {
            parent.setBackgroundResource(android.R.color.transparent);
        }

        bottomSheetDialog.show();
    }

    private void registerCustomer(String firstName, String surname, String mi, String city, String barangay, String district, int age, String birthDate, String email, String contact, String username, String password, BottomSheetDialog dialog, MaterialButton btnSubmit) {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) {
                mainHandler.post(() -> {
                    if (btnSubmit != null) btnSubmit.setEnabled(true);
                    UiMessage.show(dialog, "Database Connection Failed.");
                });
                return;
            }

            try {
                String hashedPassword = PasswordUtils.hashPassword(password);
                // DML COMMAND 1: INSERT (Adds a new record to dbo.Customers)
                String query = "INSERT INTO dbo.Customers (FirstName, Surname, MiddleInitial, City, " +
                        "Barangay, District, Age, BirthDate, Email, Contact, Username, Password) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                PreparedStatement stmt = con.prepareStatement(query);
                stmt.setString(1, firstName);
                stmt.setString(2, surname);
                stmt.setString(3, mi);
                stmt.setString(4, city);
                stmt.setString(5, barangay);
                stmt.setString(6, district);
                stmt.setInt(7, age);
                stmt.setString(8, birthDate);
                stmt.setString(9, email);
                stmt.setString(10, contact);
                stmt.setString(11, username);
                stmt.setString(12, hashedPassword);

                int rows = stmt.executeUpdate();
                con.close();

                mainHandler.post(() -> {
                    if (rows > 0) {
                        UiMessage.showLong(MainActivity.this, "Customer Registration Successful!");
                        dialog.dismiss();
                    } else {
                        if (btnSubmit != null) btnSubmit.setEnabled(true);
                        UiMessage.show(dialog, "Registration failed.");
                    }
                });
            } catch (SQLException e) {
                try {
                    con.close();
                } catch (SQLException ignored) {}
                Log.e("MainActivity", "Register Customer Error: " + e.getMessage());
                mainHandler.post(() -> {
                    if (btnSubmit != null) btnSubmit.setEnabled(true);
                    UiMessage.showLong(dialog, isDuplicateKeyError(e) ? "That username or email is already registered. Try another one." : "Error: " + e.getMessage());
                });
            }
        });
    }

    // Shows a red message under a field (the hidden TextView that follows it in the form) and a red outline.
    private void setFieldError(EditText field, String message) {
        if (field == null) return;
        field.setActivated(message != null);
        ViewGroup parent = (ViewGroup) field.getParent();
        View next = parent.getChildAt(parent.indexOfChild(field) + 1);
        if (next instanceof TextView && "fieldError".equals(next.getTag())) {
            ((TextView) next).setText(message != null ? message : "");
            next.setVisibility(message != null ? View.VISIBLE : View.GONE);
        }
    }

    // Removes a field's error as soon as the user starts fixing it.
    private void clearErrorsWhileTyping(EditText... fields) {
        for (EditText field : fields) {
            if (field == null) continue;
            field.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (field.isActivated()) setFieldError(field, null);
                }
            });
        }
    }

    // Scrolls the form so the first field with an error (and its message) is on screen.
    private void scrollToFirstError(EditText... fields) {
        for (EditText field : fields) {
            if (field != null && field.isActivated()) {
                field.post(() -> field.requestRectangleOnScreen(new Rect(0, 0, field.getWidth(), field.getHeight() + 80), false));
                return;
            }
        }
    }

    private boolean checkRequired(EditText field, String value, String message) {
        setFieldError(field, value.isEmpty() ? message : null);
        return !value.isEmpty();
    }

    private boolean checkAge(EditText field, String ageStr) {
        String error = null;
        if (ageStr.isEmpty()) {
            error = "Enter your age";
        } else {
            try {
                int age = Integer.parseInt(ageStr);
                if (age < 1 || age > 120) error = "Enter a valid age";
            } catch (NumberFormatException e) {
                error = "Enter a valid age";
            }
        }
        setFieldError(field, error);
        return error == null;
    }

    private void setupRealtimePasswordChecklist(EditText etPassword, TextView tvLength, TextView tvUpper, TextView tvLower, TextView tvSpecial) {
        if (etPassword == null) return;
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String pass = s.toString();
                boolean hasLength = pass.length() >= 8;
                boolean hasUpper = pass.matches(".*[A-Z].*");
                boolean hasLower = pass.matches(".*[a-z].*");
                boolean hasSpecial = pass.matches(".*[0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");

                if (tvLength != null) {
                    tvLength.setText((hasLength ? "✓" : "❌") + " At least 8 characters");
                    tvLength.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, hasLength ? R.color.on_success_container : R.color.error_red));
                }
                if (tvUpper != null) {
                    tvUpper.setText((hasUpper ? "✓" : "❌") + " At least 1 uppercase letter (A-Z)");
                    tvUpper.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, hasUpper ? R.color.on_success_container : R.color.error_red));
                }
                if (tvLower != null) {
                    tvLower.setText((hasLower ? "✓" : "❌") + " At least 1 lowercase letter (a-z)");
                    tvLower.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, hasLower ? R.color.on_success_container : R.color.error_red));
                }
                if (tvSpecial != null) {
                    tvSpecial.setText((hasSpecial ? "✓" : "❌") + " At least 1 number or special character (!@#$)");
                    tvSpecial.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, hasSpecial ? R.color.on_success_container : R.color.error_red));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    // Email, contact number, username and password (with confirmation): same rules for customers and pharmacies.
    private boolean checkContactAndAccount(EditText etEmail, EditText etContact, EditText etUsername, EditText etPassword, EditText etConfirmPassword,
                                           String email, String contact, String username, String password, String confirmPassword) {
        boolean ok = true;

        String emailError = null;
        if (email.isEmpty()) emailError = "Enter your email";
        else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) emailError = "Enter a valid email address";
        setFieldError(etEmail, emailError);
        ok &= emailError == null;

        int digits = contact.replaceAll("[^0-9]", "").length();
        String contactError = null;
        if (contact.isEmpty()) contactError = "Enter your contact number";
        else if (!contact.matches("[0-9+\\-\\s()]+") || digits < 7 || digits > 15) contactError = "Enter a valid phone number";
        setFieldError(etContact, contactError);
        ok &= contactError == null;

        String usernameError = null;
        if (username.isEmpty()) usernameError = "Choose a username";
        else if (username.length() < 4) usernameError = "Use at least 4 characters";
        else if (username.matches(".*\\s.*")) usernameError = "Spaces aren't allowed";
        setFieldError(etUsername, usernameError);
        ok &= usernameError == null;

        String passwordError = null;
        boolean hasLength = password.length() >= 8;
        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasSpecial = password.matches(".*[0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");

        if (password.isEmpty()) passwordError = "Choose a password";
        else if (!hasLength || !hasUpper || !hasLower || !hasSpecial) {
            passwordError = "Password must satisfy all 4 checklist criteria above";
        }
        setFieldError(etPassword, passwordError);
        ok &= passwordError == null;

        String confirmError = null;
        if (confirmPassword.isEmpty()) confirmError = "Re-enter your password";
        else if (!confirmPassword.equals(password)) confirmError = "Passwords do not match";
        setFieldError(etConfirmPassword, confirmError);
        ok &= confirmError == null;

        return ok;
    }

    // SQL Server error 2627/2601 = a UNIQUE constraint was hit (username or email already exists).
    private boolean isDuplicateKeyError(SQLException e) {
        return e.getErrorCode() == 2627 || e.getErrorCode() == 2601
                || (e.getMessage() != null && e.getMessage().toLowerCase(Locale.US).contains("duplicate key"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
