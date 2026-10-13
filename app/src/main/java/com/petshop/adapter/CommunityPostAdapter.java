package com.petshop.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.petshop.R;
import com.petshop.activity.ImagePreviewActivity;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.util.AvatarUtil;
import com.petshop.util.GlideUtil;

import java.util.List;

/**
 * 社区动态列表适配器：作者信息、正文、最多三张配图，以及点赞 / 评论 / 删除入口
 */
public class CommunityPostAdapter
        extends BaseAdapter<ApiService.CommunityPostItem, CommunityPostAdapter.PostViewHolder> {

    /** 单条动态最多展示的配图数量，与后端上限保持一致 */
    private static final int MAX_IMAGES = 6;
    /** 每行最多放几张图，超过 3 张时自动换行 */
    private static final int COLUMNS = 3;

    public interface Listener {
        void onLikeClick(ApiService.CommunityPostItem item, int position);

        void onCommentClick(ApiService.CommunityPostItem item);

        /** 自己的动态：编辑内容 / 改可见范围 / 删除 */
        void onMoreClick(ApiService.CommunityPostItem item, int position);
    }

    private final Listener listener;
    private final int imageSizePx;
    private final int imageGapPx;

    public CommunityPostAdapter(Context context, List<ApiService.CommunityPostItem> data,
                                Listener listener) {
        super(context, data);
        this.listener = listener;
        float density = context.getResources().getDisplayMetrics().density;
        this.imageSizePx = (int) (96 * density);
        this.imageGapPx = (int) (8 * density);
    }

    @Override
    public PostViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new PostViewHolder(inflateView(R.layout.item_community_post, parent));
    }

    @Override
    public void onBindViewHolder(PostViewHolder holder, int position) {
        ApiService.CommunityPostItem item = getItem(position);
        if (item == null) {
            return;
        }

        String userName = item.getUserName();
        holder.tvUserName.setText(userName == null || userName.isEmpty() ? "匿名用户" : userName);
        holder.tvTime.setText(formatTime(item.getCreatedAt()));
        holder.tvContent.setText(item.getContent());

        loadAvatar(holder.ivAvatar, item);

        // 仅自己可见的动态在列表里也打上标记，避免作者误以为已公开
        holder.tvPrivate.setVisibility(item.isPrivate() ? View.VISIBLE : View.GONE);
        holder.btnMore.setVisibility(item.isMine() ? View.VISIBLE : View.GONE);
        holder.btnMore.setOnClickListener(v -> {
            if (listener != null) {
                int index = holder.getAdapterPosition();
                listener.onMoreClick(item, index == RecyclerView.NO_POSITION ? position : index);
            }
        });

        holder.ivLike.setImageResource(item.isLiked() ? R.drawable.ic_liked : R.drawable.ic_like);
        holder.tvLike.setText(item.getLikeCount() > 0 ? String.valueOf(item.getLikeCount()) : "赞");
        holder.tvLike.setTextColor(context.getColor(
                item.isLiked() ? R.color.colorPrimary : R.color.colorTextSecondary));
        holder.tvComment.setText(item.getCommentCount() > 0
                ? String.valueOf(item.getCommentCount()) : "评论");

        holder.btnLike.setOnClickListener(v -> {
            if (listener != null) {
                int index = holder.getAdapterPosition();
                listener.onLikeClick(item, index == RecyclerView.NO_POSITION ? position : index);
            }
        });
        holder.btnComment.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCommentClick(item);
            }
        });

        renderImages(holder.llImages, item);
    }

    /** 配图由代码拼装：1～3 张排一行，4～6 张排成两行 */
    private void renderImages(LinearLayout container, ApiService.CommunityPostItem item) {
        container.removeAllViews();
        int count = Math.min(Math.max(item.getImageCount(), 0), MAX_IMAGES);
        if (count <= 0) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);
        container.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = null;
        for (int i = 0; i < count; i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    rowParams.topMargin = imageGapPx;
                }
                row.setLayoutParams(rowParams);
                container.addView(row);
            }
            ImageView image = new ImageView(context);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(imageSizePx, imageSizePx);
            if (i % COLUMNS != COLUMNS - 1) {
                params.setMarginEnd(imageGapPx);
            }
            image.setLayoutParams(params);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            final String imageUrl = buildImageUrl(item.getId(), i, item.getUpdatedAt());
            GlideUtil.loadRounded(context, imageUrl, image);
            // 点击配图全屏查看大图
            image.setOnClickListener(v -> ImagePreviewActivity.open(context, imageUrl));
            if (row != null) {
                row.addView(image);
            }
        }
    }

    private void loadAvatar(ImageView imageView, ApiService.CommunityPostItem item) {
        // 统一走 AvatarUtil：地址里带账号 ID 与版本号，换账号后不会命中上一个人的头像缓存
        AvatarUtil.loadUserAvatar(context, item.getUserId(), item.hasAvatar(), imageView);
    }

    private String baseUrl() {
        String baseUrl = ApiClient.getCurrentBaseUrl(context);
        return baseUrl == null ? "" : baseUrl;
    }

    private String buildImageUrl(int postId, int index, String version) {
        String url = baseUrl() + "community/posts/" + postId + "/images/" + index;
        // 配图改过后图片仍占同一个 index，用 updatedAt 当版本号，避免命中旧图缓存
        if (version != null && !version.isEmpty()) {
            url = url + "?v=" + version.replace(' ', '_').replace(':', '-');
        }
        return url;
    }

    /** 服务端返回的是 ISO 时间串，这里只展示到分钟 */
    private String formatTime(String createdAt) {
        if (createdAt == null || createdAt.isEmpty()) {
            return "";
        }
        String display = createdAt.replace('T', ' ');
        return display.length() >= 16 ? display.substring(0, 16) : display;
    }

    static class PostViewHolder extends BaseViewHolder {
        ImageView ivAvatar, btnMore, ivLike, ivComment;
        TextView tvUserName, tvTime, tvContent, tvLike, tvComment, tvPrivate;
        LinearLayout llImages, btnLike, btnComment;

        PostViewHolder(View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            btnMore = itemView.findViewById(R.id.btn_more);
            ivLike = itemView.findViewById(R.id.iv_like);
            ivComment = itemView.findViewById(R.id.iv_comment);
            tvUserName = itemView.findViewById(R.id.tv_user_name);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvPrivate = itemView.findViewById(R.id.tv_private);
            tvContent = itemView.findViewById(R.id.tv_content);
            tvLike = itemView.findViewById(R.id.tv_like);
            tvComment = itemView.findViewById(R.id.tv_comment);
            llImages = itemView.findViewById(R.id.ll_images);
            btnLike = itemView.findViewById(R.id.btn_like);
            btnComment = itemView.findViewById(R.id.btn_comment);
        }
    }
}
