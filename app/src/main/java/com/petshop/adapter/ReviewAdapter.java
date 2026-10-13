package com.petshop.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.petshop.R;
import com.petshop.api.ApiService;

import java.util.List;

/**
 * 商品评价列表适配器：展示评价人、星级、时间与内容
 */
public class ReviewAdapter extends BaseAdapter<ApiService.ProductReview, ReviewAdapter.ReviewViewHolder> {

    public ReviewAdapter(Context context, List<ApiService.ProductReview> data) {
        super(context, data);
    }

    @Override
    public ReviewViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ReviewViewHolder(inflateView(R.layout.item_review, parent));
    }

    @Override
    public void onBindViewHolder(ReviewViewHolder holder, int position) {
        ApiService.ProductReview review = getItem(position);
        if (review == null) {
            return;
        }
        String userName = review.getUserName();
        holder.tvUserName.setText(userName == null || userName.isEmpty() ? "匿名用户" : userName);

        String content = review.getContent();
        holder.tvContent.setText(content == null || content.isEmpty() ? "此用户没有填写评价内容" : content);

        holder.tvRating.setText(buildStars(review.getRating()));
        holder.tvDate.setText(formatTime(review.getCreatedAt()));
    }

    private String buildStars(int rating) {
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            stars.append(i < rating ? "★" : "☆");
        }
        return stars.toString();
    }

    /** 服务端返回的是 ISO 时间串，这里只展示到分钟 */
    private String formatTime(String createdAt) {
        if (createdAt == null || createdAt.isEmpty()) {
            return "";
        }
        String display = createdAt.replace('T', ' ');
        return display.length() >= 16 ? display.substring(0, 16) : display;
    }

    static class ReviewViewHolder extends BaseViewHolder {
        TextView tvUserName, tvDate, tvRating, tvContent;

        ReviewViewHolder(View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tv_user_name);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvRating = itemView.findViewById(R.id.tv_rating);
            tvContent = itemView.findViewById(R.id.tv_content);
        }
    }
}
