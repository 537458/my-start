package com.petshop.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.petshop.R;
import com.petshop.api.ApiService;
import com.petshop.util.AvatarUtil;

import java.util.List;

/**
 * 社区评论列表适配器：评论人头像、昵称、时间与内容
 */
public class CommunityCommentAdapter
        extends BaseAdapter<ApiService.CommunityCommentItem, CommunityCommentAdapter.CommentViewHolder> {

    public CommunityCommentAdapter(Context context, List<ApiService.CommunityCommentItem> data) {
        super(context, data);
    }

    @Override
    public CommentViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new CommentViewHolder(inflateView(R.layout.item_community_comment, parent));
    }

    @Override
    public void onBindViewHolder(CommentViewHolder holder, int position) {
        ApiService.CommunityCommentItem item = getItem(position);
        if (item == null) {
            return;
        }
        String userName = item.getUserName();
        holder.tvUserName.setText(userName == null || userName.isEmpty() ? "匿名用户" : userName);
        holder.tvContent.setText(item.getContent());
        holder.tvTime.setText(formatTime(item.getCreatedAt()));

        // 统一走 AvatarUtil：地址里带账号 ID 与版本号，换账号后不会命中上一个人的头像缓存
        AvatarUtil.loadUserAvatar(context, item.getUserId(), item.hasAvatar(), holder.ivAvatar);
    }

    /** 服务端返回的是 ISO 时间串，这里只展示到分钟 */
    private String formatTime(String createdAt) {
        if (createdAt == null || createdAt.isEmpty()) {
            return "";
        }
        String display = createdAt.replace('T', ' ');
        return display.length() >= 16 ? display.substring(0, 16) : display;
    }

    static class CommentViewHolder extends BaseViewHolder {
        ImageView ivAvatar;
        TextView tvUserName, tvTime, tvContent;

        CommentViewHolder(View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvUserName = itemView.findViewById(R.id.tv_user_name);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvContent = itemView.findViewById(R.id.tv_content);
        }
    }
}
