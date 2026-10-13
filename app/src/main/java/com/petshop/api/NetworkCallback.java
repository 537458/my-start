package com.petshop.api;

import android.content.Context;

import com.petshop.util.LogUtil;
import com.petshop.util.ToastUtil;
import com.petshop.util.ErrorHandler;
import com.petshop.view.LoadingView;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public abstract class NetworkCallback<T> implements Callback<ApiService.Response<T>> {

    private Context context;
    private LoadingView loadingView;
    private boolean showLoading;
    private boolean showErrorToast;

    public NetworkCallback(Context context) {
        this(context, true, true);
    }

    public NetworkCallback(Context context, boolean showLoading, boolean showErrorToast) {
        this.context = context;
        this.showLoading = showLoading;
        this.showErrorToast = showErrorToast;
        
        if (showLoading) {
            loadingView = new LoadingView(context);
        }
    }

    @Override
    public void onResponse(Call<ApiService.Response<T>> call, Response<ApiService.Response<T>> response) {
        hideLoading();
        
        if (response.isSuccessful() && response.body() != null) {
            ApiService.Response<T> apiResponse = response.body();
            
            if (apiResponse.getCode() == 200) {
                onSuccess(apiResponse.getData());
            } else if (apiResponse.getCode() == 401) {
                onUnauthorized(apiResponse.getMessage());
            } else {
                onError(apiResponse.getCode(), apiResponse.getMessage());
            }
        } else {
            LogUtil.e("NetworkCallback", "响应失败: " + response.code() + " " + response.message());
            onFailure(call, new IOException("服务器响应错误: " + response.code()));
        }
    }

    @Override
    public void onFailure(Call<ApiService.Response<T>> call, Throwable t) {
        hideLoading();
        
        String errorMessage = getErrorMessage(t);
        LogUtil.e("NetworkCallback", "请求失败: " + errorMessage, t);
        LogUtil.e("NetworkCallback", "请求 URL: " + call.request().url());
        LogUtil.e("NetworkCallback", "请求方法: " + call.request().method());
        LogUtil.e("NetworkCallback", "请求头: " + call.request().headers());
        
        if (showErrorToast) {
            ErrorHandler.handleException(context, t);
        }
        
        onNetworkError(errorMessage);
    }

    private String getErrorMessage(Throwable t) {
        if (t instanceof SocketTimeoutException) {
            return "网络开小差了，请稍后重试";
        } else if (t instanceof UnknownHostException) {
            return "网络连接已断开，请检查网络设置";
        } else if (t instanceof IOException) {
            return "网络异常，请稍后重试";
        } else {
            // 不显示技术性错误详情，避免用户困惑
            return "操作失败，请稍后重试";
        }
    }

    private void hideLoading() {
        if (loadingView != null && loadingView.isShowing()) {
            loadingView.dismiss();
        }
    }

    protected void showLoading(String message) {
        if (loadingView != null && showLoading) {
            loadingView.setMessage(message);
            loadingView.show();
        }
    }

    protected void showLoading() {
        showLoading("加载中...");
    }

    protected abstract void onSuccess(T data);

    protected void onError(int code, String message) {
        LogUtil.e("NetworkCallback", "业务错误: " + code + ", " + message);
        
        if (showErrorToast) {
            ErrorHandler.handleBusinessError(context, code, message);
        }
        
        onBusinessError(code, message);
    }

    protected void onUnauthorized(String message) {
        LogUtil.e("NetworkCallback", "未授权: " + message);
        
        if (showErrorToast) {
            ErrorHandler.handleUnauthorizedError(context);
        }
        
        onTokenExpired(message);
    }

    protected void onNetworkError(String message) {
        LogUtil.e("NetworkCallback", "网络错误: " + message);
        
        if (showErrorToast) {
            ToastUtil.showError(context, message);
        }
    }

    protected void onBusinessError(int code, String message) {
    }

    protected void onTokenExpired(String message) {
    }
}
