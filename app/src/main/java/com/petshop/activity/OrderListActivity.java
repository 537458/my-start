package com.petshop.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.petshop.R;
import com.petshop.util.AuthUtil;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.util.ToastUtil;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrderListActivity extends AppCompatActivity {

    private RecyclerView rvOrderList;
    private List<ApiService.BackendOrder> orderList;
    private List<ApiService.BackendOrder> filteredList;
    private OrderAdapter adapter;
    private TextView tvAll, tvPending, tvShipping, tvCompleted, tvCanceled;
    private String currentFilter = "all"; // all, pending, shipping, completed, canceled

    /** 单页条数，与后端 PageHelper 配合，滑到底部自动加载下一页 */
    private static final int PAGE_SIZE = 10;
    private int currentPage = 1;
    private boolean loading = false;
    private boolean hasMore = true;
    /** 请求序号：切换页签后丢弃上一个页签的迟到响应 */
    private int requestSeq = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_list);

        if (!AuthUtil.checkLoginWithPddStyle(this)) {
            finish();
            return;
        }

        initView();
        initOrderList();
        // switchTab 内部会重置分页并发起首次请求
        switchTab(resolveInitialTab());
    }

    /**
     * 个人中心的“待付款 / 待发货 / 已完成 / 已取消”入口会携带 status，
     * 这里把它映射到对应的筛选标签，而不是永远回到“全部”。
     */
    private String resolveInitialTab() {
        String status = getIntent().getStringExtra("status");
        if (status == null) {
            return "all";
        }
        switch (status) {
            case "pending_payment":
                return "pending";
            case "pending_delivery":
            case "pending_receipt":
                return "shipping";
            case "completed":
                return "completed";
            case "canceled":
                return "canceled";
            default:
                return "all";
        }
    }

    private void initView() {
        rvOrderList = findViewById(R.id.rv_order_list);

        TextView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Tab初始化
        tvAll = findViewById(R.id.tv_all);
        tvPending = findViewById(R.id.tv_pending);
        tvShipping = findViewById(R.id.tv_shipping);
        tvCompleted = findViewById(R.id.tv_completed);
        tvCanceled = findViewById(R.id.tv_canceled);

        // Tab点击事件
        tvAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab("all");
            }
        });

        tvPending.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab("pending");
            }
        });

        tvShipping.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab("shipping");
            }
        });

        tvCompleted.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab("completed");
            }
        });

        tvCanceled.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab("canceled");
            }
        });
    }

    private void switchTab(String tab) {
        currentFilter = tab;

        // 更新Tab文字颜色
        int orangeColor = 0xFFFF9800;
        int grayColor = 0xFF757575;

        tvAll.setTextColor(tab.equals("all") ? orangeColor : grayColor);
        tvPending.setTextColor(tab.equals("pending") ? orangeColor : grayColor);
        tvShipping.setTextColor(tab.equals("shipping") ? orangeColor : grayColor);
        tvCompleted.setTextColor(tab.equals("completed") ? orangeColor : grayColor);
        tvCanceled.setTextColor(tab.equals("canceled") ? orangeColor : grayColor);

        // 切换页签：重置分页并重新从服务端拉取
        reloadOrders();
    }

    private void filterOrders() {
        filteredList = new ArrayList<>();

        if (orderList == null || orderList.isEmpty()) {
            adapter.notifyDataSetChanged();
            return;
        }

        for (ApiService.BackendOrder order : orderList) {
            String status = order.getStatus() != null ? order.getStatus() : "待付款";

            switch (currentFilter) {
                case "all":
                    filteredList.add(order);
                    break;
                case "pending":
                    if (status.equals("待付款")) {
                        filteredList.add(order);
                    }
                    break;
                case "shipping":
                    // 已付款（待发货）与已发货（待收货）都属于“未完结”订单
                    if (status.equals("已付款") || status.equals("已发货")) {
                        filteredList.add(order);
                    }
                    break;
                case "completed":
                    if (status.equals("已完成") || status.equals("已签收")) {
                        filteredList.add(order);
                    }
                    break;
                case "canceled":
                    if (status.equals("已取消")) {
                        filteredList.add(order);
                    }
                    break;
            }
        }

        adapter.notifyDataSetChanged();
    }

    private void initOrderList() {
        orderList = new ArrayList<>();
        filteredList = new ArrayList<>();
        adapter = new OrderAdapter();
        rvOrderList.setLayoutManager(new LinearLayoutManager(this));
        rvOrderList.setAdapter(adapter);

        // 滑到接近底部时自动加载下一页
        rvOrderList.addOnScrollListener(new RecyclerView.OnScrollListener() {
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
                    loadMoreOrders();
                }
            }
        });
    }

    /** 重置分页并从第一页重新加载 */
    private void reloadOrders() {
        currentPage = 1;
        hasMore = true;
        loading = false;
        requestSeq++;
        orderList.clear();
        filteredList.clear();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        loadOrdersFromApi();
    }

    /** 滑到底部时加载下一页 */
    private void loadMoreOrders() {
        if (loading || !hasMore) {
            return;
        }
        currentPage++;
        loadOrdersFromApi();
    }

    /** 当前页签对应的服务端状态过滤条件；null 表示不过滤 */
    private String statusOf(String filter) {
        switch (filter) {
            case "pending":
                return "待付款";
            case "shipping":
                return "已付款,已发货";
            case "completed":
                return "已完成,已签收";
            case "canceled":
                return "已取消";
            default:
                return null;
        }
    }

    private void loadOrdersFromApi() {
        if (loading || !hasMore) {
            return;
        }
        Log.d("OrderListActivity", "开始加载订单: page=" + currentPage + ", filter=" + currentFilter);
        Log.d("OrderListActivity", "当前用户ID: " + ApiClient.getInstance(this).getUserId());
        Log.d("OrderListActivity", "是否已登录: " + ApiClient.getInstance(this).isLoggedIn());
        Log.d("OrderListActivity", "API地址: " + ApiClient.getCurrentBaseUrl(this));
        
        // 检查登录状态
        if (!ApiClient.getInstance(this).isLoggedIn()) {
            Log.w("OrderListActivity", "用户未登录，无法加载订单");
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        
        loading = true;
        final int seq = requestSeq;
        ApiClient.getInstance(this).getApiService()
                .getOrders(currentPage, PAGE_SIZE, statusOf(currentFilter))
                .enqueue(new Callback<ApiService.Response<List<ApiService.BackendOrder>>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<List<ApiService.BackendOrder>>> call, Response<ApiService.Response<List<ApiService.BackendOrder>>> response) {
                        if (seq != requestSeq) {
                            return; // 页签已切换，丢弃过期响应
                        }
                        loading = false;
                        Log.d("OrderListActivity", "响应状态码: " + response.code());
                        if (response.isSuccessful()) {
                            Log.d("OrderListActivity", "请求成功");
                            if (response.body() != null) {
                                Log.d("OrderListActivity", "响应码: " + response.body().getCode());
                                Log.d("OrderListActivity", "响应消息: " + response.body().getMessage());
                                if (response.body().getCode() == 200) {
                                    List<ApiService.BackendOrder> orders = response.body().getData();
                                    Log.d("OrderListActivity", "获取到订单数量: " + (orders != null ? orders.size() : 0));
                                    if (orders != null && !orders.isEmpty()) {
                                        if (orders.size() < PAGE_SIZE) {
                                            hasMore = false;
                                        }
                                        orderList.addAll(orders);
                                        filterOrders(); // 加载完成后按页签再筛一次
                                    } else {
                                        Log.d("OrderListActivity", "本页没有更多订单");
                                        hasMore = false;
                                        if (currentPage > 1) {
                                            currentPage--;
                                        }
                                    }
                                } else {
                                    Log.d("OrderListActivity", "业务错误: " + response.body().getMessage());
                                    Toast.makeText(OrderListActivity.this, "加载失败: " + response.body().getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Log.d("OrderListActivity", "响应体为null");
                                Toast.makeText(OrderListActivity.this, "加载失败: 服务器响应异常", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.d("OrderListActivity", "请求失败，状态码: " + response.code());
                            Toast.makeText(OrderListActivity.this, "加载失败: 网络错误 " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<List<ApiService.BackendOrder>>> call, Throwable t) {
                        if (seq != requestSeq) {
                            return;
                        }
                        loading = false;
                        if (currentPage > 1) {
                            currentPage--; // 回退页码，允许重新触发加载
                        }
                        Log.e("OrderListActivity", "加载订单失败", t);
                        Toast.makeText(OrderListActivity.this, "加载失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {

        @NonNull
        @Override
        public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order, parent, false);
            return new OrderViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
            ApiService.BackendOrder order = filteredList.get(position);
            holder.tvOrderId.setText("订单号 " + (order.getOrderNo() != null ? order.getOrderNo() : "N/A"));

            StringBuilder itemsText = new StringBuilder();
            if (order.getItems() != null && !order.getItems().isEmpty()) {
                for (int i = 0; i < order.getItems().size(); i++) {
                    ApiService.OrderItemSimple item = order.getItems().get(i);
                    itemsText.append(item.getProductName());
                    if (item.getQuantity() > 1) {
                        itemsText.append(" x").append(item.getQuantity());
                    }
                    if (i < order.getItems().size() - 1) {
                        itemsText.append(", ");
                    }
                }
            } else {
                itemsText.append("暂无商品信息");
            }
            holder.tvOrderItems.setText(itemsText.toString());

            holder.tvOrderPrice.setText(String.format("¥%.2f", order.getTotalPrice()));

            String displayTime = order.getCreatedAt();
            if (displayTime != null && displayTime.contains("T")) {
                displayTime = displayTime.replace('T', ' ');
            }
            holder.tvOrderTime.setText("下单时间 " + (displayTime != null && displayTime.length() >= 16
                    ? displayTime.substring(0, 16) : "N/A"));

            // 支付渠道：未支付订单服务端不返回，直接隐藏，避免出现空白标签
            String payMethodLabel = order.getPayMethodLabel();
            if (payMethodLabel != null && !payMethodLabel.isEmpty()) {
                holder.tvOrderPayMethod.setText(payMethodLabel);
                holder.tvOrderPayMethod.setVisibility(View.VISIBLE);
            } else {
                holder.tvOrderPayMethod.setVisibility(View.GONE);
            }

            String status = order.getStatus() != null ? order.getStatus() : "待付款";
            String displayStatus = mapOrderStatus(status);
            holder.tvOrderStatus.setText(displayStatus);
            
            int statusColor;
            switch (displayStatus) {
                case "待付款":
                    statusColor = 0xFFFF9800;
                    break;
                case "已付款":
                case "已发货":
                    statusColor = 0xFF4CAF50;
                    break;
                case "已完成":
                    statusColor = 0xFF2196F3;
                    break;
                case "已取消":
                    statusColor = 0xFFF44336;
                    break;
                default:
                    statusColor = 0xFF757575;
                    break;
            }
            holder.tvOrderStatus.setTextColor(statusColor);

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent intent = new Intent(OrderListActivity.this, OrderDetailActivity.class);
                        intent.putExtra("orderId", order.getId());
                        intent.putExtra("orderNo", order.getOrderNo());
                        intent.putExtra("totalPrice", order.getTotalPrice());
                        intent.putExtra("status", order.getStatus());
                        intent.putExtra("payMethodLabel", order.getPayMethodLabel());
                        intent.putExtra("paidAt", order.getPaidAt());
                        intent.putExtra("createdAt", order.getCreatedAt());
                        intent.putExtra("receiver", order.getReceiver());
                        intent.putExtra("phone", order.getPhone());
                        intent.putExtra("address", order.getAddress());
                        
                        StringBuilder itemsText = new StringBuilder();
                        if (order.getItems() != null && !order.getItems().isEmpty()) {
                            for (ApiService.OrderItemSimple item : order.getItems()) {
                                if (itemsText.length() > 0) {
                                    itemsText.append("\n");
                                }
                                itemsText.append(item.getProductName())
                                        .append(" x")
                                        .append(item.getQuantity())
                                        .append("  ¥")
                                        .append(item.getPrice());
                            }
                        }
                        intent.putExtra("itemsText", itemsText.toString());
                        
                        startActivityForResult(intent, 1002);
                        Toast.makeText(OrderListActivity.this, "正在打开订单详情...", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        com.petshop.util.LogUtil.e("OrderListActivity", "启动订单详情失败", e);
                        ToastUtil.showError(OrderListActivity.this, "启动失败: " + e.getMessage());
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return filteredList.size();
        }

        class OrderViewHolder extends RecyclerView.ViewHolder {
            TextView tvOrderId;
            TextView tvOrderItems;
            TextView tvOrderPrice;
            TextView tvOrderTime;
            TextView tvOrderStatus;
            TextView tvOrderPayMethod;

            public OrderViewHolder(@NonNull View itemView) {
                super(itemView);
                tvOrderId = itemView.findViewById(R.id.tv_order_id);
                tvOrderItems = itemView.findViewById(R.id.tv_order_items);
                tvOrderPrice = itemView.findViewById(R.id.tv_order_price);
                tvOrderTime = itemView.findViewById(R.id.tv_order_time);
                tvOrderStatus = itemView.findViewById(R.id.tv_order_status);
                tvOrderPayMethod = itemView.findViewById(R.id.tv_order_pay_method);
            }
        }
    }
    
    /**
     * 订单状态映射方法
     * 服务器状态与展示状态保持一致：待付款 / 已付款 / 已发货 / 已完成 / 已取消
     * @param status 服务器返回的原始状态
     * @return 显示状态
     */
    private String mapOrderStatus(String status) {
        if (status == null || status.isEmpty()) {
            return "待付款";
        }
        return status;
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1002) {
            // 详情页可能发生了支付 / 取消 / 确认收货 / 评价，返回后统一刷新列表
            reloadOrders();
        }
    }
}
