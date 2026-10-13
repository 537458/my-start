package com.petshop.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.petshop.R;
import com.petshop.api.ApiService;

import java.util.List;
import java.util.Locale;

/**
 * 优惠券列表适配器：领券中心与我的券共用，通过 mineMode 区分按钮含义
 */
public class CouponAdapter extends BaseAdapter<ApiService.CouponItem, CouponAdapter.CouponViewHolder> {

    public interface OnCouponActionListener {
        void onReceive(ApiService.CouponItem item);

        void onUse(ApiService.CouponItem item);
    }

    private final boolean mineMode;
    private final OnCouponActionListener listener;

    public CouponAdapter(Context context, List<ApiService.CouponItem> data, boolean mineMode,
                         OnCouponActionListener listener) {
        super(context, data);
        this.mineMode = mineMode;
        this.listener = listener;
    }

    @Override
    public CouponViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new CouponViewHolder(inflateView(R.layout.item_coupon, parent));
    }

    @Override
    public void onBindViewHolder(CouponViewHolder holder, int position) {
        ApiService.CouponItem item = getItem(position);
        if (item == null) {
            return;
        }
        holder.tvName.setText(item.getName());
        holder.tvDesc.setText(item.getDescription());
        holder.tvExpire.setText(item.getEndTime() == null || item.getEndTime().isEmpty()
                ? "长期有效" : "有效期至 " + formatDate(item.getEndTime()));

        if ("DISCOUNT".equals(item.getType())) {
            double zhe = item.getDiscount() * 10;
            holder.tvValue.setText(String.format(Locale.CHINA, "%.1f折", zhe));
        } else {
            holder.tvValue.setText(String.format(Locale.CHINA, "¥%.0f", item.getAmount()));
        }
        holder.tvThreshold.setText(String.format(Locale.CHINA, "满%.0f可用", item.getMinAmount()));

        if (mineMode) {
            if ("USED".equals(item.getStatus())) {
                holder.btnAction.setText("已使用");
                holder.btnAction.setEnabled(false);
                holder.btnAction.setBackgroundResource(R.drawable.bg_button_disabled);
                holder.btnAction.setOnClickListener(null);
            } else {
                holder.btnAction.setText("去使用");
                holder.btnAction.setEnabled(true);
                holder.btnAction.setBackgroundResource(R.drawable.bg_button_primary);
                holder.btnAction.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onUse(item);
                    }
                });
            }
        } else if (item.isReceived()) {
            holder.btnAction.setText("已领取");
            holder.btnAction.setEnabled(false);
            holder.btnAction.setBackgroundResource(R.drawable.bg_button_disabled);
            holder.btnAction.setOnClickListener(null);
        } else {
            holder.btnAction.setText("领取");
            holder.btnAction.setEnabled(true);
            holder.btnAction.setBackgroundResource(R.drawable.bg_button_primary);
            holder.btnAction.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onReceive(item);
                }
            });
        }
    }

    private String formatDate(String isoTime) {
        if (isoTime == null) {
            return "";
        }
        // 后端返回形如 2027-10-28T12:00:00，展示只保留日期部分
        return isoTime.length() >= 10 ? isoTime.substring(0, 10) : isoTime;
    }

    static class CouponViewHolder extends BaseViewHolder {
        TextView tvValue, tvThreshold, tvName, tvDesc, tvExpire, btnAction;

        CouponViewHolder(View itemView) {
            super(itemView);
            tvValue = itemView.findViewById(R.id.tv_value);
            tvThreshold = itemView.findViewById(R.id.tv_threshold);
            tvName = itemView.findViewById(R.id.tv_name);
            tvDesc = itemView.findViewById(R.id.tv_desc);
            tvExpire = itemView.findViewById(R.id.tv_expire);
            btnAction = itemView.findViewById(R.id.btn_action);
        }
    }
}
