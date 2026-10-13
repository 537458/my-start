package com.petshop.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.petshop.R;
import com.petshop.activity.IntroActivity;

import java.util.List;

/**
 * 引导页适配器
 * 用于显示引导页的内容
 */
public class IntroAdapter extends RecyclerView.Adapter<IntroAdapter.IntroViewHolder> {

    private List<IntroActivity.IntroItem> introItems;

    public IntroAdapter(List<IntroActivity.IntroItem> introItems) {
        this.introItems = introItems;
    }

    @NonNull
    @Override
    public IntroViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_intro, parent, false);
        return new IntroViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull IntroViewHolder holder, int position) {
        IntroActivity.IntroItem item = introItems.get(position);
        // 每页用不同色调的渐变圆底，比统一的灰色占位图更有层次
        holder.flBadge.setBackgroundResource(item.getBadgeResId());
        holder.ivImage.setImageResource(item.getImageResId());
        holder.tvTitle.setText(item.getTitle());
        holder.tvDescription.setText(item.getDescription());
    }

    @Override
    public int getItemCount() {
        return introItems.size();
    }

    static class IntroViewHolder extends RecyclerView.ViewHolder {
        FrameLayout flBadge;
        ImageView ivImage;
        TextView tvTitle;
        TextView tvDescription;

        public IntroViewHolder(@NonNull View itemView) {
            super(itemView);
            flBadge = itemView.findViewById(R.id.fl_badge);
            ivImage = itemView.findViewById(R.id.iv_image);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvDescription = itemView.findViewById(R.id.tv_description);
        }
    }
}
