package com.petshop.util;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceUtil {
    private static final String PREF_NAME = "petshop_preferences";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_NAME = "user_name";

    private static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void setLoggedIn(Context context, boolean isLoggedIn) {
        getPreferences(context).edit().putBoolean(KEY_IS_LOGGED_IN, isLoggedIn).apply();
    }

    public static boolean isLoggedIn(Context context) {
        return getPreferences(context).getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public static void setUserId(Context context, String userId) {
        getPreferences(context).edit().putString(KEY_USER_ID, userId).apply();
    }

    public static String getUserId(Context context) {
        return getPreferences(context).getString(KEY_USER_ID, "");
    }

    public static void setUserName(Context context, String userName) {
        getPreferences(context).edit().putString(KEY_USER_NAME, userName).apply();
    }

    public static String getUserName(Context context) {
        return getPreferences(context).getString(KEY_USER_NAME, "");
    }

    public static void clearUserInfo(Context context) {
        getPreferences(context).edit()
                .remove(KEY_IS_LOGGED_IN)
                .remove(KEY_USER_ID)
                .remove(KEY_USER_NAME)
                .apply();
    }

    public static void clearToken(Context context) {
        // Token 存储在 ApiClient 中，这里调用 ApiClient 的方法
    }

    public static void clearUserId(Context context) {
        getPreferences(context).edit().remove(KEY_USER_ID).apply();
    }
}