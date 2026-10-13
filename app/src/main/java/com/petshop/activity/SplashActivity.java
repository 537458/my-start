package com.petshop.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.petshop.R;
import com.petshop.api.ApiClient;
import com.petshop.util.LogUtil;

/**
 * 引导页Activity
 * 应用启动时显示，用于初始化应用数据和检查登录状态
 */
public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final long SPLASH_DURATION = 2000; // 2秒
    private static final String PREF_NAME = "petshop_prefs";
    private static final String FIRST_LAUNCH_KEY = "first_launch";

    private final Handler splashHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // 延迟2秒后进入主页面或登录页面
        splashHandler.postDelayed(this::checkFirstLaunch, SPLASH_DURATION);
    }

    @Override
    protected void onDestroy() {
        // 页面销毁后不再跳转，避免已退出的启动页又把用户拉进下一个页面
        splashHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /**
     * 检查是否首次启动应用
     */
    private void checkFirstLaunch() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean(FIRST_LAUNCH_KEY, true);

        if (isFirstLaunch) {
            // 首次启动，显示引导页
            showIntroActivity();
        } else {
            // 非首次启动，检查登录状态
            checkLoginStatus();
        }
    }

    /**
     * 显示引导页
     */
    private void showIntroActivity() {
        // 标记为非首次启动
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(FIRST_LAUNCH_KEY, false).apply();

        // 跳转到引导页
        Intent intent = new Intent(this, IntroActivity.class);
        startActivity(intent);
        finish();
    }

    /**
     * 检查登录状态
     */
    private void checkLoginStatus() {
        boolean isLoggedIn = ApiClient.getInstance(this).isLoggedIn();
        LogUtil.d(TAG, "登录状态: " + isLoggedIn);

        if (isLoggedIn) {
            // 已登录，跳转到主页面
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        } else {
            // 未登录，跳转到登录页面
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        }
        finish();
    }
}
