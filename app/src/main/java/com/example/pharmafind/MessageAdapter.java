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
    private final String currentUserRole;
    private OnSatisfactionListener satisfactionListener;

    public interface OnSatisfactionListener {
        void onCustomerSatisfied(MessageModel message);
    }

    public MessageAdapter(List<MessageModel> messageList, int currentUserId, String currentUserRole) {
        this.messageList = messageList;
        this.currentUserId = currentUserId;
        this.currentUserRole = currentUserRole;
    }

    public void setOnSatisfactionListener(OnSatisfactionListener listener) {
        this.satisfactionListener = listener;
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

        boolean isSatisfactionPrompt = msg.getMessageText() != null &&
                msg.getMessageText().toLowerCase().contains("satisfied with my care");

        if (isSatisfactionPrompt && "customer".equalsIgnoreCase(currentUserRole) && !isSentByMe) {
            if (holder.layoutSatisfactionActions != null) {
                holder.layoutSatisfactionActions.setVisibility(View.VISIBLE);
                if (holder.btnSatisfiedYes != null) {
                    holder.btnSatisfiedYes.setOnClickListener(v -> {
                        if (satisfactionListener != null) {
                            satisfactionListener.onCustomerSatisfied(msg);
                        }
                    });
                }
                if (holder.btnSatisfiedNo != null) {
                    holder.btnSatisfiedNo.setOnClickListener(v -> {
                        holder.layoutSatisfactionActions.setVisibility(View.GONE);
                    });
                }
            }
        } else {
            if (holder.layoutSatisfactionActions != null) {
                holder.layoutSatisfactionActions.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return messageList != null ? messageList.size() : 0;
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutBubbleContainer, layoutSatisfactionActions;
        MaterialCardView cardMessageBubble;
        TextView tvSenderName, tvMessageText, tvMessageTime;
        com.google.android.material.button.MaterialButton btnSatisfiedYes, btnSatisfiedNo;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutBubbleContainer = itemView.findViewById(R.id.layoutBubbleContainer);
            cardMessageBubble = itemView.findViewById(R.id.cardMessageBubble);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            tvMessageText = itemView.findViewById(R.id.tvMessageText);
            tvMessageTime = itemView.findViewById(R.id.tvMessageTime);
            layoutSatisfactionActions = itemView.findViewById(R.id.layoutSatisfactionActions);
            btnSatisfiedYes = itemView.findViewById(R.id.btnSatisfiedYes);
            btnSatisfiedNo = itemView.findViewById(R.id.btnSatisfiedNo);
        }
    }
}
