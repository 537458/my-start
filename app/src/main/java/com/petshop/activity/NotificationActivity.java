package com.petshop.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.petshop.R;
import com.petshop.adapter.NotificationAdapter;
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
 * 消息中心：展示订单/优惠券/系统通知，支持全部已读与跳转订单详情
 */
public class NotificationActivity extends AppCompatActivity {

    private RecyclerView rvNotifications;
    private TextView tvEmpty;
    private NotificationAdapter adapter;
    private final List<ApiService.NotificationItem> notifications = new ArrayList<>();

    /** 单页条数，与后端 PageHelper 配合，滑到底部自动加载下一页 */
    private static final int PAGE_SIZE = 20;
    private int currentPage = 1;
    private boolean loading = false;
    private boolean hasMore = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        if (!AuthUtil.checkLoginWithPddStyle(this)) {
            finish();
            return;
        }

        initView();
        reloadNotifications();
    }

    private void initView() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvNotifications = findViewById(R.id.rv_notifications);
        tvEmpty = findViewById(R.id.tv_empty);
        findViewById(R.id.tv_read_all).setOnClickListener(v -> markAllRead());

        adapter = new NotificationAdapter(this, notifications, this::openNotification);
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        rvNotifications.setAdapter(adapter);

        // 滑到接近底部时自动加载下一页
        rvNotifications.addOnScrollListener(new RecyclerView.OnScrollListener() {
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
                    loadMoreNotifications();
                }
            }
        });
    }

    /** 重置分页并从第一页重新加载 */
    private void reloadNotifications() {
        currentPage = 1;
        hasMore = true;
        loading = false;
        notifications.clear();
        adapter.notifyDataSetChanged();
        loadNotifications();
    }

    /** 滑到底部时加载下一页 */
    private void loadMoreNotifications() {
        if (loading || !hasMore) {
            return;
        }
        currentPage++;
        loadNotifications();
    }

    private void loadNotifications() {
        if (loading || !hasMore) {
            return;
        }
        loading = true;
        final int page = currentPage;
        ApiClient.getInstance(this).getApiService().getNotifications(page, PAGE_SIZE)
                .enqueue(new Callback<ApiService.Response<List<ApiService.NotificationItem>>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<List<ApiService.NotificationItem>>> call,
                                           Response<ApiService.Response<List<ApiService.NotificationItem>>> response) {
                        loading = false;
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200 && response.body().getData() != null) {
                            List<ApiService.NotificationItem> pageData = response.body().getData();
                            if (pageData.size() < PAGE_SIZE) {
                                hasMore = false;
                            }
                            notifications.addAll(pageData);
                            adapter.notifyDataSetChanged();
                        } else {
                            if (page > 1) {
                                currentPage--; // 回退页码，允许重新触发加载
                            }
                            ToastUtil.showError(NotificationActivity.this, "消息加载失败");
                        }
                        updateEmptyState();
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<List<ApiService.NotificationItem>>> call, Throwable t) {
                        loading = false;
                        if (page > 1) {
                            currentPage--;
                        }
                        LogUtil.e("Notification", "消息加载失败", t);
                        ToastUtil.showError(NotificationActivity.this, "网络异常，请稍后重试");
                        updateEmptyState();
                    }
                });
    }

    /** 点击消息：先置为已读，订单类消息再跳转到订单详情 */
    private void openNotification(ApiService.NotificationItem item) {
        if (!item.isRead()) {
            item.setRead(true);
            adapter.notifyDataSetChanged();
            ApiClient.getInstance(this).getApiService().markNotificationRead(item.getId())
                    .enqueue(new Callback<ApiService.Response<Void>>() {
                        @Override
                        public void onResponse(Call<ApiService.Response<Void>> call,
                                               Response<ApiService.Response<Void>> response) {
                            // 已读状态先本地生效，失败不影响使用
                        }

                        @Override
                        public void onFailure(Call<ApiService.Response<Void>> call, Throwable t) {
                            LogUtil.w("Notification", "标记已读失败: " + t.getMessage());
                        }
                    });
        }
        if ("ORDER".equals(item.getType()) && item.getRelatedId() != null) {
            Intent intent = new Intent(this, OrderDetailActivity.class);
            intent.putExtra("orderId", item.getRelatedId());
            startActivity(intent);
        }
    }

    private void markAllRead() {
        if (notifications.isEmpty()) {
            return;
        }
        ApiClient.getInstance(this).getApiService().markAllNotificationsRead()
                .enqueue(new Callback<ApiService.Response<Void>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Void>> call,
                                           Response<ApiService.Response<Void>> response) {
                        for (ApiService.NotificationItem item : notifications) {
                            item.setRead(true);
                        }
                        adapter.notifyDataSetChanged();
                        ToastUtil.showSuccess(NotificationActivity.this, "已全部标记为已读");
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Void>> call, Throwable t) {
                        LogUtil.e("Notification", "全部已读失败", t);
                        ToastUtil.showError(NotificationActivity.this, "网络异常，请稍后重试");
                    }
                });
    }

    private void updateEmptyState() {
        boolean empty = notifications.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvNotifications.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
