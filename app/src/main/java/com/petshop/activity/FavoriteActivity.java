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
import com.petshop.adapter.FavoriteAdapter;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.util.AuthUtil;
import com.petshop.util.LogUtil;
import com.petshop.util.ToastUtil;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 我的收藏：展示收藏的商品，支持取消收藏与进入商品详情
 */
public class FavoriteActivity extends AppCompatActivity {

    private RecyclerView rvFavorites;
    private TextView tvEmpty;
    private FavoriteAdapter adapter;
    private final List<ApiService.FavoriteItem> favorites = new ArrayList<>();

    /** 单页条数，与后端 PageHelper 配合，滑到底部自动加载下一页 */
    private static final int PAGE_SIZE = 20;
    private int currentPage = 1;
    private boolean loading = false;
    private boolean hasMore = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite);

        if (!AuthUtil.checkLoginWithPddStyle(this)) {
            finish();
            return;
        }

        initView();
        reloadFavorites();
    }

    private void initView() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvFavorites = findViewById(R.id.rv_favorites);
        tvEmpty = findViewById(R.id.tv_empty);

        adapter = new FavoriteAdapter(this, favorites, this::removeFavorite);
        rvFavorites.setLayoutManager(new LinearLayoutManager(this));
        rvFavorites.setAdapter(adapter);

        // 滑到接近底部时自动加载下一页
        rvFavorites.addOnScrollListener(new RecyclerView.OnScrollListener() {
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
                    loadMoreFavorites();
                }
            }
        });
    }

    /** 重置分页并从第一页重新加载 */
    private void reloadFavorites() {
        currentPage = 1;
        hasMore = true;
        loading = false;
        favorites.clear();
        adapter.notifyDataSetChanged();
        loadFavorites();
    }

    /** 滑到底部时加载下一页 */
    private void loadMoreFavorites() {
        if (loading || !hasMore) {
            return;
        }
        currentPage++;
        loadFavorites();
    }

    private void loadFavorites() {
        if (loading || !hasMore) {
            return;
        }
        loading = true;
        final int page = currentPage;
        ApiClient.getInstance(this).getApiService().getFavorites(page, PAGE_SIZE)
                .enqueue(new Callback<ApiService.Response<List<ApiService.FavoriteItem>>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<List<ApiService.FavoriteItem>>> call,
                                           Response<ApiService.Response<List<ApiService.FavoriteItem>>> response) {
                        loading = false;
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200 && response.body().getData() != null) {
                            List<ApiService.FavoriteItem> pageData = response.body().getData();
                            if (pageData.size() < PAGE_SIZE) {
                                hasMore = false;
                            }
                            favorites.addAll(pageData);
                            adapter.notifyDataSetChanged();
                        } else {
                            if (page > 1) {
                                currentPage--; // 回退页码，允许重新触发加载
                            }
                            ToastUtil.showError(FavoriteActivity.this, "收藏列表加载失败");
                        }
                        updateEmptyState();
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<List<ApiService.FavoriteItem>>> call, Throwable t) {
                        loading = false;
                        if (page > 1) {
                            currentPage--;
                        }
                        LogUtil.e("Favorite", "收藏列表加载失败", t);
                        ToastUtil.showError(FavoriteActivity.this, "网络异常，请稍后重试");
                        updateEmptyState();
                    }
                });
    }

    private void removeFavorite(ApiService.FavoriteItem item) {
        ApiClient.getInstance(this).getApiService().removeFavorite(item.getProductId())
                .enqueue(new Callback<ApiService.Response<Boolean>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Boolean>> call,
                                           Response<ApiService.Response<Boolean>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                            favorites.remove(item);
                            adapter.notifyDataSetChanged();
                            updateEmptyState();
                            ToastUtil.showSuccess(FavoriteActivity.this, "已取消收藏");
                        } else {
                            ToastUtil.showError(FavoriteActivity.this, "取消收藏失败");
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Boolean>> call, Throwable t) {
                        LogUtil.e("Favorite", "取消收藏失败", t);
                        ToastUtil.showError(FavoriteActivity.this, "网络异常，请稍后重试");
                    }
                });
    }

    private void updateEmptyState() {
        boolean empty = favorites.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvFavorites.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
