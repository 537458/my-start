package com.petshop.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.petshop.R;

/**
 * 支付结果页。
 * 只做结果展示与下一步引导，不承担任何支付请求：
 * - 从下单页支付成功进入：detailExtras 携带订单详情参数，「查看订单」直接打开详情；
 * - 从订单详情页支付成功进入：fromDetail=true，「查看订单」直接回到已有的详情页。
 */
public class PayResultActivity extends AppCompatActivity {

    public static final String EXTRA_AMOUNT = "amount";
    public static final String EXTRA_PAY_METHOD_LABEL = "pay_method_label";
    public static final String EXTRA_ORDER_NO = "order_no";
    public static final String EXTRA_PAID_AT = "paid_at";
    public static final String EXTRA_DETAIL_EXTRAS = "detail_extras";
    public static final String EXTRA_FROM_DETAIL = "from_detail";

    private boolean fromDetail;
    private Bundle detailExtras;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pay_result);

        fromDetail = getIntent().getBooleanExtra(EXTRA_FROM_DETAIL, false);
        detailExtras = getIntent().getBundleExtra(EXTRA_DETAIL_EXTRAS);

        TextView tvAmount = findViewById(R.id.tv_amount);
        TextView tvPayMethod = findViewById(R.id.tv_pay_method);
        TextView tvOrderNo = findViewById(R.id.tv_order_no);
        TextView tvPaidAt = findViewById(R.id.tv_paid_at);
        View llRowPaidAt = findViewById(R.id.ll_row_paid_at);
        View dividerPaidAt = findViewById(R.id.divider_before_paid_at);

        double amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0);
        tvAmount.setText(String.format(java.util.Locale.CHINA, "¥%.2f", amount));

        String payMethod = getIntent().getStringExtra(EXTRA_PAY_METHOD_LABEL);
        tvPayMethod.setText(payMethod != null && !payMethod.isEmpty() ? payMethod : "在线支付");

        String orderNo = getIntent().getStringExtra(EXTRA_ORDER_NO);
        tvOrderNo.setText(orderNo != null && !orderNo.isEmpty() ? orderNo : "-");

        // 服务端未返回支付时间时不展示该行，避免出现空白标签
        String paidAt = formatTime(getIntent().getStringExtra(EXTRA_PAID_AT));
        if (paidAt.isEmpty()) {
            llRowPaidAt.setVisibility(View.GONE);
            dividerPaidAt.setVisibility(View.GONE);
        } else {
            tvPaidAt.setText(paidAt);
        }

        findViewById(R.id.btn_view_order).setOnClickListener(v -> openOrderDetail());
        findViewById(R.id.btn_continue_shopping).setOnClickListener(v -> goShopping());

        // 返回行为与“查看订单”一致：从详情页进来就回详情，否则回首页，避免退回到已结束的下单页
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (fromDetail) {
                    finish();
                } else {
                    goShopping();
                }
            }
        });
    }

    private void openOrderDetail() {
        if (fromDetail) {
            finish();
            return;
        }
        if (detailExtras != null) {
            Intent intent = new Intent(this, OrderDetailActivity.class);
            intent.putExtras(detailExtras);
            startActivity(intent);
        }
        finish();
    }

    private void goShopping() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    /** 服务端时间形如 2026-05-12T10:20:30，这里转成「2026-05-12 10:20」 */
    private static String formatTime(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String text = raw.replace('T', ' ');
        return text.length() > 16 ? text.substring(0, 16) : text;
    }
}
