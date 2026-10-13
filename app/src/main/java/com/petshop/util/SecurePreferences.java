package com.petshop.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.security.GeneralSecurityException;

/**
 * 安全偏好设置工具类（加密存储敏感信息）
 */
public class SecurePreferences {
    
    private static final String PREF_NAME = "secure_petshop_prefs";
    private static final String TAG = "SecurePreferences";
    
    private static SharedPreferences instance;
    
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static synchronized SharedPreferences getInstance(Context context) {
        if (instance == null) {
            try {
                MasterKey masterKey = new MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();
                
                instance = EncryptedSharedPreferences.create(
                        context,
                        PREF_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
                Log.d(TAG, "加密存储初始化成功");
            } catch (GeneralSecurityException e) {
                Log.e(TAG, "安全存储初始化失败，降级到普通存储", e);
                instance = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            } catch (Exception e) {
                Log.e(TAG, "初始化失败", e);
                instance = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            }
        }
        return instance;
    }
    
    /**
     * 保存认证 Token（加密）
     */
    public static void saveToken(Context context, String token) {
        getInstance(context).edit().putString("auth_token", token).apply();
        Log.d(TAG, "Token 已加密保存");
    }
    
    /**
     * 获取认证 Token
     */
    public static String getToken(Context context) {
        return getInstance(context).getString("auth_token", null);
    }
    
    /**
     * 清除所有数据（退出登录时使用）
     */
    public static void clear(Context context) {
        getInstance(context).edit().clear().apply();
        Log.d(TAG, "安全存储已清空");
    }
    
    /**
     * 保存用户 ID
     */
    public static void saveUserId(Context context, int userId) {
        getInstance(context).edit().putInt("user_id", userId).apply();
    }
    
    /**
     * 获取用户 ID
     */
    public static int getUserId(Context context) {
        return getInstance(context).getInt("user_id", -1);
    }
}
