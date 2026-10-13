package com.petshop.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.petshop.R;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.util.GlideUtil;

import java.util.List;
import java.util.Locale;

/**
 * 我的收藏列表适配器
 */
public class FavoriteAdapter extends BaseAdapter<ApiService.FavoriteItem, FavoriteAdapter.FavoriteViewHolder> {

    public interface OnFavoriteActionListener {
        void onRemove(ApiService.FavoriteItem item);
    }

    private final OnFavoriteActionListener listener;

    public FavoriteAdapter(Context context, List<ApiService.FavoriteItem> data,
                           OnFavoriteActionListener listener) {
        super(context, data);
        this.listener = listener;
    }

    @Override
    public FavoriteViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new FavoriteViewHolder(inflateView(R.layout.item_favorite, parent));
    }

    @Override
    public void onBindViewHolder(FavoriteViewHolder holder, int position) {
        ApiService.FavoriteItem item = getItem(position);
        if (item == null) {
            return;
        }
        holder.tvName.setText(item.getName());
        holder.tvPrice.setText(String.format(Locale.CHINA, "¥%.2f", item.getPrice()));
        holder.tvStock.setText(item.getStock() > 0 ? "库存 " + item.getStock() + " 件" : "暂时缺货");
        GlideUtil.loadThumbnail(context, buildImageUrl(item.getProductId()), holder.ivProduct);

        holder.itemView.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(context, com.petshop.activity.ProductDetailActivity.class);
            intent.putExtra("productId", item.getProductId());
            context.startActivity(intent);
        });
        holder.btnRemove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRemove(item);
            }
        });
    }

    private String buildImageUrl(int productId) {
        String baseUrl = ApiClient.getInstance(context).getBaseUrl();
        String url = baseUrl + "images/product/" + productId;
        if (ApiClient.isEmulatorDevice()) {
            return url.replace("localhost", "10.0.2.2").replace("127.0.0.1", "10.0.2.2");
        }
        return url;
    }

    static class FavoriteViewHolder extends BaseViewHolder {
        ImageView ivProduct;
        TextView tvName, tvPrice, tvStock, btnRemove;

        FavoriteViewHolder(View itemView) {
            super(itemView);
            ivProduct = itemView.findViewById(R.id.iv_product);
            tvName = itemView.findViewById(R.id.tv_name);
            tvPrice = itemView.findViewById(R.id.tv_price);
            tvStock = itemView.findViewById(R.id.tv_stock);
            btnRemove = itemView.findViewById(R.id.btn_remove);
        }
    }
}
