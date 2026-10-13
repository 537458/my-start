package com.petshop.util;

import android.content.Context;
import android.widget.ImageView;

import com.petshop.R;
import com.petshop.api.ApiClient;

/**
 * 头像加载统一入口。
 * 所有头像地址都在这里拼装，固定带上账号 ID 与头像版本号：
 * 换账号或修改头像后地址必然变化，不会命中上一个人 / 旧头像的缓存。
 */
public final class AvatarUtil {

    private AvatarUtil() {
    }

    /**
     * 当前登录用户的头像地址。
     * 接口本身按 token 取人，uid 只用于隔离不同账号的缓存 key。
     *
     * @return 未登录时返回 null
     */
    public static String currentAvatarUrl(Context context) {
        if (context == null) {
            return null;
        }
        ApiClient apiClient = ApiClient.getInstance(context);
        if (apiClient == null || !apiClient.isLoggedIn()) {
            return null;
        }
        return apiBaseUrl(context) + "users/me/avatar/data?uid=" + apiClient.getUserId()
                + "&v=" + ApiClient.getAvatarVersion();
    }

    /** 社区等公开场景下某个用户的头像地址 */
    public static String userAvatarUrl(Context context, int userId) {
        return apiBaseUrl(context) + "users/" + userId + "/avatar?uid=" + userId
                + "&v=" + ApiClient.getAvatarVersion();
    }

    /**
     * 加载当前登录用户的头像；未登录时直接显示默认头像。
     * 该接口需要鉴权，因此带 Authorization 头，且不落磁盘缓存。
     */
    public static void loadCurrentAvatar(Context context, ImageView imageView) {
        if (context == null || imageView == null) {
            return;
        }
        ApiClient apiClient = ApiClient.getInstance(context);
        String url = currentAvatarUrl(context);
        if (apiClient == null || url == null || apiClient.getToken() == null) {
            showDefaultAvatar(imageView);
            return;
        }
        GlideUtil.loadCircleWithAuth(context, url, imageView, apiClient.getToken());
    }

    /** 加载某个用户的公开头像；对方没上传头像时显示默认头像 */
    public static void loadUserAvatar(Context context, int userId, boolean hasAvatar, ImageView imageView) {
        if (context == null || imageView == null) {
            return;
        }
        if (!hasAvatar) {
            showDefaultAvatar(imageView);
            return;
        }
        GlideUtil.loadAvatar(context, userAvatarUrl(context, userId), imageView);
    }

    /** 恢复默认头像，避免退出登录后还残留上一个账号的照片 */
    public static void showDefaultAvatar(ImageView imageView) {
        if (imageView != null) {
            imageView.setImageResource(R.drawable.ic_avatar_placeholder);
        }
    }

    /** 与接口共用同一个 host，避免图片请求打到别的地址 */
    private static String apiBaseUrl(Context context) {
        String baseUrl = ApiClient.getCurrentBaseUrl(context);
        return baseUrl == null ? "" : baseUrl;
    }
}
