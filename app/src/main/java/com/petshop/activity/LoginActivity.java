package com.petshop.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.petshop.R;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.api.NetworkCallback;
import com.petshop.databinding.ActivityLoginBinding;
import com.petshop.model.LoginData;
import com.petshop.model.User;
import com.petshop.util.GlideUtil;
import com.petshop.util.ToastUtil;
import com.petshop.util.ValidationUtil;
import com.petshop.util.SecurePreferences;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity implements View.OnClickListener {

    private ActivityLoginBinding binding;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 清除旧的自定义 URL 配置，使用新的默认配置
        ApiClient.clearCustomBaseUrl(this);
        
        // 检查并显示当前 API 地址
        checkAndShowApiUrl();

        apiService = ApiClient.getInstance(this).getApiService();

        if (ApiClient.getInstance(this).isLoggedIn()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }

        initListeners();
    }

    /**
     * 检查并显示当前 API 地址，真机使用时提示用户配置
     */
    private void checkAndShowApiUrl() {
        String currentUrl = ApiClient.getCurrentBaseUrl(this);
        android.util.Log.d("LoginActivity", "Current API URL: " + currentUrl);

        // 如果是真机且使用的是模拟器地址，提示用户
        if (!isEmulator() && currentUrl.contains("10.0.2.2")) {
            android.util.Log.w("LoginActivity", "Real device using emulator IP!");
            // 显示提示，指导用户如何设置正确的 IP
            showApiUrlDialog();
        }
    }

    /**
     * 判断是否为模拟器
     */
    private boolean isEmulator() {
        return android.os.Build.FINGERPRINT.contains("generic") ||
               android.os.Build.MODEL.contains("Emulator") ||
               android.os.Build.MODEL.contains("sdk_gphone");
    }

    /**
     * 显示 API 地址配置对话框
     */
    private void showApiUrlDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("真机调试配置")
                .setMessage("检测到您正在真机上运行。\n\n" +
                        "当前 API 地址: " + ApiClient.getCurrentBaseUrl(this) + "\n\n" +
                        "真机无法访问 10.0.2.2，请设置您电脑的实际 IP 地址。\n\n" +
                        "示例: http://192.168.1.100:8080/api/")
                .setPositiveButton("设置 IP", (dialog, which) -> showSetApiUrlDialog())
                .setNegativeButton("取消", null)
                .setCancelable(false)
                .show();
    }

    /**
     * 显示设置 API 地址的输入对话框
     */
    private void showSetApiUrlDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("http://192.168.x.x:8080/api/");
        input.setText(ApiClient.getCustomBaseUrl(this));

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("设置 API 地址")
                .setMessage("请输入您电脑的局域网 IP 地址:\n" +
                        "(确保手机和电脑在同一 WiFi 网络)")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    if (!url.isEmpty()) {
                        ApiClient.setCustomBaseUrl(this, url);
                        ToastUtil.showSuccess(this, "API 地址已设置，请重新启动应用");
                        // 重新创建 ApiClient 实例
                        apiService = ApiClient.getInstance(this).getApiService();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void initListeners() {
        binding.btnLogin.setOnClickListener(this);
        binding.tvRegister.setOnClickListener(this);
        binding.tvForgotPassword.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_login) {
            login();
        } else if (id == R.id.tv_register) {
            startActivity(new Intent(this, RegisterActivity.class));
        } else if (id == R.id.tv_forgot_password) {
            startActivity(new Intent(this, ForgotPasswordActivity.class));
        }
    }

    private void login() {
        if (!validateInput()) {
            return;
        }

        String username = binding.tilUsername.getEditText().getText().toString().trim();
        String password = binding.tilPassword.getEditText().getText().toString().trim();

        Map<String, String> params = new HashMap<>();
        params.put("username", username);
        params.put("password", password);

        apiService.login(params).enqueue(new NetworkCallback<LoginData>(this) {
            @Override
            protected void onSuccess(LoginData data) {
                if (data != null) {
                    String token = data.getToken();
                    User user = data.getUser();
                    if (token != null && user != null) {
                        // 使用加密存储保存 Token 和用户信息
                        SecurePreferences.saveToken(LoginActivity.this, token);
                        ApiClient.getInstance(LoginActivity.this).saveUserInfo(new Gson().toJson(user));
                        SecurePreferences.saveUserId(LoginActivity.this, user.getId());
                        // 换账号了，头像版本号必须刷新，否则可能仍显示上一个人的头像
                        ApiClient.refreshAvatarVersion();
                        GlideUtil.clearMemoryCache(LoginActivity.this);
                        ToastUtil.showSuccess(LoginActivity.this, "登录成功");
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    }
                }
            }

            @Override
            protected void onBusinessError(int code, String message) {
                ToastUtil.showError(LoginActivity.this, "登录失败: " + message);
            }
        });
    }

    private boolean validateInput() {
        boolean isValid = true;

        if (!ValidationUtil.validateUsername(binding.tilUsername)) {
            isValid = false;
        }

        if (!ValidationUtil.validatePassword(binding.tilPassword)) {
            isValid = false;
        }

        return isValid;
    }
}
