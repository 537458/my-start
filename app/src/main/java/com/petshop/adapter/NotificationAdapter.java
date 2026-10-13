package com.petshop.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.petshop.R;
import com.petshop.api.ApiService;

import java.util.List;

/**
 * 站内消息列表适配器
 */
public class NotificationAdapter extends BaseAdapter<ApiService.NotificationItem, NotificationAdapter.NotificationViewHolder> {

    public interface OnNotificationClickListener {
        void onClick(ApiService.NotificationItem item);
    }

    private final OnNotificationClickListener listener;

    public NotificationAdapter(Context context, List<ApiService.NotificationItem> data,
                               OnNotificationClickListener listener) {
        super(context, data);
        this.listener = listener;
    }

    @Override
    public NotificationViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new NotificationViewHolder(inflateView(R.layout.item_notification, parent));
    }

    @Override
    public void onBindViewHolder(NotificationViewHolder holder, int position) {
        ApiService.NotificationItem item = getItem(position);
        if (item == null) {
            return;
        }
        holder.tvTitle.setText(item.getTitle());
        holder.tvContent.setText(item.getContent());
        holder.tvTime.setText(formatTime(item.getCreatedAt()));
        // 未读消息保留圆点，已读消息隐藏
        holder.vDot.setVisibility(item.isRead() ? View.INVISIBLE : View.VISIBLE);
        holder.tvTitle.setTextColor(context.getColor(
                item.isRead() ? R.color.colorTextSecondary : R.color.colorTextPrimary));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(item);
            }
        });
    }

    private String formatTime(String isoTime) {
        if (isoTime == null || isoTime.length() < 16) {
            return isoTime == null ? "" : isoTime;
        }
        // 2026-10-28T10:30:00 -> 10-28 10:30
        return isoTime.substring(5, 10) + " " + isoTime.substring(11, 16);
    }

    static class NotificationViewHolder extends BaseViewHolder {
        View vDot;
        TextView tvTitle, tvContent, tvTime;

        NotificationViewHolder(View itemView) {
            super(itemView);
            vDot = itemView.findViewById(R.id.v_dot);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvContent = itemView.findViewById(R.id.tv_content);
            tvTime = itemView.findViewById(R.id.tv_time);
        }
    }
}
