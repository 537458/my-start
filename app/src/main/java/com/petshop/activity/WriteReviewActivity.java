package com.petshop.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.petshop.R;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.util.LogUtil;
import com.petshop.util.ToastUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 订单评价页：对订单内所有商品提交同一评分与评价内容。
 * 一单只能评价一次，成功后关闭并通知订单详情刷新。
 */
public class WriteReviewActivity extends AppCompatActivity {

    private int orderId;
    private String orderNo;
    private final List<ApiService.OrderItemSimple> orderItems = new ArrayList<>();

    private TextView tvOrderNo;
    private LinearLayout llProducts;
    private RatingBar ratingBar;
    private EditText etContent;
    private Button btnSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_write_review);

        orderId = getIntent().getIntExtra("orderId", 0);
        if (orderId == 0) {
            ToastUtil.showError(this, "订单信息异常");
            finish();
            return;
        }

        initView();
        loadOrderDetail();
    }

    private void initView() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvOrderNo = findViewById(R.id.tv_order_no);
        llProducts = findViewById(R.id.ll_products);
        ratingBar = findViewById(R.id.rating_bar);
        etContent = findViewById(R.id.et_content);
        btnSubmit = findViewById(R.id.btn_submit);

        btnSubmit.setOnClickListener(v -> submitReview());
    }

    private void loadOrderDetail() {
        ApiClient.getInstance(this).getApiService().getOrderDetail(orderId)
                .enqueue(new Callback<ApiService.Response<ApiService.BackendOrder>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<ApiService.BackendOrder>> call,
                                           Response<ApiService.Response<ApiService.BackendOrder>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200 && response.body().getData() != null) {
                            ApiService.BackendOrder order = response.body().getData();
                            orderNo = order.getOrderNo();
                            tvOrderNo.setText("订单号：" + (orderNo != null ? orderNo : ""));
                            orderItems.clear();
                            if (order.getItems() != null) {
                                orderItems.addAll(order.getItems());
                            }
                            renderProducts();
                        } else {
                            ToastUtil.showError(WriteReviewActivity.this, "订单信息加载失败");
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<ApiService.BackendOrder>> call, Throwable t) {
                        ToastUtil.showError(WriteReviewActivity.this, "网络异常，请稍后重试");
                    }
                });
    }

    private void renderProducts() {
        llProducts.removeAllViews();
        if (orderItems.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("没有可评价的商品");
            empty.setTextColor(getColorValue(R.color.colorTextSecondary));
            empty.setTextSize(13f);
            llProducts.addView(empty);
            return;
        }
        for (ApiService.OrderItemSimple item : orderItems) {
            TextView row = new TextView(this);
            row.setText(item.getProductName() + "  x" + item.getQuantity());
            row.setTextColor(getColorValue(R.color.colorTextPrimary));
            row.setTextSize(14f);
            row.setPadding(0, 8, 0, 8);
            llProducts.addView(row);
        }
    }

    private int getColorValue(int colorRes) {
        return androidx.core.content.ContextCompat.getColor(this, colorRes);
    }

    private void submitReview() {
        if (orderItems.isEmpty()) {
            ToastUtil.showWarning(this, "没有可评价的商品");
            return;
        }

        int rating = (int) ratingBar.getRating();
        if (rating < 1) {
            rating = 5;
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (ApiService.OrderItemSimple item : orderItems) {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("productId", item.getProductId());
            itemMap.put("rating", rating);
            items.add(itemMap);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("orderId", orderId);
        params.put("content", etContent.getText().toString().trim());
        params.put("items", items);

        btnSubmit.setEnabled(false);
        btnSubmit.setText("提交中...");

        ApiClient.getInstance(this).getApiService().createReview(params)
                .enqueue(new Callback<ApiService.Response<Void>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Void>> call,
                                           Response<ApiService.Response<Void>> response) {
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("提交评价");
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                            ToastUtil.showSuccess(WriteReviewActivity.this, "评价成功，感谢您的反馈");
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            String message = response.body() != null ? response.body().getMessage() : "评价失败";
                            ToastUtil.showError(WriteReviewActivity.this, message);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Void>> call, Throwable t) {
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("提交评价");
                        LogUtil.e("WriteReview", "提交评价失败", t);
                        ToastUtil.showError(WriteReviewActivity.this, "网络异常，评价失败");
                    }
                });
    }
}
