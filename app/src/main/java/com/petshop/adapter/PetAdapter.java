package com.petshop.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.petshop.R;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.util.GlideUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 我的宠物列表：一只宠物一张卡片，点卡片进编辑页
 */
public class PetAdapter extends RecyclerView.Adapter<PetAdapter.PetViewHolder> {

    public interface Listener {
        void onClick(ApiService.PetItem item);
    }

    private final Context context;
    private final List<ApiService.PetItem> items = new ArrayList<>();
    private final Listener listener;

    public PetAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItems(List<ApiService.PetItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_pet, parent, false);
        return new PetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PetViewHolder holder, int position) {
        ApiService.PetItem item = items.get(position);
        holder.tvName.setText(item.getName());

        String breed = item.getBreed() == null ? "" : item.getBreed().trim();
        holder.tvBreed.setText(breed.isEmpty()
                ? item.getSpeciesLabel()
                : item.getSpeciesLabel() + " · " + breed);

        String meta = buildMeta(item);
        holder.tvMeta.setText(meta);
        holder.tvMeta.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);

        if (item.isHasAvatar()) {
            GlideUtil.loadRounded(context, buildAvatarUrl(item), holder.ivAvatar);
        } else {
            // 没有照片时露出底部的渐变爪印占位
            holder.ivAvatar.setImageDrawable(null);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** 性别、年龄、体重，有哪项拼哪项 */
    private String buildMeta(ApiService.PetItem item) {
        StringBuilder builder = new StringBuilder();
        if (item.getGender() != null
                && !ApiService.PetItem.GENDER_UNKNOWN.equals(item.getGender())) {
            builder.append(item.getGenderLabel());
        }
        String age = formatAge(item.getBirthday());
        if (!age.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(age);
        }
        if (item.getWeight() != null && item.getWeight() > 0) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(trimZero(item.getWeight())).append("kg");
        }
        return builder.toString();
    }

    private String trimZero(double value) {
        String text = String.valueOf(Math.round(value * 100) / 100.0);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    /** 生日换算成「2岁3个月」这类文案，解析失败就不显示 */
    private String formatAge(String birthday) {
        if (birthday == null || birthday.trim().isEmpty()) {
            return "";
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            Date born = format.parse(birthday.trim());
            if (born == null) {
                return "";
            }
            long days = (System.currentTimeMillis() - born.getTime()) / (24L * 60 * 60 * 1000);
            if (days < 0) {
                return "";
            }
            int months = (int) (days / 30);
            if (months < 1) {
                return "不到1个月";
            }
            if (months < 12) {
                return months + "个月";
            }
            int years = months / 12;
            int rest = months % 12;
            return rest == 0 ? years + "岁" : years + "岁" + rest + "个月";
        } catch (Exception e) {
            return "";
        }
    }

    /** 头像地址带 updatedAt 版本号，换图后不会命中旧缓存 */
    private String buildAvatarUrl(ApiService.PetItem item) {
        String baseUrl = ApiClient.getCurrentBaseUrl(context);
        String url = (baseUrl == null ? "" : baseUrl) + "pets/" + item.getId() + "/avatar";
        String version = item.getUpdatedAt();
        if (version != null && !version.isEmpty()) {
            url = url + "?v=" + version.replace(' ', '_').replace(':', '-');
        }
        return url;
    }

    static class PetViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivAvatar;
        final TextView tvName;
        final TextView tvBreed;
        final TextView tvMeta;

        PetViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_pet_avatar);
            tvName = itemView.findViewById(R.id.tv_pet_name);
            tvBreed = itemView.findViewById(R.id.tv_pet_breed);
            tvMeta = itemView.findViewById(R.id.tv_pet_meta);
        }
    }
}
