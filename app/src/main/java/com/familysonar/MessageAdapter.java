package com.familysonar;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {
    private final Context context;
    private final ArrayList<ChatMessage> items = new ArrayList<>();

    MessageAdapter(Context context) {
        this.context = context;
    }

    void submit(List<ChatMessage> messages) {
        items.clear();
        items.addAll(messages);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context)
                .inflate(R.layout.item_message, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage item = items.get(position);
        LinearLayout.LayoutParams bodyParams = (LinearLayout.LayoutParams) holder.body.getLayoutParams();
        LinearLayout.LayoutParams metaParams = (LinearLayout.LayoutParams) holder.meta.getLayoutParams();
        if (item.outgoing) {
            holder.row.setGravity(Gravity.END);
            holder.body.setBackgroundResource(R.drawable.message_outgoing);
            holder.body.setTextColor(context.getColor(R.color.safe_on_primary));
            bodyParams.gravity = Gravity.END;
            metaParams.gravity = Gravity.END;
        } else {
            holder.row.setGravity(Gravity.START);
            holder.body.setBackgroundResource(R.drawable.message_incoming);
            holder.body.setTextColor(context.getColor(R.color.safe_on_surface));
            bodyParams.gravity = Gravity.START;
            metaParams.gravity = Gravity.START;
        }
        holder.body.setLayoutParams(bodyParams);
        holder.meta.setLayoutParams(metaParams);
        holder.body.setText(item.body);
        if (item.body == null || item.body.isEmpty()) {
            holder.body.setVisibility(View.GONE);
        } else {
            holder.body.setVisibility(View.VISIBLE);
        }
        if (item.mediaUri != null) {
            holder.media.setVisibility(View.VISIBLE);
            com.bumptech.glide.Glide.with(context)
                    .load(item.mediaUri)
                    .fitCenter()
                    .transform(new com.bumptech.glide.load.resource.bitmap.RoundedCorners(24))
                    .into(holder.media);
        } else {
            com.bumptech.glide.Glide.with(context).clear(holder.media);
            holder.media.setImageDrawable(null);
            holder.media.setVisibility(View.GONE);
        }
        holder.meta.setText(buildMeta(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String buildMeta(ChatMessage item) {
        String meta = SmsRepository.formatDate(context, item.dateMillis);
        if (item.mms) {
            return meta + " · MMS";
        }
        if (!item.outgoing) {
            return meta;
        }
        if (item.type == SmsRepository.TYPE_FAILED || item.status == SmsRepository.STATUS_FAILED) {
            return meta + " · failed";
        }
        if (item.status == SmsRepository.STATUS_COMPLETE) {
            return meta + " · delivered";
        }
        if (item.type == SmsRepository.TYPE_OUTBOX || item.status == SmsRepository.STATUS_PENDING) {
            return meta + " · sending";
        }
        return meta + " · sent";
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout row;
        final TextView body;
        final ImageView media;
        final TextView meta;

        ViewHolder(@NonNull android.view.View itemView) {
            super(itemView);
            row = itemView.findViewById(R.id.messageRow);
            body = itemView.findViewById(R.id.messageBody);
            media = itemView.findViewById(R.id.messageMedia);
            meta = itemView.findViewById(R.id.messageMeta);
        }
    }
}
