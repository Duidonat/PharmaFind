package com.example.pharmafind;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private conSQL sqlHelper;
    private UserSessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new UserSessionManager(this);
        if (sessionManager.isLoggedIn()) {
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
        setContentView(R.layout.activity_main);

        sqlHelper = new conSQL();

        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
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
    }

    private void showLoginDialog() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_login, null);
        bottomSheetDialog.setContentView(dialogView);

        EditText etUsername = dialogView.findViewById(R.id.etUsername);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);
        MaterialButton btnLoginSubmit = dialogView.findViewById(R.id.btnLoginSubmit);

        if (btnLoginSubmit != null) {
            btnLoginSubmit.setOnClickListener(v -> {
                String username = etUsername.getText().toString().trim();
                String password = etPassword.getText().toString().trim();

                if (username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show();
                    return;
                }

                performLogin(username, password, bottomSheetDialog);
            });
        }

        View parent = (View) dialogView.getParent();
        if (parent != null) {
            parent.setBackgroundResource(android.R.color.transparent);
        }

        bottomSheetDialog.show();
    }

    private void performLogin(String username, String password, BottomSheetDialog dialog) {
        Connection con = sqlHelper.conclass();
        if (con == null) {
            Toast.makeText(this, "Failed to connect to database. Check credentials in conSQL.java", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Check Customers table
            String customerQuery = "SELECT CustomerID, FirstName, Surname, City, Barangay, District FROM dbo.Customers WHERE Username = ? AND Password = ?";
            PreparedStatement customerStmt = con.prepareStatement(customerQuery);
            customerStmt.setString(1, username);
            customerStmt.setString(2, password);
            ResultSet customerRs = customerStmt.executeQuery();

            if (customerRs.next()) {
                int id = customerRs.getInt("CustomerID");
                String name = customerRs.getString("FirstName") + " " + customerRs.getString("Surname");
                String city = customerRs.getString("City");
                String barangay = customerRs.getString("Barangay");
                String district = customerRs.getString("District");
                if (city == null) city = "";
                if (barangay == null) barangay = "";
                if (district == null) district = "";

                sessionManager.createCustomerSession(id, username, name, city, barangay, district);

                Toast.makeText(this, "Welcome Customer: " + name, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                con.close();

                Intent intent = new Intent(MainActivity.this, CustomerHomeActivity.class);
                startActivity(intent);
                finish();
                return;
            }

            // Check Pharmacies table
            String pharmacyQuery = "SELECT PharmacyID, PharmacyName, City, Barangay, District, Status FROM dbo.Pharmacies WHERE Username = ? AND Password = ?";
            PreparedStatement pharmacyStmt = con.prepareStatement(pharmacyQuery);
            pharmacyStmt.setString(1, username);
            pharmacyStmt.setString(2, password);
            ResultSet pharmacyRs = pharmacyStmt.executeQuery();

            if (pharmacyRs.next()) {
                int id = pharmacyRs.getInt("PharmacyID");
                String name = pharmacyRs.getString("PharmacyName");
                String city = pharmacyRs.getString("City");
                String barangay = pharmacyRs.getString("Barangay");
                String district = pharmacyRs.getString("District");
                String status = pharmacyRs.getString("Status");
                if (city == null) city = "";
                if (barangay == null) barangay = "";
                if (district == null) district = "";
                if (status == null) status = "pending";

                sessionManager.createPharmacySession(id, username, name, city, barangay, district, status);

                Toast.makeText(this, "Welcome Pharmacy: " + name, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                con.close();

                Intent intent = new Intent(MainActivity.this, PharmacyHomeActivity.class);
                startActivity(intent);
                finish();
                return;
            }

            Toast.makeText(this, "Invalid Username or Password", Toast.LENGTH_SHORT).show();
            con.close();

        } catch (SQLException e) {
            Log.e("MainActivity", "Login Error: " + e.getMessage());
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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

            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            registerPharmacy(pharmacyName, city, barangay, district, email, contact, username, password, bottomSheetDialog);
        });

        View parent = (View) dialogView.getParent();
        if (parent != null) {
            parent.setBackgroundResource(android.R.color.transparent);
        }

        bottomSheetDialog.show();
    }

    private void registerPharmacy(String name, String city, String barangay, String district, String email, String contact, String username, String password, BottomSheetDialog dialog) {
        Connection con = sqlHelper.conclass();
        if (con == null) {
            Toast.makeText(this, "Database Connection Failed. Check credentials in conSQL.java", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String query = "INSERT INTO dbo.Pharmacies (PharmacyName, City, Barangay, District, Email, Contact, Username, Password, Status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending')";
            PreparedStatement stmt = con.prepareStatement(query);
            stmt.setString(1, name);
            stmt.setString(2, city);
            stmt.setString(3, barangay);
            stmt.setString(4, district);
            stmt.setString(5, email);
            stmt.setString(6, contact);
            stmt.setString(7, username);
            stmt.setString(8, password);

            int rows = stmt.executeUpdate();
            if (rows > 0) {
                Toast.makeText(this, "Pharmacy Registration Successful! Pending admin verification.", Toast.LENGTH_LONG).show();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Registration failed.", Toast.LENGTH_SHORT).show();
            }
            con.close();
        } catch (SQLException e) {
            Log.e("MainActivity", "Register Pharmacy Error: " + e.getMessage());
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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

            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            int age = ageStr.isEmpty() ? 0 : Integer.parseInt(ageStr);

            registerCustomer(firstName, surname, middleInitial, city, barangay, district, age, birthDate, email, contact, username, password, bottomSheetDialog);
        });

        View parent = (View) dialogView.getParent();
        if (parent != null) {
            parent.setBackgroundResource(android.R.color.transparent);
        }

        bottomSheetDialog.show();
    }

    private void registerCustomer(String firstName, String surname, String mi, String city, String barangay, String district, int age, String birthDate, String email, String contact, String username, String password, BottomSheetDialog dialog) {
        Connection con = sqlHelper.conclass();
        if (con == null) {
            Toast.makeText(this, "Database Connection Failed. Check credentials in conSQL.java", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String query = "INSERT INTO dbo.Customers (FirstName, Surname, MiddleInitial, City, Barangay, District, Age, BirthDate, Email, Contact, Username, Password) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
            stmt.setString(12, password);

            int rows = stmt.executeUpdate();
            if (rows > 0) {
                Toast.makeText(this, "Customer Registration Successful!", Toast.LENGTH_LONG).show();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Registration failed.", Toast.LENGTH_SHORT).show();
            }
            con.close();
        } catch (SQLException e) {
            Log.e("MainActivity", "Register Customer Error: " + e.getMessage());
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
