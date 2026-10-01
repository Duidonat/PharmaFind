package com.example.pharmafind;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.List;

public class PharmacyReplyAdapter extends RecyclerView.Adapter<PharmacyReplyAdapter.ReplyViewHolder> {

    private final List<PharmacyReplyModel> replyList;
    private final OnReplyActionListener listener;
    // Only the customer who posted this inquiry is allowed to select a pharmacy's offer.
    // Defaults to false (hidden) until the Activity confirms ownership.
    private boolean canSelectPharmacy = false;

    public interface OnReplyActionListener {
        void onViewProfileClick(PharmacyReplyModel reply);
        void onContactClick(PharmacyReplyModel reply);
        void onAcceptOfferClick(PharmacyReplyModel reply);
        void onReplyTextClick(PharmacyReplyModel reply);
    }

    public PharmacyReplyAdapter(List<PharmacyReplyModel> replyList, OnReplyActionListener listener) {
        this.replyList = replyList;
        this.listener = listener;
    }

    public void setCanSelectPharmacy(boolean canSelectPharmacy) {
        this.canSelectPharmacy = canSelectPharmacy;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReplyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pharmacy_reply, parent, false);
        return new ReplyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReplyViewHolder holder, int position) {
        PharmacyReplyModel reply = replyList.get(position);

        holder.tvPharmacyName.setText(reply.getPharmacyName() != null ? reply.getPharmacyName() : "Pharmacy");

        if (holder.tvPharmacyVerifiedBadge != null) {
            if ("approved".equalsIgnoreCase(reply.getPharmacyStatus())) {
                holder.tvPharmacyVerifiedBadge.setText("✓ Verified");
                holder.tvPharmacyVerifiedBadge.setTextColor(android.graphics.Color.parseColor("#2E7D32"));
            } else {
                holder.tvPharmacyVerifiedBadge.setText("⏳ Pending");
                holder.tvPharmacyVerifiedBadge.setTextColor(android.graphics.Color.parseColor("#F57C00"));
            }
        }

        String locationDate = "";
        if (reply.getPharmacyBarangay() != null && !reply.getPharmacyBarangay().isEmpty()) {
            locationDate += reply.getPharmacyBarangay() + ", ";
        }
        if (reply.getPharmacyCity() != null && !reply.getPharmacyCity().isEmpty()) {
            locationDate += reply.getPharmacyCity() + " ";
        }
        if (reply.getCreatedAt() != null && !reply.getCreatedAt().isEmpty()) {
            locationDate += "• " + reply.getCreatedAt();
        }
        holder.tvPharmacyLocationDate.setText(locationDate.trim());

        if (reply.getAvailabilityStatus() != null && !reply.getAvailabilityStatus().isEmpty()) {
            holder.tvAvailabilityBadge.setText(reply.getAvailabilityStatus().toUpperCase());
            holder.tvAvailabilityBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvAvailabilityBadge.setVisibility(View.GONE);
        }

        if (reply.getPrice() != null && !reply.getPrice().trim().isEmpty()) {
            holder.tvPrice.setText("Price: " + reply.getPrice());
            holder.tvPrice.setVisibility(View.VISIBLE);
        } else {
            holder.tvPrice.setVisibility(View.GONE);
        }

        if (reply.getNote() != null && !reply.getNote().trim().isEmpty()) {
            holder.tvNote.setText(reply.getNote());
            holder.tvNote.setVisibility(View.VISIBLE);
        } else {
            holder.tvNote.setVisibility(View.GONE);
        }

        View.OnClickListener textClickListener = v -> {
            if (listener != null) listener.onReplyTextClick(reply);
        };

        holder.tvPrice.setOnClickListener(textClickListener);
        holder.tvNote.setOnClickListener(textClickListener);
        holder.itemView.setOnClickListener(textClickListener);

        holder.btnViewPharmacyProfile.setOnClickListener(v -> {
            if (listener != null) listener.onViewProfileClick(reply);
        });

        holder.btnContact.setOnClickListener(v -> {
            if (listener != null) listener.onContactClick(reply);
        });

        if (holder.btnAcceptOffer != null) {
            if (canSelectPharmacy) {
                holder.btnAcceptOffer.setVisibility(View.VISIBLE);
                holder.btnAcceptOffer.setOnClickListener(v -> {
                    if (listener != null) listener.onAcceptOfferClick(reply);
                });
            } else {
                holder.btnAcceptOffer.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return replyList != null ? replyList.size() : 0;
    }

    static class ReplyViewHolder extends RecyclerView.ViewHolder {
        TextView tvPharmacyName, tvPharmacyLocationDate, tvAvailabilityBadge, tvPrice, tvNote, tvPharmacyVerifiedBadge;
        MaterialButton btnContact, btnViewPharmacyProfile, btnAcceptOffer;

        public ReplyViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPharmacyName = itemView.findViewById(R.id.tvPharmacyName);
            tvPharmacyLocationDate = itemView.findViewById(R.id.tvPharmacyLocationDate);
            tvAvailabilityBadge = itemView.findViewById(R.id.tvAvailabilityBadge);
            tvPharmacyVerifiedBadge = itemView.findViewById(R.id.tvPharmacyVerifiedBadge);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvNote = itemView.findViewById(R.id.tvNote);
            btnContact = itemView.findViewById(R.id.btnContact);
            btnViewPharmacyProfile = itemView.findViewById(R.id.btnViewPharmacyProfile);
            btnAcceptOffer = itemView.findViewById(R.id.btnAcceptOffer);
        }
    }
}
