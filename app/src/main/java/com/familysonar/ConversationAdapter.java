package com.familysonar;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {
    interface Listener {
        void onConversationClicked(ConversationPreview preview);
    }

    private final Context context;
    private final Listener listener;
    private final ArrayList<ConversationPreview> items = new ArrayList<>();

    ConversationAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    void submit(List<ConversationPreview> conversations) {
        items.clear();
        items.addAll(conversations);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context)
                .inflate(R.layout.item_conversation, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConversationPreview item = items.get(position);
        String displayName = ContactNameResolver.resolve(context, item.address);
        holder.name.setText(displayName);
        holder.preview.setText(item.preview);
        holder.date.setText(SmsRepository.formatDate(context, item.dateMillis));
        holder.avatar.setText(initialFor(displayName));
        if (item.unreadCount > 0) {
            holder.badge.setVisibility(View.VISIBLE);
            holder.badge.setText(String.valueOf(item.unreadCount));
        } else {
            holder.badge.setVisibility(View.GONE);
        }
        holder.itemView.setOnClickListener(view -> listener.onConversationClicked(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    ConversationPreview getItem(int position) {
        if (position < 0 || position >= items.size()) {
            return null;
        }
        return items.get(position);
    }

    private String initialFor(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "?";
        }
        return title.trim().substring(0, 1).toUpperCase(Locale.getDefault());
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView avatar;
        final TextView name;
        final TextView preview;
        final TextView date;
        final TextView badge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.avatarText);
            name = itemView.findViewById(R.id.conversationName);
            preview = itemView.findViewById(R.id.conversationPreview);
            date = itemView.findViewById(R.id.conversationDate);
            badge = itemView.findViewById(R.id.unreadBadge);
        }
    }
}
