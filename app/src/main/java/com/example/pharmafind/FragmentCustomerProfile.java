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

public class FragmentCustomerProfile extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_customer_profile, container, false);

        UserSessionManager session = new UserSessionManager(requireContext());

        TextView tvProfileName = view.findViewById(R.id.tvProfileName);
        TextView tvProfileLocation = view.findViewById(R.id.tvProfileLocation);
        TabLayout tabLayout = view.findViewById(R.id.profileTabLayout);
        ViewPager2 viewPager = view.findViewById(R.id.profileViewPager);

        tvProfileName.setText(session.getFullName());
        tvProfileLocation.setText("Barangay " + session.getBarangay() + ", " + session.getCity());

        // Setup ViewPager Adapter for 2 Tabs
        viewPager.setAdapter(new ProfilePagerAdapter(this));

        // Connect TabLayout with ViewPager2
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText("About / Personal Info");
            } else {
                tab.setText("My Requests");
            }
        }).attach();

        return view;
    }

    private static class ProfilePagerAdapter extends FragmentStateAdapter {

        public ProfilePagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                return new FragmentCustomerTabAbout();
            } else {
                return new FragmentCustomerTabHistory();
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}
