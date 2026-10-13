package com.petshop.util;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

import com.petshop.activity.LoginActivity;

/**
 * 错误处理工具类
 * 提供统一的错误处理方法和错误提示
 */
public class ErrorHandler {

    private static final String TAG = "ErrorHandler";

    /**
     * 处理网络错误
     * @param context 上下文
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     */
    public static void handleNetworkError(Context context, int errorCode, String errorMessage) {
        LogUtil.e(TAG, "网络错误: " + errorCode + " - " + errorMessage);

        switch (errorCode) {
            case 401:
                // 未授权，跳转到登录页
                handleUnauthorizedError(context);
                break;
            case 404:
                ToastUtil.showError(context, "请求的资源不存在");
                break;
            case 500:
                ToastUtil.showError(context, "服务器内部错误，请稍后重试");
                break;
            case 502:
                ToastUtil.showError(context, "服务器暂时不可用，请稍后重试");
                break;
            case 504:
                ToastUtil.showError(context, "服务器响应超时，请稍后重试");
                break;
            default:
                ToastUtil.showError(context, errorMessage != null ? errorMessage : "网络异常，请稍后重试");
                break;
        }
    }

    /**
     * 处理未授权错误（401）
     * @param context 上下文
     */
    public static void handleUnauthorizedError(Context context) {
        LogUtil.e(TAG, "未授权错误，跳转到登录页");
        
        // 清除本地登录信息
        com.petshop.api.ApiClient.getInstance(context).clearToken();
        com.petshop.api.ApiClient.getInstance(context).clearUserInfo();
        
        // 跳转到登录页
        Intent intent = new Intent(context, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        
        // 显示登录过期提示
        ToastUtil.showError(context, "登录已过期，请重新登录");
    }

    /**
     * 处理业务错误
     * @param context 上下文
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     */
    public static void handleBusinessError(Context context, int errorCode, String errorMessage) {
        LogUtil.e(TAG, "业务错误: " + errorCode + " - " + errorMessage);
        
        // 根据错误码显示不同的提示信息
        String message = errorMessage != null ? errorMessage : "操作失败，请稍后重试";
        ToastUtil.showError(context, message);
    }

    /**
     * 检查网络连接
     * @param context 上下文
     * @return 是否有网络连接
     */
    public static boolean checkNetwork(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    /**
     * 显示网络连接错误提示
     * @param context 上下文
     */
    public static void showNetworkError(Context context) {
        ToastUtil.showError(context, "网络连接已断开，请检查网络设置");
    }

    /**
     * 处理通用错误
     * @param context 上下文
     * @param throwable 异常
     */
    public static void handleException(Context context, Throwable throwable) {
        LogUtil.e(TAG, "异常: " + throwable.getMessage(), throwable);
        
        String errorMessage;
        if (throwable instanceof java.net.SocketTimeoutException) {
            errorMessage = "网络开小差了，请稍后重试";
        } else if (throwable instanceof java.net.UnknownHostException) {
            errorMessage = "网络连接已断开，请检查网络设置";
        } else if (throwable instanceof java.io.IOException) {
            errorMessage = "网络异常，请稍后重试";
        } else {
            errorMessage = "操作失败，请稍后重试";
        }
        
        ToastUtil.showError(context, errorMessage);
    }

    /**
     * 处理API响应错误
     * @param context 上下文
     * @param code 响应码
     * @param message 响应消息
     */
    public static void handleApiError(Context context, int code, String message) {
        if (code == 401) {
            handleUnauthorizedError(context);
        } else if (code >= 400 && code < 500) {
            handleBusinessError(context, code, message);
        } else if (code >= 500) {
            handleNetworkError(context, code, message);
        }
    }
}
