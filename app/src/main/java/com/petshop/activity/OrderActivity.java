package com.petshop.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.content.Context;
import android.view.LayoutInflater;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.petshop.R;
import com.petshop.activity.LoginActivity;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.util.AuthUtil;
import com.petshop.util.LogUtil;
import com.petshop.util.ToastUtil;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.bumptech.glide.Glide;
import com.petshop.util.GlideUtil;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrderActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_ADDRESS = 1;
    private static final int REQUEST_CODE_PAYMENT = 2;
    private static final int REQUEST_CODE_REMARK = 3;

    private TextView tvTotalPrice;
    private TextView btnPay;
    private TextView btnLater;
    private RecyclerView rvOrderItems;
    private TextView tvProductTotal;
    private TextView tvBottomTotal;
    private TextView tvUserInfo;
    private TextView tvUserAddress;
    private View llAddressContainer;
    private View llPaymentContainer;
    private View llRemarkContainer;
    private TextView tvSelectedPayment;
    private TextView tvRemark;
    private View llCouponContainer;
    private TextView tvSelectedCoupon;
    private View llDiscountRow;
    private TextView tvDiscountAmount;
    
    private List<ApiService.CartItemSimple> selectedItems = new ArrayList<>();
    private double totalPrice = 0;
    // 已选优惠券（user_coupons.id）与本地预估抵扣额，最终抵扣以服务端重算为准
    private Integer selectedUserCouponId = null;
    private String selectedCouponName = null;
    private double discountAmount = 0;
    private List<ApiService.CouponItem> usableCoupons = new ArrayList<>();
    // 支付方式：编码与后端 PayMethod 保持一致
    private String selectedPayMethodCode = "WECHAT";
    private String selectedPayMethodName = "微信支付";
    private ProgressDialog payProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order);

        if (!AuthUtil.checkLoginWithPddStyle(this)) {
            finish();
            return;
        }

        initView();
        
        Intent intent = getIntent();
        if (intent.hasExtra("product_id")) {
            loadProductFromApi(intent.getIntExtra("product_id", 0));
        } else {
            loadCartFromApi();
        }
        
        loadUserInfo();
    }

    private void initView() {
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }
        tvTotalPrice = findViewById(R.id.tv_total_price);
        btnPay = findViewById(R.id.btn_pay);
        btnLater = findViewById(R.id.btn_later);
        rvOrderItems = findViewById(R.id.rv_order_items);
        tvProductTotal = findViewById(R.id.tv_product_total);
        tvBottomTotal = findViewById(R.id.tv_bottom_total);
        tvUserInfo = findViewById(R.id.tv_user_info);
        tvUserAddress = findViewById(R.id.tv_user_address);
        llAddressContainer = findViewById(R.id.ll_address_container);
        llPaymentContainer = findViewById(R.id.ll_payment_container);
        tvSelectedPayment = findViewById(R.id.tv_selected_payment);
        // 默认支付方式：沿用用户上次选择（与支付方式页共用同一份配置）
        selectedPayMethodCode = getSharedPreferences("payment_prefs", MODE_PRIVATE)
                .getString("last_method", "WECHAT");
        selectedPayMethodName = payMethodLabelOf(selectedPayMethodCode);
        tvSelectedPayment.setText(selectedPayMethodName);
        llCouponContainer = findViewById(R.id.ll_coupon_container);
        tvSelectedCoupon = findViewById(R.id.tv_selected_coupon);
        llDiscountRow = findViewById(R.id.ll_discount_row);
        tvDiscountAmount = findViewById(R.id.tv_discount_amount);

        llCouponContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCouponPicker();
            }
        });

        llAddressContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 先检查用户是否有地址
                ApiClient.getInstance(OrderActivity.this).getApiService().getAddresses()
                                .enqueue(new Callback<ApiService.Response<List<com.petshop.model.Address>>>() {
                                    @Override
                                    public void onResponse(Call<ApiService.Response<List<com.petshop.model.Address>>> call,
                                                           Response<ApiService.Response<List<com.petshop.model.Address>>> response) {
                                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                                            List<com.petshop.model.Address> addresses = response.body().getData();
                                            if (addresses == null || addresses.isEmpty()) {
                                                // 没有地址，直接跳转到添加新地址
                                                Intent intent = new Intent(OrderActivity.this, AddEditAddressActivity.class);
                                                startActivityForResult(intent, REQUEST_CODE_ADDRESS);
                                            } else {
                                                // 有地址，跳转到地址管理页选择
                                                Intent intent = new Intent(OrderActivity.this, AddressListActivity.class);
                                                intent.putExtra("SELECT_MODE", true);
                                                // 携带当前选中地址ID（如果有）
                                                String currentAddress = tvUserAddress.getText().toString();
                                                if (!currentAddress.isEmpty() && !currentAddress.equals("默认地址")) {
                                                    // 这里需要根据实际情况获取当前地址ID，暂时先传0
                                                    intent.putExtra("CURRENT_ADDRESS_ID", 0);
                                                }
                                                startActivityForResult(intent, REQUEST_CODE_ADDRESS);
                                            }
                                        } else {
                                            // 接口调用失败，跳转到地址管理页
                                            Intent intent = new Intent(OrderActivity.this, AddressListActivity.class);
                                            intent.putExtra("SELECT_MODE", true);
                                            startActivityForResult(intent, REQUEST_CODE_ADDRESS);
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<ApiService.Response<List<com.petshop.model.Address>>> call, Throwable t) {
                                        // 接口调用失败，跳转到地址管理页
                                        Intent intent = new Intent(OrderActivity.this, AddressListActivity.class);
                                        intent.putExtra("SELECT_MODE", true);
                                        startActivityForResult(intent, REQUEST_CODE_ADDRESS);
                                    }
                                });
            }
        });

        llPaymentContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(OrderActivity.this, PaymentMethodActivity.class);
                intent.putExtra(PaymentMethodActivity.EXTRA_AMOUNT, getPayableAmount());
                intent.putExtra(PaymentMethodActivity.EXTRA_SELECTED_CODE, selectedPayMethodCode);
                startActivityForResult(intent, REQUEST_CODE_PAYMENT);
            }
        });

        llRemarkContainer = findViewById(R.id.ll_remark_container);
        tvRemark = findViewById(R.id.tv_remark);
        if (llRemarkContainer != null) {
            llRemarkContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(OrderActivity.this, OrderRemarkActivity.class);
                    intent.putExtra("remark", tvRemark.getText().toString());
                    startActivityForResult(intent, REQUEST_CODE_REMARK);
                }
            });
        }

        btnPay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedItems.isEmpty()) {
                    Toast.makeText(OrderActivity.this, "请先选择心仪的商品吧", Toast.LENGTH_SHORT).show();
                    return;
                }
                showPayConfirmDialog();
            }
        });

        btnLater.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedItems.isEmpty()) {
                    Toast.makeText(OrderActivity.this, "请先选择心仪的商品吧", Toast.LENGTH_SHORT).show();
                    return;
                }
                createOrder(false);
            }
        });
    }

    private void loadCartFromApi() {
        // 先检查是否有从商品详情页传递过来的商品信息
        Intent intent = getIntent();
        android.util.Log.d("OrderLoad", "loadCartFromApi 被调用，Intent 中有 product_id: " + intent.hasExtra("product_id"));

        // 购物车结算：只处理购物车里勾选的商品
        if (intent.hasExtra("cart_items_json")) {
            loadSelectedCartItems(intent.getStringExtra("cart_items_json"));
            return;
        }

        if (intent.hasExtra("product_id")) {
            try {
                // 获取商品信息
                int productId = intent.getIntExtra("product_id", 0);
                String productName = intent.getStringExtra("product_name");
                double productPrice = intent.getDoubleExtra("product_price", 0);
                int productQuantity = intent.getIntExtra("product_quantity", 1);
                totalPrice = intent.getDoubleExtra("total_price", 0);
                
                android.util.Log.d("OrderLoad", "从Intent获取商品: productId=" + productId + ", name=" + productName + ", price=" + productPrice);
                
                // 创建一个临时的CartItemSimple对象
                ApiService.CartItemSimple item = new ApiService.CartItemSimple();
                item.setProductId(productId);
                item.setProductName(productName);
                item.setPrice(productPrice);
                item.setQuantity(productQuantity);
                // 设置商品图片URL，与商品详情页保持一致
                String productImage = intent.getStringExtra("product_image");
                item.setImageUrl(productImage);
                
                // 添加到选中商品列表
                selectedItems.clear();
                selectedItems.add(item);
                
                android.util.Log.d("OrderLoad", "商品已加入 selectedItems，当前数量: " + selectedItems.size() + ", productId=" + selectedItems.get(0).getProductId());
                
                // 更新总价并显示商品
                updateTotalPrice();
                rvOrderItems.setLayoutManager(new LinearLayoutManager(OrderActivity.this));
                rvOrderItems.setAdapter(new OrderAdapter());
            } catch (Exception e) {
                e.printStackTrace();
                android.util.Log.e("OrderLoad", "异常: " + e.getMessage(), e);
                // 异常时从API加载
                loadCartFromApiFallback();
            }
        } else if (intent.hasExtra("selected_items")) {
            try {
                // 尝试获取总价格
                if (intent.hasExtra("total_price")) {
                    totalPrice = intent.getDoubleExtra("total_price", 0);
                }
                
                // 直接从 API 加载购物车，因为我们需要完整的商品信息
                ApiClient.getInstance(this).getApiService().getCart()
                        .enqueue(new Callback<ApiService.Response<List<ApiService.CartItemSimple>>>() {
                            @Override
                            public void onResponse(Call<ApiService.Response<List<ApiService.CartItemSimple>>> call,
                                                   Response<ApiService.Response<List<ApiService.CartItemSimple>>> response) {
                                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                                    List<ApiService.CartItemSimple> items = response.body().getData();
                                    if (items != null) {
                                        selectedItems.clear();
                                        totalPrice = 0;
                                        for (ApiService.CartItemSimple item : items) {
                                            // 确保 productId 被正确设置
                                            if (item.getProductId() == 0 && item.getId() != 0) {
                                                item.setProductId(item.getId());
                                                android.util.Log.d("CartLoad", "修复 productId: " + item.getProductId());
                                            }
                                            selectedItems.add(item);
                                            totalPrice += item.getPrice() * item.getQuantity();
                                            android.util.Log.d("CartLoad", "加载购物车商品: productId=" + item.getProductId() + ", name=" + item.getProductName() + ", imageUrl=" + item.getImageUrl());
                                        }
                                        updateTotalPrice();
                                        rvOrderItems.setLayoutManager(new LinearLayoutManager(OrderActivity.this));
                                        rvOrderItems.setAdapter(new OrderAdapter());
                                    }
                                }
                            }

                            @Override
                            public void onFailure(Call<ApiService.Response<List<ApiService.CartItemSimple>>> call, Throwable t) {
                                android.util.Log.e("CartLoad", "加载购物车失败: " + t.getMessage());
                                Toast.makeText(OrderActivity.this, "加载购物车失败", Toast.LENGTH_SHORT).show();
                            }
                        });
            } catch (Exception e) {
                e.printStackTrace();
                // 异常时从API加载
                loadCartFromApiFallback();
            }
        } else {
            // 没有传递商品信息，从API加载
            loadCartFromApiFallback();
        }
    }
    
    /**
     * 解析购物车传入的勾选商品，未勾选的商品不会出现在订单中
     */
    private void loadSelectedCartItems(String json) {
        try {
            List<ApiService.CartItemSimple> items = new Gson().fromJson(json,
                    new TypeToken<List<ApiService.CartItemSimple>>() {}.getType());
            selectedItems.clear();
            totalPrice = 0;
            if (items != null) {
                for (ApiService.CartItemSimple item : items) {
                    if (item.getProductId() <= 0) {
                        continue;
                    }
                    selectedItems.add(item);
                    totalPrice += item.getPrice() * item.getQuantity();
                }
            }
            if (selectedItems.isEmpty()) {
                Toast.makeText(this, "没有可结算的商品", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            updateTotalPrice();
            rvOrderItems.setLayoutManager(new LinearLayoutManager(OrderActivity.this));
            rvOrderItems.setAdapter(new OrderAdapter());
            LogUtil.d("OrderLoad", "购物车勾选商品数: " + selectedItems.size() + ", 总价: " + totalPrice);
        } catch (Exception e) {
            LogUtil.e("OrderLoad", "解析勾选商品失败", e);
            Toast.makeText(this, "商品信息解析失败，请返回购物车重试", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadCartFromApiFallback() {
        // 从 API 加载购物车
        ApiClient.getInstance(this).getApiService().getCart()
                .enqueue(new Callback<ApiService.Response<List<ApiService.CartItemSimple>>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<List<ApiService.CartItemSimple>>> call,
                                           Response<ApiService.Response<List<ApiService.CartItemSimple>>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                            List<ApiService.CartItemSimple> items = response.body().getData();
                            if (items != null) {
                                selectedItems.clear();
                                totalPrice = 0;
                                for (ApiService.CartItemSimple item : items) {
                                    // 确保 productId 被正确设置
                                    if (item.getProductId() == 0 && item.getId() != 0) {
                                        item.setProductId(item.getId());
                                        android.util.Log.d("CartLoad", "修复 productId: " + item.getProductId());
                                    }
                                    selectedItems.add(item);
                                    totalPrice += item.getPrice() * item.getQuantity();
                                    android.util.Log.d("CartLoad", "加载购物车商品: productId=" + item.getProductId() + ", name=" + item.getProductName() + ", price=" + item.getPrice() + ", imageUrl=" + item.getImageUrl());
                                }
                                updateTotalPrice();
                                rvOrderItems.setLayoutManager(new LinearLayoutManager(OrderActivity.this));
                                rvOrderItems.setAdapter(new OrderAdapter());
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<List<ApiService.CartItemSimple>>> call, Throwable t) {
                        android.util.Log.e("CartLoad", "加载购物车失败: " + t.getMessage());
                        Toast.makeText(OrderActivity.this, "加载购物车失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadProductFromApi(int productId) {
        android.util.Log.d("OrderProduct", "开始加载商品: productId=" + productId);
        if (productId == 0) {
            android.util.Log.e("OrderProduct", "错误: 接收到的 product_id 为 0!");
            Toast.makeText(this, "商品信息异常", Toast.LENGTH_SHORT).show();
            return;
        }
        ApiClient.getInstance(this).getApiService().getProductDetail(productId)
                .enqueue(new Callback<ApiService.Response<com.petshop.model.Product>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<com.petshop.model.Product>> call,
                                           Response<ApiService.Response<com.petshop.model.Product>> response) {
                        android.util.Log.d("OrderProduct", "网络请求结果: " + (response.isSuccessful() ? "成功" : "失败"));
                        try {
                            if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                                com.petshop.model.Product product = response.body().getData();
                                if (product != null) {
                                    selectedItems.clear();
                                    totalPrice = 0;
                                    
                                    // 创建CartItemSimple对象
                                    ApiService.CartItemSimple item = new ApiService.CartItemSimple();
                                    int pid = product.getId();
                                    android.util.Log.d("OrderProduct", "解析商品ID: " + pid + ", 名称: " + product.getName());
                                    item.setProductId(pid); // 设置 productId 用于订单创建
                                    item.setId(pid);
                                    item.setProductName(product.getName());
                                    item.setPrice(product.getPrice());
                                    item.setQuantity(1);
                                    item.setImageUrl(product.getMainImage());
                                    
                                    selectedItems.add(item);
                                    totalPrice += product.getPrice() * 1;
                                    
                                    updateTotalPrice();
                                    rvOrderItems.setLayoutManager(new LinearLayoutManager(OrderActivity.this));
                                    rvOrderItems.setAdapter(new OrderAdapter());
                                    android.util.Log.d("OrderProduct", "商品加载完成，selectedItems 数量: " + selectedItems.size());
                                } else {
                                    android.util.Log.e("OrderProduct", "商品数据为空");
                                }
                            } else {
                                android.util.Log.e("OrderProduct", "请求失败或返回码非200");
                            }
                        } catch (Exception e) {
                            android.util.Log.e("OrderProduct", "异常: " + e.getMessage(), e);
                            ToastUtil.showError(OrderActivity.this, "加载商品失败: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<com.petshop.model.Product>> call, Throwable t) {
                        android.util.Log.e("OrderProduct", "网络请求失败: " + t.getMessage());
                        Toast.makeText(OrderActivity.this, "加载商品信息失败，请稍后重试", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * 应付金额 = 商品总价 - 优惠券抵扣（不小于 0）
     */
    private double getPayableAmount() {
        double payable = totalPrice - discountAmount;
        return payable < 0 ? 0 : payable;
    }

    private void updateTotalPrice() {
        tvProductTotal.setText("¥" + String.format("%.2f", totalPrice));

        // 实付 = 商品总价 - 优惠券抵扣，不允许为负
        double payable = getPayableAmount();
        String payableStr = "¥" + String.format("%.2f", payable);
        tvTotalPrice.setText(payableStr);
        tvBottomTotal.setText(payableStr);

        if (discountAmount > 0) {
            llDiscountRow.setVisibility(View.VISIBLE);
            tvDiscountAmount.setText("-¥" + String.format("%.2f", discountAmount));
        } else {
            llDiscountRow.setVisibility(View.GONE);
        }
        refreshCouponLabel();
    }

    private void refreshCouponLabel() {
        if (selectedUserCouponId != null) {
            tvSelectedCoupon.setText(selectedCouponName != null ? selectedCouponName : "已选择优惠券");
        } else if (!usableCoupons.isEmpty()) {
            tvSelectedCoupon.setText(usableCoupons.size() + " 张可用");
        } else {
            tvSelectedCoupon.setText("暂无可用");
        }
    }

    /**
     * 拉取当前订单金额可用的优惠券，并弹出选择框
     */
    private void showCouponPicker() {
        if (totalPrice <= 0) {
            Toast.makeText(this, "订单金额计算中，请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        ApiClient.getInstance(this).getApiService().getUsableCoupons(totalPrice)
                .enqueue(new Callback<ApiService.Response<List<ApiService.CouponItem>>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<List<ApiService.CouponItem>>> call,
                                           Response<ApiService.Response<List<ApiService.CouponItem>>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                            List<ApiService.CouponItem> coupons = response.body().getData();
                            usableCoupons = coupons != null ? coupons : new ArrayList<>();
                            if (usableCoupons.isEmpty()) {
                                ToastUtil.showInfo(OrderActivity.this, "当前订单没有可用的优惠券");
                                refreshCouponLabel();
                                return;
                            }
                            showCouponDialog(usableCoupons);
                        } else {
                            String msg = response.body() != null ? response.body().getMessage() : "加载优惠券失败";
                            ToastUtil.showError(OrderActivity.this, msg);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<List<ApiService.CouponItem>>> call, Throwable t) {
                        LogUtil.e("OrderActivity", "加载可用优惠券失败", t);
                        ToastUtil.showError(OrderActivity.this, "网络错误，请稍后重试");
                    }
                });
    }

    private void showCouponDialog(final List<ApiService.CouponItem> coupons) {
        final List<String> labels = new ArrayList<>();
        labels.add("不使用优惠券");
        int checkedIndex = 0;
        for (int i = 0; i < coupons.size(); i++) {
            ApiService.CouponItem coupon = coupons.get(i);
            labels.add(buildCouponLabel(coupon));
            if (selectedUserCouponId != null && selectedUserCouponId.equals(coupon.getUserCouponId())) {
                checkedIndex = i + 1;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("选择优惠券")
                .setSingleChoiceItems(labels.toArray(new String[0]), checkedIndex, (dialog, which) -> {
                    if (which == 0) {
                        selectedUserCouponId = null;
                        selectedCouponName = null;
                        discountAmount = 0;
                    } else {
                        ApiService.CouponItem coupon = coupons.get(which - 1);
                        selectedUserCouponId = coupon.getUserCouponId();
                        selectedCouponName = coupon.getName();
                        discountAmount = coupon.getDiscountAmount();
                    }
                    updateTotalPrice();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String buildCouponLabel(ApiService.CouponItem coupon) {
        StringBuilder label = new StringBuilder();
        label.append(coupon.getName() != null ? coupon.getName() : "优惠券");
        label.append("（可抵 ¥").append(String.format("%.2f", coupon.getDiscountAmount())).append("）");
        return label.toString();
    }

    private void createOrder(boolean isPayNow) {
        final String userInfoText = tvUserInfo.getText().toString();
        final String[] userInfoParts = userInfoText.split(" ");
        final String name = userInfoParts.length > 0 ? userInfoParts[0] : "用户";
        final String phone = userInfoParts.length > 1 ? userInfoParts[1] : "15579161054";
        String tempAddress = tvUserAddress.getText().toString();
        if (tempAddress.isEmpty()) {
            tempAddress = "默认地址";
        }
        final String address = tempAddress;
        
        // 首先检查所有商品的库存
        checkStockBeforeOrder(isPayNow, name, phone, address);
    }

    /**
     * 下单前检查库存
     */
    private void checkStockBeforeOrder(final boolean isPayNow, final String name, final String phone, final String address) {
        if (selectedItems.isEmpty()) {
            Toast.makeText(OrderActivity.this, "请先选择心仪的商品吧", Toast.LENGTH_SHORT).show();
            return;
        }
        final ProgressDialog progressDialog = new ProgressDialog(OrderActivity.this);
        progressDialog.setMessage("正在检查库存...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // 记录检查失败的商品
        final List<String> stockErrors = new ArrayList<>();
        final int[] checkedCount = {0};
        final int totalCount = selectedItems.size();
        
        for (final ApiService.CartItemSimple item : selectedItems) {
            Map<String, Integer> request = new HashMap<>();
            request.put("quantity", item.getQuantity());
            
            ApiClient.getInstance(this).getApiService().checkStock(item.getProductId(), request)
                    .enqueue(new Callback<ApiService.Response<ApiService.StockCheckResult>>() {
                        @Override
                        public void onResponse(Call<ApiService.Response<ApiService.StockCheckResult>> call,
                                               Response<ApiService.Response<ApiService.StockCheckResult>> response) {
                            synchronized (checkedCount) {
                                checkedCount[0]++;
                                
                                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                                    ApiService.StockCheckResult result = response.body().getData();
                                    if (result != null && !result.isAvailable()) {
                                        stockErrors.add(item.getProductName() + " 库存不足，当前库存: " + result.getCurrentStock());
                                    }
                                } else {
                                    stockErrors.add(item.getProductName() + " 库存检查失败");
                                }
                                
                                // 检查是否所有商品都已检查完毕
                                if (checkedCount[0] == totalCount) {
                                    progressDialog.dismiss();
                                    if (!stockErrors.isEmpty()) {
                                        // 有库存问题，显示错误信息
                                        StringBuilder errorMsg = new StringBuilder("库存检查失败:\n");
                                        for (String error : stockErrors) {
                                            errorMsg.append("- ").append(error).append("\n");
                                        }
                                        Toast.makeText(OrderActivity.this, errorMsg.toString(), Toast.LENGTH_LONG).show();
                                    } else {
                                        // 库存检查通过，继续创建订单
                                        proceedToCreateOrder(isPayNow, name, phone, address);
                                    }
                                }
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiService.Response<ApiService.StockCheckResult>> call, Throwable t) {
                            synchronized (checkedCount) {
                                checkedCount[0]++;
                                stockErrors.add(item.getProductName() + " 库存检查异常: " + t.getMessage());
                                
                                if (checkedCount[0] == totalCount) {
                                    progressDialog.dismiss();
                                    StringBuilder errorMsg = new StringBuilder("库存检查失败:\n");
                                    for (String error : stockErrors) {
                                        errorMsg.append("- ").append(error).append("\n");
                                    }
                                    Toast.makeText(OrderActivity.this, errorMsg.toString(), Toast.LENGTH_LONG).show();
                                }
                            }
                        }
                    });
        }
    }

    /**
     * 库存检查通过后，继续创建订单
     */
    private void proceedToCreateOrder(boolean isPayNow, final String name, final String phone, final String address) {
        Map<String, Object> orderData = new HashMap<>();
        
        orderData.put("receiver", name);
        orderData.put("phone", phone);
        orderData.put("address", address);

        // 带上已选优惠券，抵扣金额由服务端根据券规则重算
        if (selectedUserCouponId != null) {
            orderData.put("couponId", selectedUserCouponId);
        }
        
        // 添加订单商品信息
        List<Map<String, Object>> items = new ArrayList<>();
        android.util.Log.d("OrderCreate", "开始构建订单商品列表，selectedItems 数量: " + selectedItems.size());
        for (ApiService.CartItemSimple item : selectedItems) {
            android.util.Log.d("OrderCreate", "检查商品: id=" + item.getId() + ", productId=" + item.getProductId() + ", name=" + item.getProductName() + ", price=" + item.getPrice());
            if (item.getProductId() == 0) {
                android.util.Log.e("OrderCreate", "错误: productId 为 0，无法创建订单");
                Toast.makeText(OrderActivity.this, "商品信息异常，请重新选择商品", Toast.LENGTH_LONG).show();
                return;
            }
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("productId", item.getProductId());
            itemMap.put("quantity", item.getQuantity());
            itemMap.put("price", item.getPrice());
            items.add(itemMap);
        }
        orderData.put("items", items);
        
        ApiClient.getInstance(this).getApiService().createOrder(orderData)
                .enqueue(new Callback<ApiService.Response<ApiService.BackendOrder>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<ApiService.BackendOrder>> call,
                                           Response<ApiService.Response<ApiService.BackendOrder>> response) {
                        android.util.Log.d("OrderCreate", "响应状态: " + (response.isSuccessful() ? "成功" : "失败") + ", code=" + response.code());
                        if (response.body() != null) {
                            android.util.Log.d("OrderCreate", "返回码: " + response.body().getCode() + ", 消息: " + response.body().getMessage());
                        }
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                            ApiService.BackendOrder createdOrder = response.body().getData();
                            if (createdOrder != null) {
                                if (isPayNow) {
                                    payCreatedOrder(createdOrder, name, phone, address);
                                } else {
                                    clearSelectedCartItems();
                                    Toast.makeText(OrderActivity.this, "订单提交成功，请尽快完成支付", Toast.LENGTH_SHORT).show();
                                    storeCountdownInfo(createdOrder.getId());
                                    openOrderDetail(createdOrder, false, name, phone, address);
                                }
                            } else {
                                Toast.makeText(OrderActivity.this, R.string.error_operation_failed, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            String errorMsg = response.body() != null ? response.body().getMessage() : "网络错误";
                            android.util.Log.e("OrderCreate", "订单创建失败: " + errorMsg);
                            Toast.makeText(OrderActivity.this, "操作失败: " + errorMsg, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<ApiService.BackendOrder>> call, Throwable t) {
                        Toast.makeText(OrderActivity.this, R.string.error_network_disconnected, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * 支付前二次确认：展示金额与支付方式，避免误触直接扣款
     */
    private void showPayConfirmDialog() {
        String amount = String.format("%.2f", getPayableAmount());
        new AlertDialog.Builder(this)
                .setTitle("确认支付")
                .setMessage("支付金额：¥" + amount + "\n支付方式：" + selectedPayMethodName)
                .setNegativeButton("再想想", null)
                .setPositiveButton("确认支付", (dialog, which) -> createOrder(true))
                .show();
    }

    /**
     * 订单创建成功后发起真实支付：携带用户选择的支付渠道，并展示支付进度
     */
    private void payCreatedOrder(final ApiService.BackendOrder order, final String name,
                                 final String phone, final String address) {
        payProgressDialog = new ProgressDialog(this);
        payProgressDialog.setMessage("正在通过" + selectedPayMethodName + "支付...");
        payProgressDialog.setCancelable(false);
        payProgressDialog.show();

        Map<String, Object> payData = new HashMap<>();
        payData.put("payMethod", selectedPayMethodCode);

        ApiClient.getInstance(this).getApiService().payOrder(order.getId(), payData)
                .enqueue(new Callback<ApiService.Response<ApiService.BackendOrder>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<ApiService.BackendOrder>> call,
                                           Response<ApiService.Response<ApiService.BackendOrder>> response) {
                        dismissPayProgress();
                        boolean success = response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200;
                        ApiService.BackendOrder paidOrder = success ? response.body().getData() : null;
                        boolean paid = paidOrder != null && paidOrder.getStatus() != null
                                && !"待付款".equals(paidOrder.getStatus());
                        if (paid) {
                            openPayResult(paidOrder, name, phone, address);
                        } else {
                            String message = response.body() != null && response.body().getMessage() != null
                                    ? response.body().getMessage() : "支付未完成";
                            Toast.makeText(OrderActivity.this, message + "，订单已生成，可在订单详情继续支付", Toast.LENGTH_LONG).show();
                            storeCountdownInfo(order.getId());
                            openOrderDetail(order, false, name, phone, address);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<ApiService.BackendOrder>> call, Throwable t) {
                        dismissPayProgress();
                        LogUtil.e("OrderActivity", "支付请求失败: orderId=" + order.getId(), t);
                        Toast.makeText(OrderActivity.this, "支付失败，订单已生成，可在订单详情继续支付", Toast.LENGTH_LONG).show();
                        storeCountdownInfo(order.getId());
                        openOrderDetail(order, false, name, phone, address);
                    }
                });
    }

    private void dismissPayProgress() {
        if (payProgressDialog != null && payProgressDialog.isShowing() && !isFinishing()) {
            payProgressDialog.dismiss();
        }
        payProgressDialog = null;
    }

    /**
     * 组装订单详情页参数：支付成功后的结果页与直接跳转共用一份，避免两处参数不一致
     */
    private Bundle buildOrderDetailExtras(ApiService.BackendOrder order, boolean paid,
                                          String name, String phone, String address) {
        Bundle extras = new Bundle();
        extras.putInt("orderId", order.getId());
        extras.putString("orderNo", order.getOrderNo());
        extras.putDouble("totalPrice", order.getTotalPrice() > 0 ? order.getTotalPrice() : getPayableAmount());
        extras.putString("status", order.getStatus());
        extras.putString("payMethodLabel", paid ? selectedPayMethodName : "");
        extras.putString("createdAt", order.getCreatedAt() != null ? order.getCreatedAt().toString() : "");
        extras.putString("receiver", name);
        extras.putString("phone", phone);
        extras.putString("address", address);

        StringBuilder itemsText = new StringBuilder();
        for (ApiService.CartItemSimple item : selectedItems) {
            if (itemsText.length() > 0) {
                itemsText.append("\n");
            }
            itemsText.append(item.getProductName())
                    .append(" x")
                    .append(item.getQuantity())
                    .append("  ¥")
                    .append(item.getPrice());
        }
        extras.putString("itemsText", itemsText.toString());
        return extras;
    }

    /**
     * 跳转订单详情。paid 为 true 表示已付款，展示支付方式；否则引导用户继续支付
     */
    private void openOrderDetail(ApiService.BackendOrder order, boolean paid,
                                 String name, String phone, String address) {
        clearSelectedCartItems();
        Intent intent = new Intent(OrderActivity.this, OrderDetailActivity.class);
        intent.putExtras(buildOrderDetailExtras(order, paid, name, phone, address));
        startActivity(intent);
        finish();
    }

    /**
     * 支付成功：进入支付结果页，由结果页决定去订单详情还是继续逛
     */
    private void openPayResult(ApiService.BackendOrder order,
                               String name, String phone, String address) {
        clearSelectedCartItems();

        Intent intent = new Intent(OrderActivity.this, PayResultActivity.class);
        intent.putExtra(PayResultActivity.EXTRA_AMOUNT,
                order.getTotalPrice() > 0 ? order.getTotalPrice() : getPayableAmount());
        intent.putExtra(PayResultActivity.EXTRA_PAY_METHOD_LABEL, selectedPayMethodName);
        intent.putExtra(PayResultActivity.EXTRA_ORDER_NO, order.getOrderNo());
        intent.putExtra(PayResultActivity.EXTRA_PAID_AT, order.getPaidAt());
        intent.putExtra(PayResultActivity.EXTRA_DETAIL_EXTRAS,
                buildOrderDetailExtras(order, true, name, phone, address));
        startActivity(intent);
        finish();
    }

    /** 支付方式编码 -> 中文名 */
    private static String payMethodLabelOf(String code) {
        if ("ALIPAY".equals(code)) {
            return "支付宝";
        }
        if ("BANK".equals(code)) {
            return "银行卡";
        }
        if ("COD".equals(code)) {
            return "货到付款";
        }
        return "微信支付";
    }

    /**
     * 下单成功后清理已下单项对应的购物车记录。
     * 服务端收到显式 items 时不会自动清购物车，这里必须删干净：
     * 否则残留项会在购物车里再次出现，用户很容易重复下单。
     */
    private void clearSelectedCartItems() {
        for (ApiService.CartItemSimple item : selectedItems) {
            // 立即购买的商品不在购物车中（id 为 0），无需删除
            if (item.getId() <= 0) {
                continue;
            }
            deleteCartItemWithRetry(item.getId(), 1);
        }
    }

    /**
     * 删除单个购物车项，失败自动重试（最多 3 次，退避 500ms/1000ms）
     */
    private void deleteCartItemWithRetry(final int cartItemId, final int attempt) {
        ApiClient.getInstance(this).getApiService().deleteCartItem(cartItemId)
                .enqueue(new Callback<ApiService.Response<Void>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Void>> call, Response<ApiService.Response<Void>> response) {
                        // 404 说明服务端已经没有这条（如已被后端清理），视为清理成功
                        if (response.code() == 404) {
                            return;
                        }
                        boolean ok = response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200;
                        if (!ok) {
                            retryDeleteCartItem(cartItemId, attempt, "业务失败");
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Void>> call, Throwable t) {
                        retryDeleteCartItem(cartItemId, attempt, t.getMessage());
                    }
                });
    }

    private void retryDeleteCartItem(final int cartItemId, final int attempt, String reason) {
        if (attempt < 3) {
            LogUtil.w("OrderActivity", "购物车项删除失败，准备重试: itemId=" + cartItemId
                    + ", attempt=" + attempt + ", reason=" + reason);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    deleteCartItemWithRetry(cartItemId, attempt + 1);
                }
            }, 500L * attempt);
        } else {
            // 重试也失败：留下日志，购物车页面下次刷新会与服务端对齐
            LogUtil.e("OrderActivity", "购物车项删除多次失败，需手动清理: itemId=" + cartItemId, null);
        }
    }

    private void loadUserInfo() {
        // 从 API 获取用户信息
        ApiClient.getInstance(this).getApiService().getUserInfo()
                .enqueue(new Callback<ApiService.Response<com.petshop.model.User>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<com.petshop.model.User>> call,
                                           Response<ApiService.Response<com.petshop.model.User>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                            com.petshop.model.User user = response.body().getData();
                            if (user != null) {
                                String name = user.getUsername() != null ? user.getUsername() : "用户";
                                String phone = user.getPhone() != null ? user.getPhone() : "";
                                tvUserInfo.setText(name + " " + phone);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<com.petshop.model.User>> call, Throwable t) {
                        // 失败时使用默认值
                        tvUserInfo.setText("用户");
                    }
                });
        
        // 加载默认地址
        loadDefaultAddress();
    }
    
    private void loadDefaultAddress() {
        ApiClient.getInstance(this).getApiService().getAddresses()
                .enqueue(new Callback<ApiService.Response<List<com.petshop.model.Address>>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<List<com.petshop.model.Address>>> call,
                                           Response<ApiService.Response<List<com.petshop.model.Address>>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                            List<com.petshop.model.Address> addresses = response.body().getData();
                            if (addresses != null && !addresses.isEmpty()) {
                                // 查找默认地址
                                for (com.petshop.model.Address addr : addresses) {
                                    if (addr.isDefault()) {
                                        String fullAddress = addr.getProvince() + addr.getCity() + 
                                                            addr.getDistrict() + addr.getDetailAddress();
                                        tvUserAddress.setText(fullAddress);
                                        return;
                                    }
                                }
                                // 如果没有默认地址，使用第一个
                                com.petshop.model.Address firstAddr = addresses.get(0);
                                String fullAddress = firstAddr.getProvince() + firstAddr.getCity() + 
                                                    firstAddr.getDistrict() + firstAddr.getDetailAddress();
                                tvUserAddress.setText(fullAddress);
                            } else {
                                tvUserAddress.setText("请选择收货地址");
                            }
                        } else {
                            tvUserAddress.setText("请选择收货地址");
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<List<com.petshop.model.Address>>> call, Throwable t) {
                        tvUserAddress.setText("请选择收货地址");
                    }
                });
    }

    private void storeCountdownInfo(int orderId) {
        // 存储倒计时信息到SharedPreferences
        long startTime = System.currentTimeMillis();
        long expirationTime = startTime + 15 * 60 * 1000; // 15分钟
        
        android.content.SharedPreferences sharedPreferences = getSharedPreferences("order_countdown", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = sharedPreferences.edit();
        String keyStart = "order_" + orderId + "_start_time";
        String keyExpire = "order_" + orderId + "_expiration_time";
        editor.putLong(keyStart, startTime);
        editor.putLong(keyExpire, expirationTime);
        boolean success = editor.commit();
        
        android.util.Log.d("OrderActivity", "存储倒计时信息: orderId=" + orderId + ", startTime=" + startTime + ", expirationTime=" + expirationTime + ", success=" + success);
        android.util.Log.d("OrderActivity", "存储键: " + keyStart + " 和 " + keyExpire);
        
        // 立即验证存储结果
        long storedStart = sharedPreferences.getLong(keyStart, -1);
        long storedExpire = sharedPreferences.getLong(keyExpire, -1);
        android.util.Log.d("OrderActivity", "验证存储结果: storedStart=" + storedStart + ", storedExpire=" + storedExpire);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CODE_ADDRESS) {
                String name = data.getStringExtra("name");
                String phone = data.getStringExtra("phone");
                String address = data.getStringExtra("address");
                if (name != null && phone != null && address != null) {
                    tvUserInfo.setText(name + " " + phone);
                    tvUserAddress.setText(address);
                }
            } else if (requestCode == REQUEST_CODE_PAYMENT) {
                if (data == null) {
                    return;
                }
                String code = data.getStringExtra(PaymentMethodActivity.RESULT_PAYMENT_CODE);
                String name = data.getStringExtra(PaymentMethodActivity.RESULT_PAYMENT_NAME);
                if (code == null || code.isEmpty()) {
                    return;
                }
                selectedPayMethodCode = code;
                selectedPayMethodName = name != null ? name : payMethodLabelOf(code);
                tvSelectedPayment.setText(selectedPayMethodName);
            } else if (requestCode == REQUEST_CODE_REMARK) {
                String remark = data.getStringExtra("remark");
                if (remark != null) {
                    tvRemark.setText(remark);
                }
            }
        }
    }

    private class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {
        
        @NonNull
        @Override
        public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order_product, parent, false);
            return new OrderViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
            ApiService.CartItemSimple item = selectedItems.get(position);
            holder.tvProductName.setText(item.getProductName());
            holder.tvProductPrice.setText("¥" + String.format("%.2f", item.getPrice()));
            holder.tvQuantity.setText(String.valueOf(item.getQuantity()));
            
            // 使用优化后的 GlideUtil 加载商品图片，避免 Blob 数据导致的卡顿
            // 优先使用 item 中的 imageUrl（与商品详情页保持一致）
            String imageUrl = item.getImageUrl();
            String baseUrl = com.petshop.api.ApiClient.getBaseUrl();
            if (imageUrl == null || imageUrl.isEmpty()) {
                imageUrl = baseUrl + "images/product/" + item.getProductId();
            } else if (imageUrl.startsWith("http") && imageUrl.contains("/uploads/")) {
                // 如果是HTTP URL指向上传目录，改用后端BLOB接口
                imageUrl = baseUrl + "images/product/" + item.getProductId();
            } else if (!imageUrl.startsWith("data:") && !imageUrl.startsWith("http")) {
                // 相对路径拼接基础URL
                if (imageUrl.startsWith("/")) {
                    imageUrl = baseUrl + imageUrl.substring(1);
                } else {
                    imageUrl = baseUrl + imageUrl;
                }
            }
            GlideUtil.loadThumbnail(holder.itemView.getContext(), imageUrl, holder.ivProduct);
        }
        
        @Override
        public int getItemCount() {
            return selectedItems.size();
        }
        
        class OrderViewHolder extends RecyclerView.ViewHolder {
            ImageView ivProduct;
            TextView tvProductName;
            TextView tvProductPrice;
            TextView tvQuantity;
            TextView btnMinus;
            TextView btnPlus;
            
            public OrderViewHolder(@NonNull View itemView) {
                super(itemView);
                ivProduct = itemView.findViewById(R.id.iv_product);
                tvProductName = itemView.findViewById(R.id.tv_product_name);
                tvProductPrice = itemView.findViewById(R.id.tv_product_price);
                tvQuantity = itemView.findViewById(R.id.tv_quantity);
                btnMinus = itemView.findViewById(R.id.btn_minus);
                btnPlus = itemView.findViewById(R.id.btn_plus);
                
                btnMinus.setVisibility(View.GONE);
                btnPlus.setVisibility(View.GONE);
            }
        }
    }
}