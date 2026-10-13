package com.petshop.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.petshop.activity.LoginActivity;
import com.petshop.api.ApiClient;
import com.petshop.util.SecurePreferences;

/**
 * 认证工具类
 * 用于检查登录状态和跳转敏感页面
 */
public class AuthUtil {
    private static final String TAG = "AuthUtil";

    /**
     * 检查是否已登录，如果未登录则跳转到登录页面
     * @param context 上下文
     * @return true-已登录，false-未登录
     */
    public static boolean checkLogin(Context context) {
        if (context == null) {
            return false;
        }

        ApiClient apiClient = ApiClient.getInstance(context);
        if (apiClient == null || !apiClient.isLoggedIn()) {
            Log.w(TAG, "用户未登录，跳转到登录页面");
            Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show();
            
            if (context instanceof Activity) {
                Intent intent = new Intent(context, LoginActivity.class);
                context.startActivity(intent);
            }
            return false;
        }
        
        return true;
    }

    /**
     * 检查是否已登录，如果未登录则显示拼多多风格的提示
     * @param context 上下文
     * @return true-已登录，false-未登录
     */
    public static boolean checkLoginWithPddStyle(Context context) {
        if (context == null) {
            return false;
        }

        ApiClient apiClient = ApiClient.getInstance(context);
        if (apiClient == null || !apiClient.isLoggedIn()) {
            Log.w(TAG, "用户未登录，跳转到登录页面");
            Toast.makeText(context, "登录后查看更多精彩内容", Toast.LENGTH_SHORT).show();
            
            if (context instanceof Activity) {
                Intent intent = new Intent(context, LoginActivity.class);
                context.startActivity(intent);
            }
            return false;
        }
        
        return true;
    }

    /**
     * 安全跳转到敏感页面
     * @param context 上下文
     * @param targetClass 目标页面类
     * @return true-已登录，false-未登录
     */
    public static boolean navigateToSensitivePage(Context context, Class<?> targetClass) {
        return checkLoginWithPddStyle(context);
    }

    /**
     * 获取当前用户 ID
     * @param context 上下文
     * @return 用户 ID，未登录返回 -1
     */
    public static int getCurrentUserId(Context context) {
        if (context == null) {
            return -1;
        }
    
        // 优先从加密存储获取用户 ID
        int userId = SecurePreferences.getUserId(context);
        if (userId != -1) {
            return userId;
        }
            
        ApiClient apiClient = ApiClient.getInstance(context);
        if (apiClient == null || !apiClient.isLoggedIn()) {
            return -1;
        }
            
        return apiClient.getUserId();
    }

    /**
     * 检查用户ID是否有效
     * @param context 上下文
     * @return true-有效，false-无效
     */
    public static boolean isValidUserId(Context context) {
        return getCurrentUserId(context) != -1;
    }
}
