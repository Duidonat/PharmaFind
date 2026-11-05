package com.example.pharmafind;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.List;

public class InquiryAdapter extends RecyclerView.Adapter<InquiryAdapter.InquiryViewHolder> {

    private final List<InquiryModel> inquiryList;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(InquiryModel item);
    }

    public InquiryAdapter(List<InquiryModel> inquiryList, OnItemClickListener listener) {
        this.inquiryList = inquiryList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public InquiryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_inquiry_thread, parent, false);
        return new InquiryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull InquiryViewHolder holder, int position) {
        InquiryModel item = inquiryList.get(position);

        holder.tvCustomerName.setText(item.getCustomerName() != null ? item.getCustomerName() : "Anonymous");

        String location = item.getBarangay() != null && !item.getBarangay().trim().isEmpty()
                ? "Barangay " + item.getBarangay()
                : (item.getCity() != null ? item.getCity() : "");
        if (item.getDistanceKm() >= 0.0) {
            location += String.format(java.util.Locale.US, " (%.1f km away)", item.getDistanceKm());
        }
        String dateStr = item.getCreatedAt() != null ? item.getCreatedAt() : "";

        if (!location.isEmpty() && !dateStr.isEmpty()) {
            holder.tvLocationAndDate.setText(location + " • " + dateStr);
        } else {
            holder.tvLocationAndDate.setText(location.isEmpty() ? dateStr : location);
        }

        if (item.getStatus() != null && !item.getStatus().trim().isEmpty()) {
            holder.tvStatusBadge.setText(item.getStatus().toUpperCase());
            styleStatusBadge(holder.tvStatusBadge, item.getStatus());
            holder.tvStatusBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvStatusBadge.setVisibility(View.GONE);
        }

        holder.tvMedicineName.setText("Need: " + (item.getMedicineName() != null ? item.getMedicineName() : ""));
        holder.tvDescription.setText(item.getDescription() != null ? item.getDescription() : "");
        
        int replies = item.getReplyCount();
        if (replies == 1) {
            holder.tvReplyCount.setText("1 Pharmacy Responded");
        } else {
            holder.tvReplyCount.setText("" + replies + " Pharmacy Responses");
        }

        // Lists only show that a photo exists. The photo itself is loaded on the request screen,
        // and only for the requester and pharmacies (see ActivityInquiryThread).
        if (holder.tvPhotoAttached != null) {
            holder.tvPhotoAttached.setVisibility(item.hasImage() ? View.VISIBLE : View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });

        if (holder.btnViewThread != null) {
            holder.btnViewThread.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }
    }

    // Colors the status badge: green once a request is fulfilled, teal while it's open.
    // Also used by ActivityInquiryThread so the badge looks the same on both screens.
    static void styleStatusBadge(TextView badge, String status) {
        boolean fulfilled = "fulfilled".equalsIgnoreCase(status);
        badge.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(badge.getContext(),
                fulfilled ? R.color.success_container : R.color.teal_container)));
        badge.setTextColor(ContextCompat.getColor(badge.getContext(),
                fulfilled ? R.color.on_success_container : R.color.primary_dark));
    }

    @Override
    public int getItemCount() {
        return inquiryList != null ? inquiryList.size() : 0;
    }

    static class InquiryViewHolder extends RecyclerView.ViewHolder {
        TextView tvCustomerName, tvLocationAndDate, tvStatusBadge, tvMedicineName, tvDescription, tvReplyCount, tvPhotoAttached;
        ImageView ivUserAvatar;
        MaterialButton btnViewThread;

        public InquiryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCustomerName = itemView.findViewById(R.id.tvCustomerName);
            tvLocationAndDate = itemView.findViewById(R.id.tvLocationAndDate);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
            tvMedicineName = itemView.findViewById(R.id.tvMedicineName);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvReplyCount = itemView.findViewById(R.id.tvReplyCount);
            ivUserAvatar = itemView.findViewById(R.id.ivUserAvatar);
            tvPhotoAttached = itemView.findViewById(R.id.tvPhotoAttached);
            btnViewThread = itemView.findViewById(R.id.btnViewThread);
        }
    }
}
