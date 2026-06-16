package com.ftv.player.ui.browse;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ftv.player.R;
import com.ftv.player.data.model.Channel;

import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {

    private final List<Channel> channels;
    private final OnChannelClickListener listener;

    public interface OnChannelClickListener {
        void onChannelClick(Channel channel);
    }

    public ChannelAdapter(List<Channel> channels, OnChannelClickListener listener) {
        this.channels = channels;
        this.listener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_channel, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Channel channel = channels.get(position);
        holder.name.setText(channel.getName());
        holder.category.setText(channel.getCategory());

        if (channel.getLogoUrl() != null && !channel.getLogoUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(channel.getLogoUrl())
                    .placeholder(R.drawable.ic_launcher)
                    .error(R.drawable.ic_launcher)
                    .circleCrop()
                    .into(holder.logo);
        }

        holder.itemView.setOnClickListener(v -> listener.onChannelClick(channel));
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, category;
        ImageView logo;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.channel_name);
            category = itemView.findViewById(R.id.channel_category);
            logo = itemView.findViewById(R.id.channel_logo);
        }
    }
}
