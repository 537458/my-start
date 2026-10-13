package com.petshop.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.petshop.R;
import com.petshop.adapter.ReviewAdapter;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.util.LogUtil;
import com.petshop.util.ToastUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 商品全部评价：评分汇总 + 分页加载的评价列表
 */
public class ReviewListActivity extends AppCompatActivity {

    /** 单页条数，与后端 PageHelper 配合，滑到底部自动加载下一页 */
    private static final int PAGE_SIZE = 20;

    private RecyclerView rvReviews;
    private TextView tvSummary;
    private TextView tvEmpty;
    private ReviewAdapter adapter;
    private final List<ApiService.ProductReview> reviews = new ArrayList<>();

    private int productId;
    private int currentPage = 1;
    private boolean loading = false;
    private boolean hasMore = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review_list);

        productId = getIntent().getIntExtra("productId", -1);
        if (productId <= 0) {
            finish();
            return;
        }

        initView();
        loadSummary();
        reloadReviews();
    }

    private void initView() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        String productName = getIntent().getStringExtra("productName");
        if (productName != null && !productName.isEmpty()) {
            toolbar.setSubtitle(productName);
        }

        rvReviews = findViewById(R.id.rv_reviews);
        tvSummary = findViewById(R.id.tv_summary);
        tvEmpty = findViewById(R.id.tv_empty);

        adapter = new ReviewAdapter(this, reviews);
        rvReviews.setLayoutManager(new LinearLayoutManager(this));
        rvReviews.setAdapter(adapter);

        // 滑到接近底部时自动加载下一页
        rvReviews.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) {
                    return;
                }
                RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
                if (!(layoutManager instanceof LinearLayoutManager)) {
                    return;
                }
                int lastVisible = ((LinearLayoutManager) layoutManager).findLastVisibleItemPosition();
                if (lastVisible >= adapter.getItemCount() - 3) {
                    loadMoreReviews();
                }
            }
        });
    }

    private void loadSummary() {
        ApiClient.getInstance(this).getApiService().getProductReviewSummary(productId)
                .enqueue(new Callback<ApiService.Response<ApiService.ReviewSummary>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<ApiService.ReviewSummary>> call,
                                           Response<ApiService.Response<ApiService.ReviewSummary>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200 && response.body().getData() != null) {
                            ApiService.ReviewSummary summary = response.body().getData();
                            if (summary.getCount() <= 0) {
                                tvSummary.setText("暂无评价");
                            } else {
                                tvSummary.setText(String.format(Locale.CHINA,
                                        "%.1f 分 · %d 条评价", summary.getAvgRating(), summary.getCount()));
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<ApiService.ReviewSummary>> call, Throwable t) {
                        LogUtil.e("ReviewList", "加载评价汇总失败", t);
                    }
                });
    }

    /** 重置分页并从第一页重新加载 */
    private void reloadReviews() {
        currentPage = 1;
        hasMore = true;
        loading = false;
        reviews.clear();
        adapter.notifyDataSetChanged();
        loadReviews();
    }

    /** 滑到底部时加载下一页 */
    private void loadMoreReviews() {
        if (loading || !hasMore) {
            return;
        }
        currentPage++;
        loadReviews();
    }

    private void loadReviews() {
        if (loading || !hasMore) {
            return;
        }
        loading = true;
        final int page = currentPage;
        ApiClient.getInstance(this).getApiService().getProductReviews(productId, page, PAGE_SIZE)
                .enqueue(new Callback<ApiService.Response<List<ApiService.ProductReview>>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<List<ApiService.ProductReview>>> call,
                                           Response<ApiService.Response<List<ApiService.ProductReview>>> response) {
                        loading = false;
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200 && response.body().getData() != null) {
                            List<ApiService.ProductReview> pageData = response.body().getData();
                            if (pageData.size() < PAGE_SIZE) {
                                hasMore = false;
                            }
                            reviews.addAll(pageData);
                            adapter.notifyDataSetChanged();
                        } else {
                            if (page > 1) {
                                currentPage--; // 回退页码，允许重新触发加载
                            }
                            ToastUtil.showError(ReviewListActivity.this, "评价加载失败");
                        }
                        updateEmptyState();
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<List<ApiService.ProductReview>>> call, Throwable t) {
                        loading = false;
                        if (page > 1) {
                            currentPage--;
                        }
                        LogUtil.e("ReviewList", "评价加载失败", t);
                        ToastUtil.showError(ReviewListActivity.this, "网络异常，请稍后重试");
                        updateEmptyState();
                    }
                });
    }

    private void updateEmptyState() {
        boolean empty = reviews.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvReviews.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
