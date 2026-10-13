package com.petshop.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.petshop.BuildConfig;
import com.petshop.R;
import com.petshop.activity.ProductDetailActivity;
import com.petshop.api.ApiClient;
import com.petshop.model.Product;
import com.petshop.util.DiffUtilCallback;
import com.petshop.util.GlideUtil;
import com.petshop.util.LogUtil;

import java.util.List;

public class ProductAdapter extends BaseAdapter<Product, ProductAdapter.ProductViewHolder> {

    private Context context;
    /** 网格模式（分类页两列）使用满宽卡片，横向列表使用定宽卡片 */
    private boolean gridMode;

    public ProductAdapter(Context context, List<Product> productList) {
        this(context, productList, false);
    }

    public ProductAdapter(Context context, List<Product> productList, boolean gridMode) {
        super(context, productList);
        this.context = context;
        this.gridMode = gridMode;
    }
    
    /**
     * 获取基础URL，优先使用ApiClient动态获取的地址
     */
    private String getBaseUrl() {
        if (context != null) {
            return ApiClient.getInstance(context).getBaseUrl();
        }
        return BuildConfig.API_BASE_URL;
    }

    /**
     * 获取商品图片URL
     * 优先使用后端的图片接口从数据库读取BLOB数据，确保数据可靠性
     * 注意：将localhost/127.0.0.1替换为10.0.2.2，确保Android模拟器能访问本地服务
     */
    private String getProductImageUrl(Product product) {
        String baseUrl = getBaseUrl();
        LogUtil.d("ProductAdapter", "[BLOB-LOAD] getProductImageUrl开始，productId=" + product.getId());
        LogUtil.d("ProductAdapter", "[BLOB-LOAD] BASE_URL=" + baseUrl);
        
        // 如果后端返回了图片URL
        if (product.getMainImage() != null && !product.getMainImage().isEmpty()) {
            String imageUrl = product.getMainImage();
            LogUtil.d("ProductAdapter", "[BLOB-LOAD] mainImage: " + imageUrl.substring(0, Math.min(50, imageUrl.length())) + "...");
            
            // 如果是Base64，直接返回
            if (imageUrl.startsWith("data:")) {
                LogUtil.d("ProductAdapter", "[BLOB-LOAD] 检测到Base64数据，直接使用");
                return imageUrl;
            }
            // 如果是HTTP URL指向上传目录（包含/uploads/），改用后端BLOB接口
            if (imageUrl.startsWith("http") && imageUrl.contains("/uploads/")) {
                LogUtil.d("ProductAdapter", "[BLOB-LOAD] 检测到上传目录URL，改用数据库BLOB接口");
                String finalUrl = replaceLocalhost(baseUrl + "images/product/" + product.getId());
                LogUtil.d("ProductAdapter", "[BLOB-LOAD] 使用BLOB接口: " + finalUrl);
                return finalUrl;
            }
            // 如果是HTTP URL但不是上传目录，直接使用
            if (imageUrl.startsWith("http")) {
                String finalUrl = replaceLocalhost(imageUrl);
                LogUtil.d("ProductAdapter", "[BLOB-LOAD] HTTP URL替换后: " + finalUrl);
                return finalUrl;
            }
            // 如果是相对路径（如 /images/product/1），拼接基础URL
            if (imageUrl.startsWith("/")) {
                String finalUrl = replaceLocalhost(baseUrl + imageUrl.substring(1));
                LogUtil.d("ProductAdapter", "[BLOB-LOAD] 相对路径处理后: " + finalUrl);
                return finalUrl;
            }
            String finalUrl = replaceLocalhost(baseUrl + imageUrl);
            LogUtil.d("ProductAdapter", "[BLOB-LOAD] 普通路径处理后: " + finalUrl);
            return finalUrl;
        }
        // 否则使用新的图片接口（直接返回字节流）
        String finalUrl = replaceLocalhost(baseUrl + "images/product/" + product.getId());
        LogUtil.d("ProductAdapter", "[BLOB-LOAD] 使用默认图片接口: " + finalUrl);
        return finalUrl;
    }
    
    /**
     * 模拟器需要把 localhost/127.0.0.1 替换为 10.0.2.2 才能访问本地主机；
     * 真机通过 adb reverse 端口转发，127.0.0.1 本身就能访问电脑，无需替换
     */
    private String replaceLocalhost(String url) {
        if (url == null) {
            return null;
        }
        if (ApiClient.isEmulatorDevice()) {
            return url.replace("localhost", "10.0.2.2")
                      .replace("127.0.0.1", "10.0.2.2");
        }
        return url;
    }

    public void updateData(List<Product> newProductList) {
        updateData(newProductList, new DiffUtilCallback.ItemCallback<Product>() {
            @Override
            public boolean areItemsTheSame(Product oldItem, Product newItem) {
                return oldItem.getId() == newItem.getId();
            }

            @Override
            public boolean areContentsTheSame(Product oldItem, Product newItem) {
                return oldItem.getName().equals(newItem.getName()) &&
                        oldItem.getPrice() == newItem.getPrice() &&
                        oldItem.getDescription().equals(newItem.getDescription());
            }
        });
    }

    @Override
    public ProductViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        int layoutRes = gridMode ? R.layout.item_product_grid : R.layout.item_product;
        View view = inflateView(layoutRes, parent);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ProductViewHolder holder, int position) {
        Product product = getItem(position);
        
        LogUtil.i("ProductAdapter", "[BLOB-LOAD] ========== 绑定商品项 ==========");
        LogUtil.i("ProductAdapter", "[BLOB-LOAD] 商品ID: " + product.getId());
        LogUtil.i("ProductAdapter", "[BLOB-LOAD] 商品名称: " + product.getName());
        LogUtil.i("ProductAdapter", "[BLOB-LOAD] 商品位置: " + position);
        LogUtil.i("ProductAdapter", "[BLOB-LOAD] mainImage: " + product.getMainImage());
        
        holder.tvProductName.setText(product.getName());
        holder.tvProductPrice.setText(String.format(java.util.Locale.CHINA, "¥%.2f", product.getPrice()));
        
        // 获取商品图片URL
        String imageUrl = getProductImageUrl(product);
        LogUtil.i("ProductAdapter", "[BLOB-LOAD] 最终图片URL: " + imageUrl);
        
        // 加载图片
        GlideUtil.loadThumbnail(context, imageUrl, holder.ivProduct);
        
        holder.itemView.setOnClickListener(v -> {
            LogUtil.d("ProductAdapter", "[BLOB-LOAD] 点击商品: ID=" + product.getId());
            Intent intent = new Intent(context, ProductDetailActivity.class);
            intent.putExtra("productId", product.getId());
            context.startActivity(intent);
        });
    }

    public static class ProductViewHolder extends BaseViewHolder {
        ImageView ivProduct;
        TextView tvProductName, tvProductPrice;

        public ProductViewHolder(View itemView) {
            super(itemView);
            ivProduct = itemView.findViewById(R.id.iv_product);
            tvProductName = itemView.findViewById(R.id.tv_product_name);
            tvProductPrice = itemView.findViewById(R.id.tv_product_price);
        }
    }
}