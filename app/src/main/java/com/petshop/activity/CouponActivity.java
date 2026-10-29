package com.petshop.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.petshop.R;
import com.petshop.adapter.CouponAdapter;
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
 * 优惠券中心：领券中心 / 我的优惠券两个页签
 */
public class CouponActivity extends AppCompatActivity {

    private RecyclerView rvCoupons;
    private TextView tvEmpty;
    private TextView tvTabTemplates;
    private TextView tvTabMine;

    private CouponAdapter adapter;
    private final List<ApiService.CouponItem> coupons = new ArrayList<>();
    private boolean mineMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_coupon);

        if (!AuthUtil.checkLoginWithPddStyle(this)) {
            finish();
            return;
        }

        initView();
        switchTab(false);
    }

    private void initView() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back_dark);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvCoupons = findViewById(R.id.rv_coupons);
        tvEmpty = findViewById(R.id.tv_empty);
        tvTabTemplates = findViewById(R.id.tv_tab_templates);
        tvTabMine = findViewById(R.id.tv_tab_mine);

        adapter = new CouponAdapter(this, coupons, mineMode, new CouponAdapter.OnCouponActionListener() {
            @Override
            public void onReceive(ApiService.CouponItem item) {
                receiveCoupon(item);
            }

            @Override
            public void onUse(ApiService.CouponItem item) {
                openShop();
            }
        });
        rvCoupons.setLayoutManager(new LinearLayoutManager(this));
        rvCoupons.setAdapter(adapter);

        tvTabTemplates.setOnClickListener(v -> switchTab(false));
        tvTabMine.setOnClickListener(v -> switchTab(true));
    }

    private void switchTab(boolean showMine) {
        mineMode = showMine;
        tvTabTemplates.setTextColor(getColor(showMine ? R.color.colorTextSecondary : R.color.colorPrimary));
        tvTabTemplates.setTypeface(null, showMine ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        tvTabMine.setTextColor(getColor(showMine ? R.color.colorPrimary : R.color.colorTextSecondary));
        tvTabMine.setTypeface(null, showMine ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

        // 重建适配器，切换按钮语义（领取 / 去使用）
        adapter = new CouponAdapter(this, coupons, showMine, new CouponAdapter.OnCouponActionListener() {
            @Override
            public void onReceive(ApiService.CouponItem item) {
                receiveCoupon(item);
            }

            @Override
            public void onUse(ApiService.CouponItem item) {
                openShop();
            }
        });
        rvCoupons.setAdapter(adapter);

        if (showMine) {
            loadMyCoupons();
        } else {
            loadTemplates();
        }
    }

    private void loadTemplates() {
        ApiClient.getInstance(this).getApiService().getCouponTemplates()
                .enqueue(new Callback<ApiService.Response<List<ApiService.CouponItem>>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<List<ApiService.CouponItem>>> call,
                                           Response<ApiService.Response<List<ApiService.CouponItem>>> response) {
                        handleListResponse(response, "暂无可领取的优惠券");
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<List<ApiService.CouponItem>>> call, Throwable t) {
                        onLoadFailure(t);
                    }
                });
    }

    private void loadMyCoupons() {
        ApiClient.getInstance(this).getApiService().getMyCoupons(null)
                .enqueue(new Callback<ApiService.Response<List<ApiService.CouponItem>>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<List<ApiService.CouponItem>>> call,
                                           Response<ApiService.Response<List<ApiService.CouponItem>>> response) {
                        handleListResponse(response, "还没有领过优惠券");
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<List<ApiService.CouponItem>>> call, Throwable t) {
                        onLoadFailure(t);
                    }
                });
    }

    private void handleListResponse(Response<ApiService.Response<List<ApiService.CouponItem>>> response,
                                    String emptyText) {
        if (response.isSuccessful() && response.body() != null
                && response.body().getCode() == 200 && response.body().getData() != null) {
            coupons.clear();
            coupons.addAll(response.body().getData());
            adapter.notifyDataSetChanged();
        } else {
            ToastUtil.showError(this, "优惠券加载失败");
        }
        updateEmptyState(emptyText);
    }

    private void onLoadFailure(Throwable t) {
        LogUtil.e("Coupon", "优惠券加载失败", t);
        ToastUtil.showError(this, "网络异常，请稍后重试");
        updateEmptyState(mineMode ? "还没有领过优惠券" : "暂无可领取的优惠券");
    }

    private void receiveCoupon(ApiService.CouponItem item) {
        ApiClient.getInstance(this).getApiService().receiveCoupon(item.getId())
                .enqueue(new Callback<ApiService.Response<Void>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Void>> call,
                                           Response<ApiService.Response<Void>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                            ToastUtil.showSuccess(CouponActivity.this, "领取成功，可在「我的优惠券」查看");
                            loadTemplates();
                        } else {
                            String message = response.body() != null ? response.body().getMessage() : "领取失败";
                            ToastUtil.showError(CouponActivity.this, message);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Void>> call, Throwable t) {
                        LogUtil.e("Coupon", "领券失败", t);
                        ToastUtil.showError(CouponActivity.this, "网络异常，请稍后重试");
                    }
                });
    }

    /**
     * 去使用：直接回到商城首页让用户选商品。
     * 原来直接 finish() 会退回发起页（通常是个人中心），用户还得自己再切到首页。
     */
    private void openShop() {
        ToastUtil.showInfo(this, "下单结算时可选择该优惠券抵扣");
        Intent intent = new Intent(this, MainActivity.class);
        // 复用已有实例，不再压入新的 MainActivity
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_HOME);
        startActivity(intent);
        finish();
    }

    private void updateEmptyState(String text) {
        boolean empty = coupons.isEmpty();
        tvEmpty.setText(text);
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvCoupons.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
