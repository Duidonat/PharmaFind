package com.example.pharmafind;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
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
        String dateStr = item.getCreatedAt() != null ? item.getCreatedAt() : "";

        if (!location.isEmpty() && !dateStr.isEmpty()) {
            holder.tvLocationAndDate.setText(location + " • " + dateStr);
        } else {
            holder.tvLocationAndDate.setText(location.isEmpty() ? dateStr : location);
        }

        if (item.getStatus() != null && !item.getStatus().trim().isEmpty()) {
            holder.tvStatusBadge.setText(item.getStatus().toUpperCase());
            holder.tvStatusBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvStatusBadge.setVisibility(View.GONE);
        }

        holder.tvMedicineName.setText("Need: " + (item.getMedicineName() != null ? item.getMedicineName() : ""));
        holder.tvDescription.setText(item.getDescription() != null ? item.getDescription() : "");
        
        int replies = item.getReplyCount();
        if (replies == 1) {
            holder.tvReplyCount.setText("💬 1 Pharmacy Responded");
        } else {
            holder.tvReplyCount.setText("💬 " + replies + " Pharmacy Responses");
        }

        // Display base64 decoded image if present
        if (item.getImageUrl() != null && !item.getImageUrl().trim().isEmpty()) {
            try {
                String imageStr = item.getImageUrl();
                if (imageStr.contains(",")) {
                    imageStr = imageStr.split(",")[1];
                }
                byte[] decodedBytes = Base64.decode(imageStr, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                if (bitmap != null) {
                    holder.ivInquiryImage.setImageBitmap(bitmap);
                    holder.ivInquiryImage.setVisibility(View.VISIBLE);
                } else {
                    holder.ivInquiryImage.setVisibility(View.GONE);
                }
            } catch (Exception e) {
                e.printStackTrace();
                holder.ivInquiryImage.setVisibility(View.GONE);
            }
        } else {
            holder.ivInquiryImage.setVisibility(View.GONE);
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

    @Override
    public int getItemCount() {
        return inquiryList != null ? inquiryList.size() : 0;
    }

    static class InquiryViewHolder extends RecyclerView.ViewHolder {
        TextView tvCustomerName, tvLocationAndDate, tvStatusBadge, tvMedicineName, tvDescription, tvReplyCount;
        ImageView ivUserAvatar, ivInquiryImage;
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
            ivInquiryImage = itemView.findViewById(R.id.ivInquiryImage);
            btnViewThread = itemView.findViewById(R.id.btnViewThread);
        }
    }
}
