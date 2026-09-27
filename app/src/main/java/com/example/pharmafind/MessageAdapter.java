package com.example.pharmafind;

import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private final List<MessageModel> messageList;
    private final int currentUserId;

    public MessageAdapter(List<MessageModel> messageList, int currentUserId) {
        this.messageList = messageList;
        this.currentUserId = currentUserId;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        MessageModel msg = messageList.get(position);

        holder.tvMessageText.setText(msg.getMessageText());
        holder.tvMessageTime.setText(msg.getCreatedAt() != null ? msg.getCreatedAt() : "");

        boolean isSentByMe = (msg.getSenderId() == currentUserId);

        if (isSentByMe) {
            holder.layoutBubbleContainer.setGravity(Gravity.END);
            holder.cardMessageBubble.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.primary_teal));
            holder.tvMessageText.setTextColor(Color.WHITE);
            holder.tvMessageTime.setTextColor(Color.parseColor("#B2DFDB"));
            holder.tvSenderName.setVisibility(View.GONE);
        } else {
            holder.layoutBubbleContainer.setGravity(Gravity.START);
            holder.cardMessageBubble.setCardBackgroundColor(Color.WHITE);
            holder.tvMessageText.setTextColor(Color.BLACK);
            holder.tvMessageTime.setTextColor(Color.GRAY);

            if (msg.getSenderName() != null && !msg.getSenderName().isEmpty()) {
                holder.tvSenderName.setText(msg.getSenderName());
                holder.tvSenderName.setVisibility(View.VISIBLE);
                holder.tvSenderName.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.primary_teal));
            } else {
                holder.tvSenderName.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return messageList != null ? messageList.size() : 0;
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutBubbleContainer;
        MaterialCardView cardMessageBubble;
        TextView tvSenderName, tvMessageText, tvMessageTime;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutBubbleContainer = itemView.findViewById(R.id.layoutBubbleContainer);
            cardMessageBubble = itemView.findViewById(R.id.cardMessageBubble);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            tvMessageText = itemView.findViewById(R.id.tvMessageText);
            tvMessageTime = itemView.findViewById(R.id.tvMessageTime);
        }
    }
}
