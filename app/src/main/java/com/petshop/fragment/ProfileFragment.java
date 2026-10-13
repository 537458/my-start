package com.petshop.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.petshop.R;
import com.petshop.activity.LoginActivity;
import com.petshop.activity.OrderListActivity;
import com.petshop.activity.UserInfoEditActivity;
import com.petshop.util.GlideUtil;


import com.petshop.activity.AIAssistantActivity;
import com.petshop.activity.CouponActivity;
import com.petshop.activity.FavoriteActivity;
import com.petshop.activity.NotificationActivity;
import com.petshop.activity.PetListActivity;
import com.petshop.activity.SettingsActivity;
import com.petshop.activity.AboutUsActivity;
import com.petshop.activity.AddressListActivity;
import com.petshop.api.ApiClient;
import com.petshop.model.User;
import com.petshop.util.AuthUtil;
import com.petshop.util.AvatarUtil;

public class ProfileFragment extends Fragment {

    private TextView tvUsername, tvUserDesc;
    private ImageView ivAvatar;
    private Button btnLogout;
    private LinearLayout llOrderStatus1, llOrderStatus2, llOrderStatus3, llOrderStatus4, llUserInfo, llAllOrders;
    private RelativeLayout llAiAssistant, llAddressManagement, llSettings, llAbout;
    private RelativeLayout llFavorite, llCoupon, llNotification, llMyPets;
    private TextView tvUnreadBadge;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        initView(view);
        initData();
        initListeners();
        return view;
    }

    private void initView(View view) {
        ivAvatar = view.findViewById(R.id.iv_avatar);
        tvUsername = view.findViewById(R.id.tv_username);
        tvUserDesc = view.findViewById(R.id.tv_user_desc);
        btnLogout = view.findViewById(R.id.btn_logout);
        llUserInfo = view.findViewById(R.id.ll_user_info);
        llAllOrders = view.findViewById(R.id.tv_all_orders);
        llOrderStatus1 = view.findViewById(R.id.ll_order_status_1);
        llOrderStatus2 = view.findViewById(R.id.ll_order_status_2);
        llOrderStatus3 = view.findViewById(R.id.ll_order_status_3);
        llOrderStatus4 = view.findViewById(R.id.ll_order_status_4);
        llAiAssistant = view.findViewById(R.id.rl_ai_assistant);
        llAddressManagement = view.findViewById(R.id.rl_address_management);
        llSettings = view.findViewById(R.id.rl_settings);
        llAbout = view.findViewById(R.id.rl_about);
        llFavorite = view.findViewById(R.id.rl_favorite);
        llCoupon = view.findViewById(R.id.rl_coupon);
        llNotification = view.findViewById(R.id.rl_notification);
        llMyPets = view.findViewById(R.id.rl_my_pets);
        tvUnreadBadge = view.findViewById(R.id.tv_unread_badge);
        
        // 默认头像统一使用占位头像，与编辑页保持一致
        ivAvatar.setImageResource(R.drawable.ic_avatar_placeholder);
        
        // 加载用户头像
        loadUserAvatar();
    }

    private void initData() {
        // 检查登录状态
        if (getContext() != null) {
            ApiClient apiClient = ApiClient.getInstance(getContext());
            if (apiClient != null && apiClient.isLoggedIn()) {
                // 已登录，显示用户信息
                String userInfo = apiClient.getUserInfo();
                if (userInfo != null) {
                    try {
                        User user = new Gson().fromJson(userInfo, User.class);
                        if (user != null && user.getUsername() != null) {
                            tvUsername.setText(user.getUsername());
                        } else {
                            tvUsername.setText("用户");
                        }
                    } catch (Exception e) {
                        tvUsername.setText("用户");
                    }
                    tvUserDesc.setText("宠物爱好者");
                } else {
                    tvUsername.setText("用户");
                    tvUserDesc.setText("宠物爱好者");
                }
                // 显示退出登录按钮
                btnLogout.setVisibility(View.VISIBLE);
            } else {
                // 未登录，显示登录提示
                tvUsername.setText("未登录");
                tvUserDesc.setText("点击登录，享受更多服务");
                // 隐藏退出登录按钮
                btnLogout.setVisibility(View.GONE);
            }
        } else {
            // 上下文为null，显示未登录状态
            tvUsername.setText("未登录");
            tvUserDesc.setText("点击登录，享受更多服务");
            btnLogout.setVisibility(View.GONE);
        }
    }
    
    /**
     * 加载用户头像
     */
    private void loadUserAvatar() {
        if (getContext() == null) {
            return;
        }

        ApiClient apiClient = ApiClient.getInstance(getContext());
        if (apiClient == null || !apiClient.isLoggedIn()) {
            // 未登录时不能留着上一个账号的头像
            clearUserAvatar();
            return;
        }

        // 统一走 AvatarUtil：地址里固定带账号 ID 与版本号，
        // 换账号或改头像后地址必然变化，不会命中上一个人的图片缓存
        Log.d("ProfileFragment", "加载头像: " + AvatarUtil.currentAvatarUrl(getContext()));
        AvatarUtil.loadCurrentAvatar(getContext(), ivAvatar);
    }

    /**
     * 恢复默认头像显示，避免退出登录后还显示上一个账号的照片
     */
    private void clearUserAvatar() {
        if (ivAvatar == null || getContext() == null) {
            return;
        }
        Glide.with(this).clear(ivAvatar);
        ivAvatar.setImageResource(R.drawable.ic_avatar_placeholder);
    }

    private void initListeners() {
        // 用户信息区域点击事件
        llUserInfo.setOnClickListener(v -> {
            if (getContext() != null) {
                ApiClient apiClient = ApiClient.getInstance(getContext());
                if (apiClient == null || !apiClient.isLoggedIn()) {
                    // 未登录，跳转到登录页面
                    Intent intent = new Intent(getContext(), LoginActivity.class);
                    startActivity(intent);
                } else {
                    // 已登录，跳转到个人信息编辑页面
                    Intent intent = new Intent(getContext(), UserInfoEditActivity.class);
                    startActivity(intent);
                }
            }
        });
        
        // 退出登录按钮点击事件
        btnLogout.setOnClickListener(v -> {
            if (getContext() != null) {
                ApiClient apiClient = ApiClient.getInstance(getContext());
                if (apiClient != null) {
                    apiClient.clearToken();
                    apiClient.clearUserInfo();
                    apiClient.clearUserId();
                    // 换账号前清掉内存里的头像，并恢复默认头像
                    GlideUtil.clearMemoryCache(getContext());
                    clearUserAvatar();
                    Toast.makeText(getContext(), "已退出登录", Toast.LENGTH_SHORT).show();
                    // 刷新页面状态
                    initData();
                }
            }
        });

        // 订单状态点击事件
        llAllOrders.setOnClickListener(v -> {
            if (getContext() != null) {
                if (AuthUtil.navigateToSensitivePage(getContext(), OrderListActivity.class)) {
                    Intent intent = new Intent(getContext(), OrderListActivity.class);
                    intent.putExtra("status", "all");
                    startActivity(intent);
                }
            }
        });
        llOrderStatus1.setOnClickListener(v -> {
            if (getContext() != null) {
                if (AuthUtil.navigateToSensitivePage(getContext(), OrderListActivity.class)) {
                    Intent intent = new Intent(getContext(), OrderListActivity.class);
                    intent.putExtra("status", "pending_payment");
                    startActivity(intent);
                }
            }
        });
        llOrderStatus2.setOnClickListener(v -> {
            if (getContext() != null) {
                if (AuthUtil.navigateToSensitivePage(getContext(), OrderListActivity.class)) {
                    Intent intent = new Intent(getContext(), OrderListActivity.class);
                    intent.putExtra("status", "pending_delivery");
                    startActivity(intent);
                }
            }
        });
        llOrderStatus3.setOnClickListener(v -> {
            if (getContext() != null) {
                if (AuthUtil.navigateToSensitivePage(getContext(), OrderListActivity.class)) {
                    Intent intent = new Intent(getContext(), OrderListActivity.class);
                    intent.putExtra("status", "pending_receipt");
                    startActivity(intent);
                }
            }
        });
        llOrderStatus4.setOnClickListener(v -> {
            if (getContext() != null) {
                if (AuthUtil.navigateToSensitivePage(getContext(), OrderListActivity.class)) {
                    Intent intent = new Intent(getContext(), OrderListActivity.class);
                    intent.putExtra("status", "completed");
                    startActivity(intent);
                }
            }
        });

        // AI 助手点击事件
        llAiAssistant.setOnClickListener(v -> {
            if (getContext() != null) {
                if (AuthUtil.navigateToSensitivePage(getContext(), AIAssistantActivity.class)) {
                    Intent intent = new Intent(getContext(), AIAssistantActivity.class);
                    startActivity(intent);
                }
            }
        });

        // 地址管理点击事件
        llAddressManagement.setOnClickListener(v -> {
            Log.d("ProfileFragment", "地址管理被点击");
            if (getContext() != null) {
                Log.d("ProfileFragment", "getContext()不为null");
                if (AuthUtil.navigateToSensitivePage(getContext(), AddressListActivity.class)) {
                    Log.d("ProfileFragment", "已登录，跳转到地址列表页面");
                    Intent intent = new Intent(getContext(), AddressListActivity.class);
                    Log.d("ProfileFragment", "Intent创建成功");
                    startActivity(intent);
                    Log.d("ProfileFragment", "startActivity调用成功");
                }
            } else {
                Log.e("ProfileFragment", "getContext()为null");
            }
        });

        // 设置点击事件（功能菜单中的设置项）
        llSettings.setOnClickListener(v -> {
            // 跳转到设置页面
            if (getActivity() != null) {
                Intent intent = new Intent(getActivity(), SettingsActivity.class);
                startActivity(intent);
            }
        });

        // 关于我们点击事件
        llAbout.setOnClickListener(v -> {
            // 跳转到关于我们页面
            if (getActivity() != null) {
                Intent intent = new Intent(getActivity(), AboutUsActivity.class);
                startActivity(intent);
            }
        });

        // 我的宠物
        llMyPets.setOnClickListener(v -> openSensitivePage(PetListActivity.class));

        // 我的收藏
        llFavorite.setOnClickListener(v -> openSensitivePage(FavoriteActivity.class));

        // 优惠券
        llCoupon.setOnClickListener(v -> openSensitivePage(CouponActivity.class));

        // 消息中心
        llNotification.setOnClickListener(v -> openSensitivePage(NotificationActivity.class));
    }

    /**
     * 需要登录才能进入的页面，未登录时先引导登录
     */
    private void openSensitivePage(Class<?> target) {
        if (getContext() == null) {
            return;
        }
        if (AuthUtil.navigateToSensitivePage(getContext(), target)) {
            startActivity(new Intent(getContext(), target));
        }
    }

    /**
     * 加载未读消息数，用于消息入口的红点角标
     */
    private void loadUnreadCount() {
        if (getContext() == null || tvUnreadBadge == null) {
            return;
        }
        ApiClient apiClient = ApiClient.getInstance(getContext());
        if (apiClient == null || !apiClient.isLoggedIn()) {
            tvUnreadBadge.setVisibility(View.GONE);
            return;
        }
        apiClient.getApiService().getUnreadCount().enqueue(new retrofit2.Callback<com.petshop.api.ApiService.Response<Integer>>() {
            @Override
            public void onResponse(retrofit2.Call<com.petshop.api.ApiService.Response<Integer>> call,
                                   retrofit2.Response<com.petshop.api.ApiService.Response<Integer>> response) {
                Integer count = null;
                if (response.isSuccessful() && response.body() != null) {
                    count = response.body().getData();
                }
                if (count == null || count <= 0) {
                    tvUnreadBadge.setVisibility(View.GONE);
                    return;
                }
                tvUnreadBadge.setText(count > 99 ? "99+" : String.valueOf(count));
                tvUnreadBadge.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFailure(retrofit2.Call<com.petshop.api.ApiService.Response<Integer>> call, Throwable t) {
                tvUnreadBadge.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // 页面 resumed 时重新检查登录状态并更新UI
        initData();
        // 重新加载用户头像
        loadUserAvatar();
        // 刷新未读消息角标
        loadUnreadCount();
    }
}
