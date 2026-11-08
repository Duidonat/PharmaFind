package com.example.pharmafind;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.widget.TextViewCompat;
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
        void onChatClick(PharmacyReplyModel reply);
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
            styleVerificationBadge(holder.tvPharmacyVerifiedBadge, "approved".equalsIgnoreCase(reply.getPharmacyStatus()), "Pending");
        }

        String locationDate = "";
        if (reply.getPharmacyBarangay() != null && !reply.getPharmacyBarangay().isEmpty()) {
            locationDate += reply.getPharmacyBarangay() + ", ";
        }
        if (reply.getPharmacyCity() != null && !reply.getPharmacyCity().isEmpty()) {
            locationDate += reply.getPharmacyCity() + " ";
        }
        if (reply.getDistanceKm() >= 0.0) {
            locationDate += String.format(java.util.Locale.US, "(%.1f km away) ", reply.getDistanceKm());
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

        if (canSelectPharmacy) {
            holder.btnContact.setVisibility(View.VISIBLE);
            holder.btnContact.setOnClickListener(v -> {
                if (listener != null) listener.onContactClick(reply);
            });

            if (holder.btnChat != null) {
                holder.btnChat.setVisibility(View.VISIBLE);
                holder.btnChat.setOnClickListener(v -> {
                    if (listener != null) listener.onChatClick(reply);
                });
            }

            if (holder.btnAcceptOffer != null) {
                holder.btnAcceptOffer.setVisibility(View.VISIBLE);
                holder.btnAcceptOffer.setOnClickListener(v -> {
                    if (listener != null) listener.onAcceptOfferClick(reply);
                });
            }
        } else {
            holder.btnContact.setVisibility(View.GONE);
            if (holder.btnChat != null) {
                holder.btnChat.setVisibility(View.GONE);
            }
            if (holder.btnAcceptOffer != null) {
                holder.btnAcceptOffer.setVisibility(View.GONE);
            }
        }
    }

    // Green "Verified" with a check badge, or amber "Pending" with an hourglass. Also used by
    // ActivityPharmacyProfile so the badge looks the same on both screens.
    static void styleVerificationBadge(TextView badge, boolean verified, String pendingLabel) {
        int color = android.graphics.Color.parseColor(verified ? "#2E7D32" : "#F57C00");
        badge.setText(verified ? "Verified" : pendingLabel);
        badge.setTextColor(color);
        badge.setCompoundDrawablesRelativeWithIntrinsicBounds(verified ? R.drawable.ic_verified_14 : R.drawable.ic_hourglass_empty_14, 0, 0, 0);
        TextViewCompat.setCompoundDrawableTintList(badge, ColorStateList.valueOf(color));
    }

    @Override
    public int getItemCount() {
        return replyList != null ? replyList.size() : 0;
    }

    static class ReplyViewHolder extends RecyclerView.ViewHolder {
        TextView tvPharmacyName, tvPharmacyLocationDate, tvAvailabilityBadge, tvPrice, tvNote, tvPharmacyVerifiedBadge;
        MaterialButton btnContact, btnChat, btnViewPharmacyProfile, btnAcceptOffer;

        public ReplyViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPharmacyName = itemView.findViewById(R.id.tvPharmacyName);
            tvPharmacyLocationDate = itemView.findViewById(R.id.tvPharmacyLocationDate);
            tvAvailabilityBadge = itemView.findViewById(R.id.tvAvailabilityBadge);
            tvPharmacyVerifiedBadge = itemView.findViewById(R.id.tvPharmacyVerifiedBadge);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvNote = itemView.findViewById(R.id.tvNote);
            btnContact = itemView.findViewById(R.id.btnContact);
            btnChat = itemView.findViewById(R.id.btnChatWithPharmacy);
            btnViewPharmacyProfile = itemView.findViewById(R.id.btnViewPharmacyProfile);
            btnAcceptOffer = itemView.findViewById(R.id.btnAcceptOffer);
        }
    }
}
