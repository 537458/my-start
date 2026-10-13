package com.petshop.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.petshop.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 选择支付方式。
 * 入参：EXTRA_AMOUNT 待支付金额，EXTRA_SELECTED_CODE 当前已选中的支付方式编码。
 * 出参：RESULT_PAYMENT_CODE 支付方式编码，RESULT_PAYMENT_NAME 支付方式中文名。
 * 会记住用户上次使用的支付方式，下次进入默认选中。
 */
public class PaymentMethodActivity extends AppCompatActivity {

    public static final String EXTRA_AMOUNT = "amount";
    public static final String EXTRA_SELECTED_CODE = "selected_code";
    public static final String RESULT_PAYMENT_CODE = "payment_code";
    public static final String RESULT_PAYMENT_NAME = "payment_method";

    private static final String PREF_NAME = "payment_prefs";
    private static final String KEY_LAST_METHOD = "last_method";

    private final List<PayChannel> channels = new ArrayList<>();
    private PayChannel selectedChannel;
    private PayAdapter adapter;
    private double amount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_method);

        amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0);
        String code = getIntent().getStringExtra(EXTRA_SELECTED_CODE);
        if (code == null || code.isEmpty()) {
            code = prefs().getString(KEY_LAST_METHOD, PayChannel.CODE_WECHAT);
        }
        initChannels(code);
        initView();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREF_NAME, MODE_PRIVATE);
    }

    private void initChannels(String defaultCode) {
        channels.clear();
        channels.add(new PayChannel(PayChannel.CODE_WECHAT, "微信支付", "推荐使用，安全快捷", "微", 0xFF07C160));
        channels.add(new PayChannel(PayChannel.CODE_ALIPAY, "支付宝", "支持花呗与余额支付", "支", 0xFF1677FF));
        channels.add(new PayChannel(PayChannel.CODE_BANK, "银行卡", "支持储蓄卡与信用卡", "卡", 0xFF3D5AFE));
        channels.add(new PayChannel(PayChannel.CODE_COD, "货到付款", "先验货，收货时再付款", "现", 0xFFFF9F1A));

        for (PayChannel channel : channels) {
            if (channel.getCode().equals(defaultCode)) {
                selectedChannel = channel;
                break;
            }
        }
        if (selectedChannel == null) {
            selectedChannel = channels.get(0);
        }
    }

    private void initView() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        String amountText = String.format(Locale.CHINA, "¥%.2f", amount);
        TextView tvPayAmount = findViewById(R.id.tv_pay_amount);
        TextView tvBottomAmount = findViewById(R.id.tv_bottom_amount);
        tvPayAmount.setText(amountText);
        tvBottomAmount.setText(amountText);

        RecyclerView recyclerView = findViewById(R.id.rv_payment_methods);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setNestedScrollingEnabled(false);
        adapter = new PayAdapter();
        recyclerView.setAdapter(adapter);

        TextView btnConfirm = findViewById(R.id.btn_confirm_pay);
        btnConfirm.setOnClickListener(v -> confirmSelection());
    }

    private void confirmSelection() {
        if (selectedChannel == null) {
            Toast.makeText(this, "请选择支付方式", Toast.LENGTH_SHORT).show();
            return;
        }
        // 记住本次选择，下次默认选中
        prefs().edit().putString(KEY_LAST_METHOD, selectedChannel.getCode()).apply();

        Intent result = new Intent();
        result.putExtra(RESULT_PAYMENT_CODE, selectedChannel.getCode());
        result.putExtra(RESULT_PAYMENT_NAME, selectedChannel.getName());
        setResult(RESULT_OK, result);
        finish();
    }

    private class PayAdapter extends RecyclerView.Adapter<PayAdapter.PayViewHolder> {

        @NonNull
        @Override
        public PayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_payment, parent, false);
            return new PayViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PayViewHolder holder, int position) {
            PayChannel channel = channels.get(position);
            boolean isSelected = selectedChannel != null
                    && channel.getCode().equals(selectedChannel.getCode());

            holder.tvName.setText(channel.getName());
            holder.tvDesc.setText(channel.getDescription());
            holder.tvIcon.setText(channel.getIconText());
            holder.tvIcon.setBackground(buildIconBackground(channel.getColor()));
            holder.ivCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            holder.itemView.setBackgroundResource(isSelected
                    ? R.drawable.bg_payment_item_selected
                    : R.drawable.bg_payment_item);

            holder.itemView.setOnClickListener(v -> {
                selectedChannel = channel;
                notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            return channels.size();
        }

        class PayViewHolder extends RecyclerView.ViewHolder {
            final TextView tvIcon;
            final TextView tvName;
            final TextView tvDesc;
            final ImageView ivCheck;

            PayViewHolder(@NonNull View itemView) {
                super(itemView);
                tvIcon = itemView.findViewById(R.id.tv_payment_icon);
                tvName = itemView.findViewById(R.id.tv_payment_name);
                tvDesc = itemView.findViewById(R.id.tv_payment_desc);
                ivCheck = itemView.findViewById(R.id.iv_payment_check);
            }
        }
    }

    private GradientDrawable buildIconBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    /** 支付渠道：编码与后端 PayMethod 保持一致 */
    private static class PayChannel {
        static final String CODE_WECHAT = "WECHAT";
        static final String CODE_ALIPAY = "ALIPAY";
        static final String CODE_BANK = "BANK";
        static final String CODE_COD = "COD";

        private final String code;
        private final String name;
        private final String description;
        private final String iconText;
        private final int color;

        PayChannel(String code, String name, String description, String iconText, int color) {
            this.code = code;
            this.name = name;
            this.description = description;
            this.iconText = iconText;
            this.color = color;
        }

        String getCode() {
            return code;
        }

        String getName() {
            return name;
        }

        String getDescription() {
            return description;
        }

        String getIconText() {
            return iconText;
        }

        int getColor() {
            return color;
        }
    }
}
