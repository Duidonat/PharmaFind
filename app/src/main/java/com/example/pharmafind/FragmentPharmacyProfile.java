package com.example.pharmafind;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class FragmentPharmacyProfile extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_pharmacy_profile, container, false);

        UserSessionManager session = new UserSessionManager(requireContext());

        TextView tvPharmacyProfileName = view.findViewById(R.id.tvPharmacyProfileName);
        TextView tvPharmacyProfileLocation = view.findViewById(R.id.tvPharmacyProfileLocation);
        TabLayout tabLayout = view.findViewById(R.id.pharmacyProfileTabLayout);
        ViewPager2 viewPager = view.findViewById(R.id.pharmacyProfileViewPager);

        if (tvPharmacyProfileName != null) {
            String name = session.getFullName();
            tvPharmacyProfileName.setText(!name.trim().isEmpty() ? name : "Pharmacy Profile");
        }

        if (tvPharmacyProfileLocation != null) {
            String barangay = session.getBarangay();
            String city = session.getCity();
            if (!barangay.trim().isEmpty() || !city.trim().isEmpty()) {
                tvPharmacyProfileLocation.setText("📍 Barangay " + barangay + ", " + city);
            } else {
                tvPharmacyProfileLocation.setText("📍 Location Not Set");
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
}
