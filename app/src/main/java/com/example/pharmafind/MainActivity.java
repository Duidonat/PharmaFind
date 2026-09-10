package com.example.pharmafind;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    // REPLACE THIS with your actual Somee API URL (e.g., http://pharmafind.somee.com/register.php)
    private static final String BASE_URL = "http://pharmafind.somee.com/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
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
        
        View parent = (View) dialogView.getParent();
        if (parent != null) {
            parent.setBackgroundResource(android.R.color.transparent);
        }
        
        bottomSheetDialog.show();
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
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        EditText etContact = dialogView.findViewById(R.id.etContact);
        EditText etUsername = dialogView.findViewById(R.id.etUsername);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);
        EditText etConfirmPassword = dialogView.findViewById(R.id.etConfirmPassword);
        MaterialButton btnSubmit = dialogView.findViewById(R.id.btnSubmitRegisterPharmacy);

        btnSubmit.setOnClickListener(v -> {
            Map<String, String> params = new HashMap<>();
            params.put("type", "pharmacy");
            params.put("pharmacy_name", etPharmacyName.getText().toString());
            params.put("city", etCity.getText().toString());
            params.put("barangay", etBarangay.getText().toString());
            params.put("email", etEmail.getText().toString());
            params.put("contact", etContact.getText().toString());
            params.put("username", etUsername.getText().toString());
            params.put("password", etPassword.getText().toString());
            
            String confirm = etConfirmPassword.getText().toString();
            if (!params.get("password").equals(confirm)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            sendRegistrationData(params, bottomSheetDialog);
        });

        View parent = (View) dialogView.getParent();
        if (parent != null) {
            parent.setBackgroundResource(android.R.color.transparent);
        }

        bottomSheetDialog.show();
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
        EditText etAge = dialogView.findViewById(R.id.etAge);
        EditText etBirthDate = dialogView.findViewById(R.id.etBirthDate);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        EditText etContact = dialogView.findViewById(R.id.etContact);
        EditText etUsername = dialogView.findViewById(R.id.etUsername);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);
        EditText etConfirmPassword = dialogView.findViewById(R.id.etConfirmPassword);
        MaterialButton btnSubmit = dialogView.findViewById(R.id.btnSubmitRegister);

        btnSubmit.setOnClickListener(v -> {
            Map<String, String> params = new HashMap<>();
            params.put("type", "customer");
            params.put("first_name", etFirstName.getText().toString());
            params.put("surname", etSurname.getText().toString());
            params.put("middle_initial", etMiddleInitial.getText().toString());
            params.put("city", etCity.getText().toString());
            params.put("barangay", etBarangay.getText().toString());
            params.put("age", etAge.getText().toString());
            params.put("birth_date", etBirthDate.getText().toString());
            params.put("email", etEmail.getText().toString());
            params.put("contact", etContact.getText().toString());
            params.put("username", etUsername.getText().toString());
            params.put("password", etPassword.getText().toString());

            String confirm = etConfirmPassword.getText().toString();
            if (!params.get("password").equals(confirm)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            sendRegistrationData(params, bottomSheetDialog);
        });

        View parent = (View) dialogView.getParent();
        if (parent != null) {
            parent.setBackgroundResource(android.R.color.transparent);
        }

        bottomSheetDialog.show();
    }

    private void sendRegistrationData(Map<String, String> params, BottomSheetDialog dialogToDismiss) {
        RequestQueue queue = Volley.newRequestQueue(this);
        String url = BASE_URL + "register.php"; // Update with your actual server script name

        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                response -> {
                    Toast.makeText(MainActivity.this, "Registration Successful: " + response, Toast.LENGTH_LONG).show();
                    dialogToDismiss.dismiss();
                },
                error -> Toast.makeText(MainActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_LONG).show()) {
            @Override
            protected Map<String, String> getParams() {
                return params;
            }
        };

        queue.add(stringRequest);
    }
}