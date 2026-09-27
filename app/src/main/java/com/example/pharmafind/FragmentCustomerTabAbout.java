package com.example.pharmafind;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class FragmentCustomerTabAbout extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_customer_tab_about, container, false);

        UserSessionManager session = new UserSessionManager(requireContext());

        TextView tvCityVal = view.findViewById(R.id.tvCityVal);
        TextView tvBarangayVal = view.findViewById(R.id.tvBarangayVal);
        TextView tvDistrictVal = view.findViewById(R.id.tvDistrictVal);
        TextView tvUsernameVal = view.findViewById(R.id.tvUsernameVal);

        tvCityVal.setText("City: " + session.getCity());
        tvBarangayVal.setText("Barangay: " + session.getBarangay());
        tvDistrictVal.setText("District / Purok: " + session.getDistrict());
        tvUsernameVal.setText("Username: " + session.getUsername());

        return view;
    }
}
