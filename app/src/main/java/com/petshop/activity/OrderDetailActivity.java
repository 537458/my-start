package com.petshop.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.petshop.R;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.util.AuthUtil;
import com.petshop.util.ToastUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrderDetailActivity extends AppCompatActivity {

    private int orderId;
    private String status;
    private String createdAt;
    private TextView tvOrderNo, tvOrderTime, tvOrderStatus, tvTotalPrice;
    private TextView tvReceiver, tvAddress, tvCountdown, tvEmptyItems;
    private TextView tvGoodsTotal, tvDiscountAmount, tvPaidAmount, tvPayMethod;
    private View llDiscountRow, llPayMethodRow;
    private RecyclerView rvOrderItems;
    private ImageButton btnBack;
    private Button btnCancel, btnPay, btnDelete, btnConfirm, btnReview;
    private LinearLayout llReceiver, llAddress, llCountdown, llBottomBar;
    private View flLoading;
    private CountDownTimer countDownTimer;
    private static final long FIFTEEN_MINUTES = 15 * 60 * 1000;
    private static final int REQUEST_CODE_REVIEW = 2001;
    private ApiService.BackendOrder currentOrder;
    private List<ApiService.OrderItemSimple> orderItemsList = new ArrayList<>();
    // 本次选定的支付方式（与支付方式页、下单页共用一份记忆）
    private String selectedPayMethodCode = "WECHAT";
    private String selectedPayMethodName = "微信支付";

    private final ActivityResultLauncher<Intent> paymentPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                Intent data = result.getData();
                String code = data.getStringExtra(PaymentMethodActivity.RESULT_PAYMENT_CODE);
                String name = data.getStringExtra(PaymentMethodActivity.RESULT_PAYMENT_NAME);
                if (code == null || code.isEmpty()) {
                    return;
                }
                selectedPayMethodCode = code;
                selectedPayMethodName = name != null ? name : code;
                confirmPay();
            });

    private final ActivityResultLauncher<Intent> addressPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    int addressId = data.getIntExtra("addressId", 0);
                    String name = data.getStringExtra("name");
                    String phone = data.getStringExtra("phone");
                    String address = data.getStringExtra("address");

                    tvReceiver.setText((name != null ? name : "") + " " + (phone != null ? phone : ""));
                    tvAddress.setText(address != null ? address : "");
                    updateOrderAddress(addressId, name, phone, address);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!AuthUtil.checkLoginWithPddStyle(this)) {
            finish();
            return;
        }

        setContentView(R.layout.activity_order_detail);
        selectedPayMethodCode = getSharedPreferences("payment_prefs", MODE_PRIVATE)
                .getString("last_method", "WECHAT");
        selectedPayMethodName = payMethodLabelOf(selectedPayMethodCode);
        initView();
        loadDataFromIntent();
        showLoading(true);
        loadOrderFromApi();
    }

    private void initView() {
        btnBack = findViewById(R.id.btn_back);
        tvOrderNo = findViewById(R.id.tv_order_no);
        tvOrderTime = findViewById(R.id.tv_order_time);
        tvOrderStatus = findViewById(R.id.tv_order_status);
        tvTotalPrice = findViewById(R.id.tv_total_price);
        tvReceiver = findViewById(R.id.tv_receiver);
        tvAddress = findViewById(R.id.tv_address);
        tvCountdown = findViewById(R.id.tv_countdown);
        tvEmptyItems = findViewById(R.id.tv_empty_items);
        tvGoodsTotal = findViewById(R.id.tv_goods_total);
        tvDiscountAmount = findViewById(R.id.tv_discount_amount);
        tvPaidAmount = findViewById(R.id.tv_paid_amount);
        tvPayMethod = findViewById(R.id.tv_pay_method);
        llPayMethodRow = findViewById(R.id.ll_pay_method_row);
        llDiscountRow = findViewById(R.id.ll_discount_row);
        rvOrderItems = findViewById(R.id.rv_order_items);
        rvOrderItems.setLayoutManager(new LinearLayoutManager(this));
        btnCancel = findViewById(R.id.btn_cancel);
        btnPay = findViewById(R.id.btn_pay);
        btnDelete = findViewById(R.id.btn_delete);
        btnConfirm = findViewById(R.id.btn_confirm);
        btnReview = findViewById(R.id.btn_review);
        llReceiver = findViewById(R.id.ll_receiver);
        llAddress = findViewById(R.id.ll_address);
        llCountdown = findViewById(R.id.ll_countdown);
        llBottomBar = findViewById(R.id.ll_bottom_bar);
        flLoading = findViewById(R.id.fl_loading);

        btnBack.setOnClickListener(v -> finish());

        btnCancel.setOnClickListener(v -> confirmCancelOrder());
        btnPay.setOnClickListener(v -> choosePaymentAndPay());
        btnDelete.setOnClickListener(v -> confirmDeleteOrder());
        btnConfirm.setOnClickListener(v -> confirmReceipt());
        btnReview.setOnClickListener(v -> openReviewPage());

        View.OnClickListener pickAddress = v -> {
            Intent intent = new Intent(OrderDetailActivity.this, AddressListActivity.class);
            intent.putExtra("isSelectMode", true);
            addressPickerLauncher.launch(intent);
        };
        llReceiver.setOnClickListener(pickAddress);
        llAddress.setOnClickListener(pickAddress);
    }

    private void loadDataFromIntent() {
        Intent intent = getIntent();
        if (intent != null) {
            orderId = intent.getIntExtra("orderId", 0);
        }
    }

    private void showLoading(boolean show) {
        if (flLoading != null) {
            flLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void loadOrderFromApi() {
        if (orderId == 0) {
            showLoading(false);
            return;
        }

        ApiClient.getInstance(this).getApiService().getOrderDetail(orderId)
                .enqueue(new Callback<ApiService.Response<ApiService.BackendOrder>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<ApiService.BackendOrder>> call,
                                           Response<ApiService.Response<ApiService.BackendOrder>> response) {
                        showLoading(false);
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200
                                && response.body().getData() != null) {
                            currentOrder = response.body().getData();
                            updateUIWithOrderData(currentOrder);
                        } else {
                            ToastUtil.showError(OrderDetailActivity.this, "加载订单失败");
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<ApiService.BackendOrder>> call, Throwable t) {
                        showLoading(false);
                        ToastUtil.showError(OrderDetailActivity.this, "网络异常，请重试");
                    }
                });
    }

    private void updateUIWithOrderData(ApiService.BackendOrder order) {
        status = order.getStatus();
        createdAt = order.getCreatedAt();

        tvOrderNo.setText(order.getOrderNo() != null ? order.getOrderNo() : "");
        
        // 处理时间格式，将"T"替换为空格
        String displayTime = createdAt;
        if (displayTime != null && displayTime.contains("T")) {
            displayTime = displayTime.replace('T', ' ');
        }
        tvOrderTime.setText(displayTime != null ? displayTime.substring(0, Math.min(16, displayTime.length())) : "");

        // 金额明细：totalPrice 为抵扣后的实付金额，商品总价需回落上抵扣额
        double totalPrice = order.getTotalPrice();
        double discountAmount = order.getDiscountAmount();
        tvTotalPrice.setText("¥" + String.format("%.2f", totalPrice));
        tvPaidAmount.setText("¥" + String.format("%.2f", totalPrice));
        if (discountAmount > 0) {
            llDiscountRow.setVisibility(View.VISIBLE);
            tvDiscountAmount.setText("-¥" + String.format("%.2f", discountAmount));
            tvGoodsTotal.setText("¥" + String.format("%.2f", totalPrice + discountAmount));
        } else {
            llDiscountRow.setVisibility(View.GONE);
            tvGoodsTotal.setText("¥" + String.format("%.2f", totalPrice));
        }

        // 支付方式：仅已支付的订单展示，带上支付时间
        String payMethodLabel = order.getPayMethodLabel();
        if (payMethodLabel != null && !payMethodLabel.isEmpty()) {
            llPayMethodRow.setVisibility(View.VISIBLE);
            String paidAt = order.getPaidAt();
            if (paidAt != null && !paidAt.isEmpty()) {
                String paidTime = paidAt.replace('T', ' ');
                if (paidTime.length() > 16) {
                    paidTime = paidTime.substring(0, 16);
                }
                tvPayMethod.setText(payMethodLabel + "  " + paidTime);
            } else {
                tvPayMethod.setText(payMethodLabel);
            }
        } else {
            llPayMethodRow.setVisibility(View.GONE);
        }

        tvReceiver.setText((order.getReceiver() != null ? order.getReceiver() : "")
                + " " + (order.getPhone() != null ? order.getPhone() : ""));
        tvAddress.setText(order.getAddress() != null ? order.getAddress() : "");

        updateStatusBadge();

        // 商品列表
        orderItemsList.clear();
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            orderItemsList.addAll(order.getItems());
            tvEmptyItems.setVisibility(View.GONE);
            rvOrderItems.setVisibility(View.VISIBLE);
        } else {
            tvEmptyItems.setVisibility(View.VISIBLE);
            rvOrderItems.setVisibility(View.GONE);
        }
        rvOrderItems.setAdapter(new OrderItemAdapter());

        updateButtonVisibility();
        setupCountdown();
    }

    private void updateStatusBadge() {
        if (status == null) {
            status = "待付款";
        }

        // 状态映射：待评价 -> 已取消，待收货 -> 已完成
        String displayStatus = mapOrderStatus(status);
        tvOrderStatus.setText(displayStatus);
        switch (displayStatus) {
            case "待付款":
                tvOrderStatus.setTextColor(0xFFE65100);
                tvOrderStatus.setBackgroundResource(R.drawable.bg_order_status_pending);
                break;
            case "已付款":
            case "已发货":
                tvOrderStatus.setTextColor(0xFF2E7D32);
                tvOrderStatus.setBackgroundResource(R.drawable.bg_order_status_paid);
                break;
            case "已完成":
            case "已签收":
                tvOrderStatus.setTextColor(0xFF1565C0);
                tvOrderStatus.setBackgroundResource(R.drawable.bg_order_status_paid);
                break;
            case "已取消":
                tvOrderStatus.setTextColor(0xFF757575);
                tvOrderStatus.setBackgroundResource(R.drawable.bg_order_status_cancelled);
                break;
            default:
                tvOrderStatus.setTextColor(0xFF757575);
                tvOrderStatus.setBackgroundResource(R.drawable.bg_order_status_cancelled);
                break;
        }
    }

    private void setupCountdown() {
        stopCountdown();

        if (status == null || !status.equals("待付款")) {
            llCountdown.setVisibility(View.GONE);
            return;
        }

        long expirationTime = getCountdownExpirationTime(orderId);
        long currentTime = System.currentTimeMillis();
        long remainingTime = expirationTime - currentTime;

        if (remainingTime <= 0) {
            tvCountdown.setText("订单已超时，即将自动取消");
            llCountdown.setVisibility(View.VISIBLE);
            status = "已取消";
            updateStatusBadge();
            updateButtonVisibility();
            return;
        }

        startCountdownTimer(remainingTime);
    }

    private void startCountdownTimer(long millisInFuture) {
        millisInFuture = Math.min(millisInFuture, FIFTEEN_MINUTES);

        countDownTimer = new CountDownTimer(millisInFuture, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long minutes = millisUntilFinished / (60 * 1000);
                long seconds = (millisUntilFinished % (60 * 1000)) / 1000;
                tvCountdown.setText(String.format("请在 %02d:%02d 内完成支付，超时将自动取消", minutes, seconds));
                llCountdown.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFinish() {
                tvCountdown.setText("订单已超时，即将自动取消");
                llCountdown.setVisibility(View.VISIBLE);
                status = "已取消";
                updateStatusBadge();
                updateButtonVisibility();
            }
        };
        countDownTimer.start();
    }

    private void stopCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    private void updateButtonVisibility() {
        if (status == null) {
            status = "待付款";
        }

        boolean isFinished = "已完成".equals(status) || "已取消".equals(status) || "已签收".equals(status);
        // 服务端只允许删除已完成/已取消的订单，已签收需先走确认收货
        boolean canDelete = "已完成".equals(status) || "已取消".equals(status);
        boolean isPending = "待付款".equals(status);
        boolean isShipped = "已发货".equals(status);
        boolean reviewed = currentOrder != null && Boolean.TRUE.equals(currentOrder.getReviewed());

        btnDelete.setVisibility(canDelete ? View.VISIBLE : View.GONE);
        btnCancel.setVisibility(isPending ? View.VISIBLE : View.GONE);
        btnPay.setVisibility(isPending ? View.VISIBLE : View.GONE);
        btnConfirm.setVisibility(isShipped ? View.VISIBLE : View.GONE);
        // 订单完成后才能评价，且一单只能评价一次
        btnReview.setVisibility(isFinished && !"已取消".equals(status) && !reviewed ? View.VISIBLE : View.GONE);

        // 确保底部栏有内容可显示
        boolean hasButtons = isFinished || isPending || isShipped;
        llBottomBar.setVisibility(hasButtons ? View.VISIBLE : View.GONE);
    }

    /**
     * 确认收货：已发货 -> 已完成
     */
    private void confirmReceipt() {
        if (orderId == 0) return;
        new AlertDialog.Builder(this)
                .setTitle("确认收货")
                .setMessage("确认已收到商品吗？确认后订单将完成。")
                .setPositiveButton("确认收货", (dialog, which) -> {
                    showLoading(true);
                    ApiClient.getInstance(this).getApiService().confirmReceipt(orderId)
                            .enqueue(new Callback<ApiService.Response<ApiService.BackendOrder>>() {
                                @Override
                                public void onResponse(Call<ApiService.Response<ApiService.BackendOrder>> call,
                                                       Response<ApiService.Response<ApiService.BackendOrder>> response) {
                                    showLoading(false);
                                    if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                                        status = "已完成";
                                        if (currentOrder != null) {
                                            currentOrder.setStatus(status);
                                        }
                                        updateStatusBadge();
                                        updateButtonVisibility();
                                        ToastUtil.showSuccess(OrderDetailActivity.this, "确认收货成功");
                                    } else {
                                        ToastUtil.showError(OrderDetailActivity.this, "确认收货失败，请重试");
                                    }
                                }

                                @Override
                                public void onFailure(Call<ApiService.Response<ApiService.BackendOrder>> call, Throwable t) {
                                    showLoading(false);
                                    ToastUtil.showError(OrderDetailActivity.this, "网络异常，确认收货失败");
                                }
                            });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 跳转评价页，把订单内商品一并带入
     */
    private void openReviewPage() {
        if (orderId == 0) return;
        Intent intent = new Intent(this, WriteReviewActivity.class);
        intent.putExtra("orderId", orderId);
        startActivityForResult(intent, REQUEST_CODE_REVIEW);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_REVIEW && resultCode == RESULT_OK) {
            // 评价完成后刷新订单，隐藏“去评价”入口
            loadOrderFromApi();
        }
    }

    private void confirmCancelOrder() {
        new AlertDialog.Builder(this)
                .setTitle("取消订单")
                .setMessage("确定要取消该订单吗？")
                .setPositiveButton("确定取消", (dialog, which) -> cancelOrder())
                .setNegativeButton("再想想", null)
                .show();
    }

    private void confirmDeleteOrder() {
        new AlertDialog.Builder(this)
                .setTitle("删除订单")
                .setMessage("确定要删除该订单吗？删除后不可恢复。")
                .setPositiveButton("确定删除", (dialog, which) -> deleteOrder())
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 支付第一步：选支付方式。默认选中用户上次使用的渠道，选完自动进入确认框
     */
    private void choosePaymentAndPay() {
        if (orderId == 0) {
            return;
        }
        double amount = currentOrder != null ? currentOrder.getTotalPrice() : 0;
        Intent intent = new Intent(this, PaymentMethodActivity.class);
        intent.putExtra(PaymentMethodActivity.EXTRA_AMOUNT, amount);
        intent.putExtra(PaymentMethodActivity.EXTRA_SELECTED_CODE, selectedPayMethodCode);
        paymentPickerLauncher.launch(intent);
    }

    /** 支付第二步：二次确认，避免误触直接扣款 */
    private void confirmPay() {
        double amount = currentOrder != null ? currentOrder.getTotalPrice() : 0;
        String amountText = String.format("%.2f", amount);
        new AlertDialog.Builder(this)
                .setTitle("确认支付")
                .setMessage("支付金额：¥" + amountText + "\n支付方式：" + selectedPayMethodName)
                .setNegativeButton("再想想", null)
                .setPositiveButton("确认支付", (dialog, which) -> payOrder(selectedPayMethodCode))
                .show();
    }

    /**
     * 发起支付：将选定的支付渠道一并提交，服务端会校验渠道是否合法
     */
    private void payOrder(String payMethod) {
        if (orderId == 0) {
            return;
        }

        showLoading(true);
        Map<String, Object> payData = new HashMap<>();
        payData.put("payMethod", payMethod);

        ApiClient.getInstance(this).getApiService().payOrder(orderId, payData)
                .enqueue(new Callback<ApiService.Response<ApiService.BackendOrder>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<ApiService.BackendOrder>> call,
                                           Response<ApiService.Response<ApiService.BackendOrder>> response) {
                        showLoading(false);
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                            stopCountdown();
                            clearCountdownInfo();
                            ApiService.BackendOrder paidOrder = response.body().getData();
                            if (paidOrder != null) {
                                currentOrder = paidOrder;
                                updateUIWithOrderData(paidOrder);
                            } else {
                                status = "已付款";
                                updateStatusBadge();
                                updateButtonVisibility();
                            }
                            // 支付成功后进入结果页；返回时回到已刷新过的详情页
                            openPayResult(paidOrder);
                        } else {
                            String message = response.body() != null && response.body().getMessage() != null
                                    ? response.body().getMessage() : "支付失败，请重试";
                            ToastUtil.showError(OrderDetailActivity.this, message);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<ApiService.BackendOrder>> call, Throwable t) {
                        showLoading(false);
                        ToastUtil.showError(OrderDetailActivity.this, "网络异常，支付失败");
                    }
                });
    }

    /**
     * 支付成功后跳转结果页；fromDetail=true 使结果页的“查看订单”直接回到当前详情页
     */
    private void openPayResult(ApiService.BackendOrder paidOrder) {
        double amount = currentOrder != null ? currentOrder.getTotalPrice() : 0;
        Intent intent = new Intent(this, PayResultActivity.class);
        intent.putExtra(PayResultActivity.EXTRA_AMOUNT, amount);
        intent.putExtra(PayResultActivity.EXTRA_PAY_METHOD_LABEL, selectedPayMethodName);
        intent.putExtra(PayResultActivity.EXTRA_ORDER_NO,
                currentOrder != null ? currentOrder.getOrderNo() : "");
        intent.putExtra(PayResultActivity.EXTRA_PAID_AT,
                paidOrder != null && paidOrder.getPaidAt() != null ? paidOrder.getPaidAt() : "");
        intent.putExtra(PayResultActivity.EXTRA_FROM_DETAIL, true);
        startActivity(intent);
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

    private void cancelOrder() {
        if (orderId == 0) return;

        showLoading(true);
        ApiClient.getInstance(this).getApiService().cancelOrder(orderId)
                .enqueue(new Callback<ApiService.Response<ApiService.BackendOrder>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<ApiService.BackendOrder>> call,
                                           Response<ApiService.Response<ApiService.BackendOrder>> response) {
                        showLoading(false);
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                            stopCountdown();
                            clearCountdownInfo();
                            status = "已取消";
                            updateStatusBadge();
                            updateButtonVisibility();
                            ToastUtil.showSuccess(OrderDetailActivity.this, "订单已取消");
                        } else {
                            ToastUtil.showError(OrderDetailActivity.this, "取消失败，请重试");
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<ApiService.BackendOrder>> call, Throwable t) {
                        showLoading(false);
                        ToastUtil.showError(OrderDetailActivity.this, "网络异常，取消失败");
                    }
                });
    }

    private void deleteOrder() {
        if (orderId == 0) return;

        showLoading(true);
        ApiClient.getInstance(this).getApiService().deleteOrder(orderId)
                .enqueue(new Callback<ApiService.Response<Void>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Void>> call,
                                           Response<ApiService.Response<Void>> response) {
                        showLoading(false);
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                            ToastUtil.showSuccess(OrderDetailActivity.this, "订单已删除");
                            setResult(2001);
                            finish();
                        } else {
                            // 服务端会拒绝删除进行中的订单，把具体原因透给用户
                            String message = response.body() != null && response.body().getMessage() != null
                                    ? response.body().getMessage() : "删除失败，请重试";
                            ToastUtil.showError(OrderDetailActivity.this, message);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Void>> call, Throwable t) {
                        showLoading(false);
                        ToastUtil.showError(OrderDetailActivity.this, "网络异常，删除失败");
                    }
                });
    }

    private void updateOrderAddress(int addressId, String name, String phone, String address) {
        if (orderId == 0 || currentOrder == null) return;

        currentOrder.setReceiver(name);
        currentOrder.setPhone(phone);
        currentOrder.setAddress(address);

        ApiClient.getInstance(this).getApiService().updateOrder(orderId, currentOrder)
                .enqueue(new Callback<ApiService.Response<ApiService.BackendOrder>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<ApiService.BackendOrder>> call,
                                           Response<ApiService.Response<ApiService.BackendOrder>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                            ToastUtil.showSuccess(OrderDetailActivity.this, "地址已更新");
                        } else {
                            ToastUtil.showError(OrderDetailActivity.this, "地址更新失败");
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<ApiService.BackendOrder>> call, Throwable t) {
                        ToastUtil.showError(OrderDetailActivity.this, "地址更新失败");
                    }
                });
    }

    private long getCountdownExpirationTime(int orderId) {
        android.content.SharedPreferences prefs = getSharedPreferences("order_countdown", MODE_PRIVATE);
        String keyExpire = "order_" + orderId + "_expiration_time";
        long expirationTime = prefs.getLong(keyExpire, 0);

        long currentTime = System.currentTimeMillis();
        if (expirationTime == 0 || expirationTime < currentTime) {
            expirationTime = currentTime + FIFTEEN_MINUTES;
            String keyStart = "order_" + orderId + "_start_time";
            prefs.edit().putLong(keyStart, currentTime).putLong(keyExpire, expirationTime).apply();
        }

        return expirationTime;
    }

    private void clearCountdownInfo() {
        android.content.SharedPreferences prefs = getSharedPreferences("order_countdown", MODE_PRIVATE);
        prefs.edit()
                .remove("order_" + orderId + "_start_time")
                .remove("order_" + orderId + "_expiration_time")
                .apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCountdown();
    }

    private class OrderItemAdapter extends RecyclerView.Adapter<OrderItemAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_order_detail_product, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ApiService.OrderItemSimple item = orderItemsList.get(position);
            holder.tvProductName.setText(item.getProductName());
            holder.tvProductPrice.setText("¥" + String.format("%.2f", item.getPrice()));
            holder.tvQuantity.setText("x" + item.getQuantity());

            String imageUrl = item.getImageUrl();
            String baseUrl = com.petshop.api.ApiClient.getBaseUrl();
            if (imageUrl == null || imageUrl.isEmpty()) {
                imageUrl = baseUrl + "images/product/" + item.getProductId();
            } else if (imageUrl.startsWith("http") && imageUrl.contains("/uploads/")) {
                // 如果是HTTP URL指向上传目录，改用后端BLOB接口
                imageUrl = baseUrl + "images/product/" + item.getProductId();
            } else if (!imageUrl.startsWith("data:") && !imageUrl.startsWith("http")) {
                if (imageUrl.startsWith("/")) {
                    imageUrl = baseUrl + imageUrl.substring(1);
                } else {
                    imageUrl = baseUrl + imageUrl;
                }
            }
            com.petshop.util.GlideUtil.loadThumbnail(holder.itemView.getContext(), imageUrl, holder.ivProductImage);
        }

        @Override
        public int getItemCount() {
            return orderItemsList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivProductImage;
            TextView tvProductName;
            TextView tvProductPrice;
            TextView tvQuantity;

            ViewHolder(View itemView) {
                super(itemView);
                ivProductImage = itemView.findViewById(R.id.iv_product_image);
                tvProductName = itemView.findViewById(R.id.tv_product_name);
                tvProductPrice = itemView.findViewById(R.id.tv_product_price);
                tvQuantity = itemView.findViewById(R.id.tv_quantity);
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
}
