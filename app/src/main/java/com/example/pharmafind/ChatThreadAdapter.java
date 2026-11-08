package com.example.pharmafind;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ChatThreadAdapter extends RecyclerView.Adapter<ChatThreadAdapter.ChatThreadViewHolder> {

    private final List<ChatThreadModel> threadList;
    private final OnThreadClickListener listener;

    public interface OnThreadClickListener {
        void onThreadClick(ChatThreadModel item);
    }

    public ChatThreadAdapter(List<ChatThreadModel> threadList, OnThreadClickListener listener) {
        this.threadList = threadList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChatThreadViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_thread, parent, false);
        return new ChatThreadViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatThreadViewHolder holder, int position) {
        ChatThreadModel item = threadList.get(position);

        holder.tvPartnerName.setText(item.getPartnerName() != null && !item.getPartnerName().trim().isEmpty() ? item.getPartnerName() : "User");
        holder.tvLastMessage.setText(item.getLastMessage() != null && !item.getLastMessage().trim().isEmpty() ? item.getLastMessage() : "Tap to open chat");
        holder.tvTime.setText(item.getLastMessageTime() != null ? item.getLastMessageTime() : "");
        holder.tvMedicineTopic.setText("💊 Re: " + (item.getMedicineName() != null ? item.getMedicineName() : "Medicine Request"));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onThreadClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return threadList != null ? threadList.size() : 0;
    }

    static class ChatThreadViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvPartnerName, tvTime, tvLastMessage, tvMedicineTopic;

        public ChatThreadViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivChatAvatar);
            tvPartnerName = itemView.findViewById(R.id.tvChatPartnerName);
            tvTime = itemView.findViewById(R.id.tvChatTime);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvMedicineTopic = itemView.findViewById(R.id.tvMedicineTopic);
        }
    }
}
