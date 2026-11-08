package com.example.pharmafind;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FragmentPharmacyProfile extends Fragment {

    private conSQL sqlHelper;
    private UserSessionManager session;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sqlHelper = new conSQL();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_pharmacy_profile, container, false);

        session = new UserSessionManager(requireContext());

        TextView tvPharmacyProfileName = view.findViewById(R.id.tvPharmacyProfileName);
        TextView tvPharmacyProfileLocation = view.findViewById(R.id.tvPharmacyProfileLocation);
        ImageButton btnPharmacyMenu = view.findViewById(R.id.btnPharmacyMenu);

        TabLayout tabLayout = view.findViewById(R.id.pharmacyProfileTabLayout);
        ViewPager2 viewPager = view.findViewById(R.id.pharmacyProfileViewPager);

        if (btnPharmacyMenu != null) {
            btnPharmacyMenu.setOnClickListener(v -> showVerificationDialog());
        }

        if (tvPharmacyProfileName != null) {
            String name = session.getFullName();
            tvPharmacyProfileName.setText(!name.trim().isEmpty() ? name : "Pharmacy Profile");
        }

        if (tvPharmacyProfileLocation != null) {
            String barangay = session.getBarangay();
            String city = session.getCity();
            if (!barangay.trim().isEmpty() || !city.trim().isEmpty()) {
                tvPharmacyProfileLocation.setText("Barangay " + barangay + ", " + city);
            } else {
                tvPharmacyProfileLocation.setText("Location Not Set");
            }
        }

        // Setup ViewPager Adapter for 2 Tabs
        viewPager.setAdapter(new PharmacyProfilePagerAdapter(this));

        // Connect TabLayout with ViewPager2
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText("About / Store Info");
            } else {
                tab.setText("My Responses");
            }
        }).attach();

        return view;
    }

    private void showVerificationDialog() {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_pharmacy_verification, null);
        dialog.setContentView(dialogView);

        ImageButton btnClose = dialogView.findViewById(R.id.btnClosePharmacyVerification);
        EditText etMayorsPermitNo = dialogView.findViewById(R.id.etMayorsPermitNo);
        EditText etFdaLicenseNo = dialogView.findViewById(R.id.etFdaLicenseNo);
        MaterialButton btnSubmit = dialogView.findViewById(R.id.btnSubmitPharmacyVerification);

        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                String permitNo = etMayorsPermitNo != null ? etMayorsPermitNo.getText().toString().trim() : "";
                String fdaNo = etFdaLicenseNo != null ? etFdaLicenseNo.getText().toString().trim() : "";

                if (permitNo.isEmpty()) {
                    UiMessage.show(dialog, "Please enter your Mayor's Business Permit No.");
                    return;
                }

                btnSubmit.setEnabled(false);
                String fullCredentials = "Mayor Permit: " + permitNo + (fdaNo.isEmpty() ? "" : " | FDA: " + fdaNo);
                saveVerificationCredentialsToDB(fullCredentials, dialog, btnSubmit);
            });
        }

        View parent = (View) dialogView.getParent();
        if (parent != null) {
            parent.setBackgroundResource(android.R.color.transparent);
        }

        dialog.show();
    }

    private void saveVerificationCredentialsToDB(String credentials, BottomSheetDialog dialog, MaterialButton btnSubmit) {
        executor.execute(() -> {
            Connection con = sqlHelper.conclass();
            if (con == null) {
                mainHandler.post(() -> {
                    if (btnSubmit != null) btnSubmit.setEnabled(true);
                    UiMessage.show(dialog, "Database connection error");
                });
                return;
            }

            try {
                String query = "UPDATE dbo.Pharmacies SET PermitDocumentUrl = ?, Status = 'approved' WHERE PharmacyID = ?";
                PreparedStatement stmt = con.prepareStatement(query);
                stmt.setString(1, credentials);
                stmt.setInt(2, session.getUserId());

                int rows = stmt.executeUpdate();
                con.close();

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    if (btnSubmit != null) btnSubmit.setEnabled(true);
                    if (rows > 0) {
                        session.setStatus("approved");
                        Toast.makeText(getContext(), "Verification credentials saved! Store verified.", Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    } else {
                        UiMessage.show(dialog, "Verification save failed");
                    }
                });

            } catch (SQLException e) {
                try { con.close(); } catch (SQLException ignored) {}
                Log.e("FragmentPharmacyProfile", "Error saving verification: " + e.getMessage());
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    if (btnSubmit != null) btnSubmit.setEnabled(true);
                    UiMessage.show(dialog, "Error: " + e.getMessage());
                });
            }
        });
    }

    private static class PharmacyProfilePagerAdapter extends FragmentStateAdapter {

        public PharmacyProfilePagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                return new FragmentPharmacyTabAbout();
            } else {
                return new FragmentPharmacyTabHistory();
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
