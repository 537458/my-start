package com.petshop.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

import com.petshop.R;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.databinding.ActivityProductDetailBinding;
import com.petshop.model.Product;
import com.petshop.model.ShoppingCart;
import com.petshop.util.AuthUtil;
import com.petshop.util.LogUtil;
import android.content.Intent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProductDetailActivity extends AppCompatActivity {

    /** 评价列表取首页条数：详情页最多只展示 3 条，抓一页就够 */
    private static final int REVIEW_PAGE_SIZE = 20;

    private ActivityProductDetailBinding binding;
    private Product currentProduct;
    private boolean isOutOfStock = false; // 缺货标记
    private boolean isFavorited = false;  // 收藏标记
    private boolean favoriteRequesting = false; // 收藏请求进行中，防止重复点击
    private int reviewCount = 0;          // 评价总条数，用于“查看全部”入口
    private List<ApiService.ProductReview> loadedReviews; // 已渲染的评价，汇总晚到时用它重绘

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProductDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 主题会给 Button 加上 colorPrimary 背景着色，这里清除以保留自定义的圆角背景
        binding.btnAddToCart.setBackgroundTintList(null);
        binding.btnBuyNow.setBackgroundTintList(null);

        initListeners();
        initData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到页面重新拉取收藏状态（处理从登录页返回的场景）
        int productId = getIntent().getIntExtra("productId", -1);
        if (productId > 0) {
            loadFavoriteState(productId);
        }
    }

    private void initListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnShare.setOnClickListener(v -> shareProduct());
        binding.btnFavorite.setOnClickListener(v -> toggleFavorite());
        // 点击评价标题也能进入全部评价页
        binding.tvReviewSummary.setOnClickListener(v -> openReviewList());
        
        // 添加到购物车
        binding.btnAddToCart.setOnClickListener(v -> {
            // 检查是否缺货
            if (isOutOfStock) {
                Toast.makeText(ProductDetailActivity.this, "商品已缺货，无法加入购物车", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (currentProduct != null) {
                ShoppingCart cart = new ShoppingCart();
                cart.setProductId(currentProduct.getId());
                cart.setQuantity(1);
                ApiClient.getInstance(ProductDetailActivity.this).getApiService().addToCart(cart)
                        .enqueue(new Callback<ApiService.Response<com.petshop.model.ShoppingCart>>() {
                            @Override
                            public void onResponse(Call<ApiService.Response<com.petshop.model.ShoppingCart>> call,
                                                   Response<ApiService.Response<com.petshop.model.ShoppingCart>> response) {
                                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                                    Toast.makeText(ProductDetailActivity.this, "已加入购物车", Toast.LENGTH_SHORT).show();
                                } else {
                                    String errorMsg = response.body() != null ? response.body().getMessage() : "添加失败";
                                    Toast.makeText(ProductDetailActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                                }
                            }
                            @Override
                            public void onFailure(Call<ApiService.Response<com.petshop.model.ShoppingCart>> call, Throwable t) {
                                Toast.makeText(ProductDetailActivity.this, "网络错误", Toast.LENGTH_SHORT).show();
                            }
                        });
            } else {
                Toast.makeText(ProductDetailActivity.this, "商品信息加载中", Toast.LENGTH_SHORT).show();
            }
        });

        // 立即购买
        binding.btnBuyNow.setOnClickListener(v -> {
            // 检查是否缺货
            if (isOutOfStock) {
                Toast.makeText(ProductDetailActivity.this, "商品已缺货，无法购买", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (currentProduct == null || currentProduct.getId() == 0) {
                Toast.makeText(this, "商品信息加载中，请稍后再试", Toast.LENGTH_SHORT).show();
                android.util.Log.e("BuyNow", "currentProduct 为空或 id 为 0，无法跳转");
                return;
            }
            
            android.util.Log.d("BuyNow", "点击立即购买: productId=" + currentProduct.getId() + ", name=" + currentProduct.getName() + ", price=" + currentProduct.getPrice());
            
            // 跳转到订单确认页面，与购物车结算保持一致
            Intent intent = new Intent(this, OrderActivity.class);
            intent.putExtra("product_id", currentProduct.getId());
            intent.putExtra("product_name", currentProduct.getName());
            intent.putExtra("product_price", currentProduct.getPrice());
            intent.putExtra("product_quantity", 1);
            intent.putExtra("total_price", currentProduct.getPrice());
            intent.putExtra("product_stock", currentProduct.getStock());
            // 传递商品图片URL，确保订单页面显示相同的图片
            intent.putExtra("product_image", getProductImageUrl(currentProduct));
            android.util.Log.d("BuyNow", "Intent 已设置: product_id=" + currentProduct.getId());
            startActivity(intent);
        });
    }

    /**
     * 加载商品评价：评分汇总 + 最新几条评价
     */
    private void loadProductReviews(int productId) {
        ApiClient.getInstance(this).getApiService().getProductReviewSummary(productId)
                .enqueue(new Callback<ApiService.Response<ApiService.ReviewSummary>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<ApiService.ReviewSummary>> call,
                                           Response<ApiService.Response<ApiService.ReviewSummary>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200 && response.body().getData() != null) {
                            ApiService.ReviewSummary summary = response.body().getData();
                            reviewCount = summary.getCount();
                            // 汇总和列表是两个并行请求，汇总后到时补上“查看全部”入口
                            if (loadedReviews != null) {
                                renderReviews(loadedReviews);
                            }
                            if (summary.getCount() <= 0) {
                                binding.tvReviewSummary.setText("商品评价（暂无评价）");
                            } else {
                                binding.tvReviewSummary.setText(String.format(java.util.Locale.CHINA,
                                        "商品评价 %.1f 分 · %d 条", summary.getAvgRating(), summary.getCount()));
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<ApiService.ReviewSummary>> call, Throwable t) {
                        LogUtil.e("ProductDetail", "加载评价汇总失败", t);
                    }
                });

        // 详情页只展示前几条，取第一页足够
        ApiClient.getInstance(this).getApiService().getProductReviews(productId, 1, REVIEW_PAGE_SIZE)
                .enqueue(new Callback<ApiService.Response<List<ApiService.ProductReview>>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<List<ApiService.ProductReview>>> call,
                                           Response<ApiService.Response<List<ApiService.ProductReview>>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200) {
                            renderReviews(response.body().getData());
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<List<ApiService.ProductReview>>> call, Throwable t) {
                        LogUtil.e("ProductDetail", "加载评价列表失败", t);
                    }
                });
    }

    /**
     * 渲染评价列表，最多展示 3 条
     */
    private void renderReviews(List<ApiService.ProductReview> reviews) {
        loadedReviews = reviews;
        binding.llReviewList.removeAllViews();
        if (reviews == null || reviews.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有人评价，快来抢沙发");
            empty.setTextSize(13f);
            empty.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary));
            binding.llReviewList.addView(empty);
            return;
        }
        int limit = Math.min(reviews.size(), 3);
        for (int i = 0; i < limit; i++) {
            ApiService.ProductReview review = reviews.get(i);
            TextView row = new TextView(this);
            String content = review.getContent() != null && !review.getContent().isEmpty()
                    ? review.getContent() : "此用户没有填写评价内容";
            row.setText(review.getUserName() + "  " + buildStars(review.getRating()) + "\n" + content);
            row.setTextSize(13f);
            row.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary));
            row.setLineSpacing(4f, 1f);
            row.setPadding(0, 8, 0, 8);
            binding.llReviewList.addView(row);
        }
        // 详情页只展示前 3 条，更多评价引导到独立列表页
        if (reviewCount > limit) {
            TextView more = new TextView(this);
            more.setText("查看全部 " + reviewCount + " 条评价 ›");
            more.setTextSize(13f);
            more.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
            more.setPadding(0, 12, 0, 4);
            more.setOnClickListener(v -> openReviewList());
            binding.llReviewList.addView(more);
        }
    }

    /** 跳转商品全部评价列表 */
    private void openReviewList() {
        if (currentProduct == null || currentProduct.getId() <= 0) {
            return;
        }
        Intent intent = new Intent(this, ReviewListActivity.class);
        intent.putExtra("productId", currentProduct.getId());
        intent.putExtra("productName", currentProduct.getName());
        startActivity(intent);
    }

    private String buildStars(int rating) {
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            stars.append(i < rating ? "★" : "☆");
        }
        return stars.toString();
    }

    private void initData() {
        int productId = getIntent().getIntExtra("productId", -1);
        android.util.Log.d("ProductDetail", "initData: productId=" + productId);
        
        // 立即显示加载状态
        showLoading(true);
        
        if (productId > 0) {
            loadProductDetail(productId);
            loadProductReviews(productId);
        } else {
            android.util.Log.e("ProductDetail", "未找到productId，无法加载商品详情");
            String productName = getIntent().getStringExtra("productName");
            String productPrice = getIntent().getStringExtra("productPrice");
            int productImage = getIntent().getIntExtra("productImage", R.drawable.ic_launcher);
            binding.ivProduct.setImageResource(productImage);
            binding.tvProductName.setText(productName);
            binding.tvProductPrice.setText(productPrice);
            binding.tvProductDescription.setText("商品详情");
            showLoading(false);
        }
    }

    /**
     * 调用系统分享面板分享当前商品（携带商品名与价格）
     */
    private void shareProduct() {
        if (currentProduct == null) {
            Toast.makeText(this, "商品信息加载中，请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = currentProduct.getName() != null && !currentProduct.getName().isEmpty()
                ? currentProduct.getName() : "宠物商品";
        String price = String.format(java.util.Locale.CHINA, "¥%.2f", currentProduct.getPrice());
        String shareText = "我在宠物商城发现了好物：" + name + "，仅需 " + price + "，快来看看吧！";

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, name);
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        try {
            startActivity(Intent.createChooser(shareIntent, "分享商品"));
        } catch (Exception e) {
            // 部分精简系统无分享目标，避免直接崩溃
            LogUtil.e("ProductDetail", "调起系统分享失败", e);
            Toast.makeText(this, "当前设备暂不支持分享", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 查询当前用户是否已收藏该商品；未登录时保持未选中且不提示
     */
    private void loadFavoriteState(int productId) {
        if (!AuthUtil.isValidUserId(this)) {
            return;
        }
        ApiClient.getInstance(this).getApiService().isFavorited(productId)
                .enqueue(new Callback<ApiService.Response<Boolean>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Boolean>> call,
                                           Response<ApiService.Response<Boolean>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200) {
                            isFavorited = Boolean.TRUE.equals(response.body().getData());
                            binding.btnFavorite.setSelected(isFavorited);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Boolean>> call, Throwable t) {
                        LogUtil.e("ProductDetail", "查询收藏状态失败", t);
                    }
                });
    }

    /**
     * 切换收藏状态：未登录先引导登录，请求期间禁止重复点击
     */
    private void toggleFavorite() {
        if (!AuthUtil.checkLoginWithPddStyle(this)) {
            return;
        }
        if (currentProduct == null || currentProduct.getId() <= 0) {
            Toast.makeText(this, "商品信息加载中，请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        if (favoriteRequesting) {
            return;
        }
        favoriteRequesting = true;

        int productId = currentProduct.getId();
        boolean target = !isFavorited;
        Call<ApiService.Response<Boolean>> call = target
                ? ApiClient.getInstance(this).getApiService().addFavorite(productId)
                : ApiClient.getInstance(this).getApiService().removeFavorite(productId);

        call.enqueue(new Callback<ApiService.Response<Boolean>>() {
            @Override
            public void onResponse(Call<ApiService.Response<Boolean>> call,
                                   Response<ApiService.Response<Boolean>> response) {
                favoriteRequesting = false;
                if (response.isSuccessful() && response.body() != null
                        && response.body().getCode() == 200) {
                    isFavorited = target;
                    binding.btnFavorite.setSelected(isFavorited);
                    Toast.makeText(ProductDetailActivity.this,
                            isFavorited ? "已加入收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
                } else {
                    String errorMsg = response.body() != null ? response.body().getMessage() : "操作失败";
                    Toast.makeText(ProductDetailActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiService.Response<Boolean>> call, Throwable t) {
                favoriteRequesting = false;
                LogUtil.e("ProductDetail", "收藏操作失败", t);
                Toast.makeText(ProductDetailActivity.this, "网络错误，请稍后重试", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadProductDetail(int productId) {
        LogUtil.i("ProductDetail", "[BLOB-LOAD] ========== 开始加载商品详情 ==========");
        LogUtil.i("ProductDetail", "[BLOB-LOAD] 请求商品ID: " + productId);
        
        ApiClient.getInstance(this).getApiService().getProductDetail(productId)
                .enqueue(new Callback<ApiService.Response<Product>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Product>> call,
                                           Response<ApiService.Response<Product>> response) {
                        LogUtil.i("ProductDetail", "[BLOB-LOAD] ========== 收到网络响应 ==========");
                        LogUtil.i("ProductDetail", "[BLOB-LOAD] 响应成功: " + response.isSuccessful());
                        
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                            currentProduct = response.body().getData();
                            if (currentProduct != null) {
                                LogUtil.i("ProductDetail", "[BLOB-LOAD] ========== 商品数据解析成功 ==========");
                                LogUtil.i("ProductDetail", "[BLOB-LOAD] 商品ID: " + currentProduct.getId());
                                LogUtil.i("ProductDetail", "[BLOB-LOAD] 商品名称: " + currentProduct.getName());
                                LogUtil.i("ProductDetail", "[BLOB-LOAD] 商品价格: " + currentProduct.getPrice());
                                LogUtil.i("ProductDetail", "[BLOB-LOAD] 商品库存: " + currentProduct.getStock());
                                LogUtil.i("ProductDetail", "[BLOB-LOAD] 商品描述: " + currentProduct.getDescription());
                                LogUtil.i("ProductDetail", "[BLOB-LOAD] mainImage: " + currentProduct.getMainImage());
                                
                                if (currentProduct.getId() == 0) {
                                    LogUtil.e("ProductDetail", "[BLOB-LOAD] 警告: 后端返回的商品 id 为 0!");
                                }
                                
                                // 设置商品信息
                                binding.tvProductName.setText(currentProduct.getName());
                                binding.tvProductPrice.setText(String.format(java.util.Locale.CHINA, "¥%.2f", currentProduct.getPrice()));
                                binding.tvProductDescription.setText(currentProduct.getDescription() != null ? currentProduct.getDescription() : "优质宠物用品");
                                
                                // 优化图片加载：使用高优先级+限制最大尺寸，避免大图导致卡顿
                                String imageUrl = getProductImageUrl(currentProduct);
                                LogUtil.i("ProductDetail", "[BLOB-LOAD] 最终图片URL: " + imageUrl);
                                loadProductImageOptimized(imageUrl);
                                
                                // 更新库存显示
                                updateStockDisplay(currentProduct.getStock());
                            } else {
                                LogUtil.e("ProductDetail", "[BLOB-LOAD] 商品数据为空");
                                showError("商品信息加载失败");
                            }
                        } else {
                            LogUtil.e("ProductDetail", "[BLOB-LOAD] ========== 加载失败 ==========");
                            if (response.body() != null) {
                                LogUtil.e("ProductDetail", "[BLOB-LOAD] 响应Code: " + response.body().getCode());
                                LogUtil.e("ProductDetail", "[BLOB-LOAD] 响应Message: " + response.body().getMessage());
                            }
                            showError("加载失败，请稍后重试");
                        }
                        
                        // 隐藏加载状态
                        showLoading(false);
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Product>> call, Throwable t) {
                        LogUtil.e("ProductDetail", "[BLOB-LOAD] ========== 网络请求失败 ==========");
                        LogUtil.e("ProductDetail", "[BLOB-LOAD] 异常类型: " + t.getClass().getSimpleName());
                        LogUtil.e("ProductDetail", "[BLOB-LOAD] 异常消息: " + t.getMessage(), t);
                        
                        // 隐藏加载状态
                        showLoading(false);
                        showError("网络错误，请检查网络连接");
                    }
                });
    }
    
    /**
     * 优化图片加载：使用高优先级+限制最大尺寸，避免大图导致内存压力和卡顿
     */
    private void loadProductImageOptimized(String imageUrl) {
        final long startTime = System.currentTimeMillis();
        LogUtil.i("ProductDetail", "[BLOB-LOAD] ========== 开始加载商品详情页图片 ==========");
        LogUtil.i("ProductDetail", "[BLOB-LOAD] 请求URL: " + imageUrl);
        LogUtil.i("ProductDetail", "[BLOB-LOAD] 开始时间: " + new java.util.Date(startTime));
        
        try {
            // 使用高优先级加载，确保首屏图片优先加载
            // 设置最大尺寸为800x800，避免加载过大图片导致卡顿
            com.bumptech.glide.request.RequestOptions options = new com.bumptech.glide.request.RequestOptions()
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .error(R.drawable.ic_avatar_placeholder)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                    .priority(com.bumptech.glide.Priority.HIGH)
                    .override(800, 800) // 限制最大尺寸
                    .centerCrop();
            
            com.bumptech.glide.Glide.with(ProductDetailActivity.this)
                    .load(imageUrl)
                    .apply(options)
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                            long endTime = System.currentTimeMillis();
                            long duration = endTime - startTime;
                            LogUtil.e("ProductDetail", "[BLOB-LOAD] ========== 商品详情页图片加载失败 ==========");
                            LogUtil.e("ProductDetail", "[BLOB-LOAD] 失败URL: " + imageUrl);
                            LogUtil.e("ProductDetail", "[BLOB-LOAD] 加载时长: " + duration + "ms");
                            LogUtil.e("ProductDetail", "[BLOB-LOAD] 结束时间: " + new java.util.Date(endTime));
                            if (e != null) {
                                LogUtil.e("ProductDetail", "[BLOB-LOAD] 错误类型: " + e.getClass().getSimpleName());
                                LogUtil.e("ProductDetail", "[BLOB-LOAD] 错误消息: " + e.getMessage());
                                if (e.getRootCauses() != null) {
                                    for (Throwable cause : e.getRootCauses()) {
                                        LogUtil.e("ProductDetail", "[BLOB-LOAD] 根本原因: " + cause.getMessage(), cause);
                                    }
                                }
                            }
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            long endTime = System.currentTimeMillis();
                            long duration = endTime - startTime;
                            LogUtil.i("ProductDetail", "[BLOB-LOAD] ========== 商品详情页图片加载成功 ==========");
                            LogUtil.i("ProductDetail", "[BLOB-LOAD] 成功URL: " + imageUrl);
                            LogUtil.i("ProductDetail", "[BLOB-LOAD] 数据源: " + dataSource);
                            LogUtil.i("ProductDetail", "[BLOB-LOAD] 加载时长: " + duration + "ms");
                            LogUtil.i("ProductDetail", "[BLOB-LOAD] 结束时间: " + new java.util.Date(endTime));
                            LogUtil.i("ProductDetail", "[BLOB-LOAD] Drawable类型: " + resource.getClass().getSimpleName());
                            return false;
                        }
                    })
                    .into(binding.ivProduct);
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            LogUtil.e("ProductDetail", "[BLOB-LOAD] ========== 商品详情页图片加载异常 ==========");
            LogUtil.e("ProductDetail", "[BLOB-LOAD] 异常URL: " + imageUrl);
            LogUtil.e("ProductDetail", "[BLOB-LOAD] 加载时长: " + duration + "ms");
            LogUtil.e("ProductDetail", "[BLOB-LOAD] 异常类型: " + e.getClass().getSimpleName());
            LogUtil.e("ProductDetail", "[BLOB-LOAD] 异常消息: " + e.getMessage(), e);
            binding.ivProduct.setImageResource(R.drawable.ic_avatar_placeholder);
        }
    }

    /**
     * 更新库存显示和状态
     */
    private void updateStockDisplay(int stock) {
        // 更新库存数量显示
        binding.tvStockCount.setText("库存数量: " + stock);
        
        // 判断库存状态
        if (stock <= 0) {
            // 缺货状态
            isOutOfStock = true;
            binding.tvStockStatus.setText("缺货");
            binding.tvStockStatus.setTextColor(ContextCompat.getColor(this, R.color.red));
            binding.llStockContainer.setBackgroundColor(ContextCompat.getColor(this, R.color.red_background));
            applyActionButtons(false);
        } else if (stock <= 10) {
            // 库存紧张状态
            isOutOfStock = false;
            binding.tvStockStatus.setText("库存紧张");
            binding.tvStockStatus.setTextColor(ContextCompat.getColor(this, R.color.orange));
            binding.llStockContainer.setBackgroundColor(ContextCompat.getColor(this, R.color.orange_background));
            applyActionButtons(true);
        } else {
            // 库存充足状态
            isOutOfStock = false;
            binding.tvStockStatus.setText("库存充足");
            binding.tvStockStatus.setTextColor(ContextCompat.getColor(this, R.color.green));
            binding.llStockContainer.setBackgroundColor(ContextCompat.getColor(this, R.color.green_background));
            applyActionButtons(true);
        }
        
        android.util.Log.d("StockDisplay", "库存状态更新: stock=" + stock + ", isOutOfStock=" + isOutOfStock);
    }

    /**
     * 统一设置底部按钮的可用状态与文案。
     * 使用圆角背景资源而不是 setBackgroundColor，避免按钮被改成直角色块。
     */
    private void applyActionButtons(boolean available) {
        binding.btnBuyNow.setEnabled(available);
        binding.btnAddToCart.setEnabled(available);
        if (available) {
            binding.btnBuyNow.setBackgroundResource(R.drawable.bg_button_primary);
            binding.btnAddToCart.setBackgroundResource(R.drawable.bg_button_secondary);
            binding.btnBuyNow.setText("立即购买");
            binding.btnAddToCart.setText("加入购物车");
        } else {
            binding.btnBuyNow.setBackgroundResource(R.drawable.bg_button_disabled);
            binding.btnAddToCart.setBackgroundResource(R.drawable.bg_button_disabled);
            binding.btnBuyNow.setText("已缺货");
            binding.btnAddToCart.setText("已缺货");
        }
    }

    /**
     * 获取商品图片URL
     * 优先使用后端的图片接口从数据库读取BLOB数据，确保数据可靠性
     * 注意：将localhost/127.0.0.1替换为10.0.2.2，确保Android模拟器能访问本地服务
     */
    private String getProductImageUrl(Product product) {
        String baseUrl = ApiClient.getInstance(this).getBaseUrl();
        LogUtil.d("ProductDetail", "[BLOB-LOAD] getProductImageUrl开始，productId=" + product.getId());
        LogUtil.d("ProductDetail", "[BLOB-LOAD] API_BASE_URL=" + baseUrl);
        
        if (product.getMainImage() != null && !product.getMainImage().isEmpty()) {
            String imageUrl = product.getMainImage();
            LogUtil.d("ProductDetail", "[BLOB-LOAD] mainImage: " + imageUrl.substring(0, Math.min(50, imageUrl.length())) + "...");
            
            // 如果是Base64，直接返回
            if (imageUrl.startsWith("data:")) {
                LogUtil.d("ProductDetail", "[BLOB-LOAD] 检测到Base64数据，直接使用");
                return imageUrl;
            }
            // 如果是HTTP URL指向上传目录（包含/uploads/），改用后端BLOB接口
            if (imageUrl.startsWith("http") && imageUrl.contains("/uploads/")) {
                LogUtil.d("ProductDetail", "[BLOB-LOAD] 检测到上传目录URL，改用数据库BLOB接口");
                String finalUrl = replaceLocalhost(baseUrl + "images/product/" + product.getId());
                LogUtil.d("ProductDetail", "[BLOB-LOAD] 使用BLOB接口: " + finalUrl);
                return finalUrl;
            }
            // 如果是HTTP URL但不是上传目录，直接使用
            if (imageUrl.startsWith("http")) {
                String finalUrl = replaceLocalhost(imageUrl);
                LogUtil.d("ProductDetail", "[BLOB-LOAD] HTTP URL替换后: " + finalUrl);
                return finalUrl;
            }
            // 如果是相对路径（如 /images/product/1），拼接基础URL
            if (imageUrl.startsWith("/")) {
                String finalUrl = replaceLocalhost(baseUrl + imageUrl.substring(1));
                LogUtil.d("ProductDetail", "[BLOB-LOAD] 相对路径处理后: " + finalUrl);
                return finalUrl;
            }
            String finalUrl = replaceLocalhost(baseUrl + imageUrl);
            LogUtil.d("ProductDetail", "[BLOB-LOAD] 普通路径处理后: " + finalUrl);
            return finalUrl;
        }
        // 否则使用新的图片接口（直接返回字节流）
        String finalUrl = replaceLocalhost(baseUrl + "images/product/" + product.getId());
        LogUtil.d("ProductDetail", "[BLOB-LOAD] 使用默认图片接口: " + finalUrl);
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

    /**
     * 显示/隐藏加载状态
     */
    private void showLoading(boolean loading) {
        // 禁用按钮
        binding.btnAddToCart.setEnabled(!loading);
        binding.btnBuyNow.setEnabled(!loading);
        
        // 显示/隐藏加载动画（如果布局中有ProgressBar）
        // 这里简化处理，通过按钮状态和提示来反馈加载状态
        if (loading) {
            binding.btnBuyNow.setText("加载中...");
            binding.btnAddToCart.setText("加载中...");
        } else {
            // 根据库存状态恢复按钮文字与背景
            applyActionButtons(!isOutOfStock);
        }
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}