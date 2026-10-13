package com.petshop.api;

import android.content.Context;
import android.content.Intent;

import com.petshop.activity.LoginActivity;
import com.petshop.util.LogUtil;
import com.petshop.util.PreferenceUtil;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 401 拦截器。
 * 只有「本次请求携带的就是当前 token」时才认为登录态失效：
 * 用户已经重新登录后，之前那些迟到的旧请求返回 401 不能把新 token 清掉。
 */
public class TokenRefreshInterceptor implements Interceptor {

    private static final String TAG = "TokenRefreshInterceptor";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /** 已经处理过的失效 token：并发的多个 401 只清理、只跳转一次 */
    private static volatile String handledToken;

    private final Context context;

    public TokenRefreshInterceptor(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);

        if (response.code() == 401) {
            handleUnauthorizedIfNeeded(request);
        }
        return response;
    }

    private void handleUnauthorizedIfNeeded(Request request) {
        // 登录 / 注册 / 找回密码等接口的 401 是「账号或密码错误」，不是登录态失效
        String path = request.url().encodedPath();
        if (path != null && path.contains("/auth/")) {
            return;
        }

        String requestToken = bearerToken(request);
        if (requestToken == null) {
            return;
        }

        String currentToken = ApiClient.getInstance(context).getToken();
        if (!requestToken.equals(currentToken)) {
            // 迟到的旧请求：当前 token 已经换过了，不能清
            LogUtil.w(TAG, "忽略旧请求返回的 401");
            return;
        }

        synchronized (TokenRefreshInterceptor.class) {
            if (requestToken.equals(handledToken)) {
                // 同一个失效 token 并发返回多个 401，只处理一次
                return;
            }
            handledToken = requestToken;
            doLogout();
        }
    }

    private String bearerToken(Request request) {
        String header = request.header(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private void doLogout() {
        LogUtil.w(TAG, "登录态已失效，清理本地登录信息并回到登录页");
        ApiClient apiClient = ApiClient.getInstance(context);
        apiClient.clearToken();
        apiClient.clearUserInfo();
        apiClient.clearUserId();
        PreferenceUtil.clearToken(context);
        PreferenceUtil.clearUserId(context);

        Intent intent = new Intent(context, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }
}
