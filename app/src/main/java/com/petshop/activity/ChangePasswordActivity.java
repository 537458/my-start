package com.petshop.activity;

import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.petshop.R;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.util.AuthUtil;

import java.util.HashMap;
import java.util.Map;

public class ChangePasswordActivity extends AppCompatActivity {

    private ImageView ivBack;
    private EditText etOldPassword, etNewPassword, etConfirmPassword;
    private Button btnSave;
    private ImageView ivOldPasswordVisibility, ivNewPasswordVisibility, ivConfirmPasswordVisibility;
    private boolean isOldPasswordVisible = false, isNewPasswordVisible = false, isConfirmPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        initView();
        initListeners();
    }

    private void initView() {
        ivBack = findViewById(R.id.iv_back);
        etOldPassword = findViewById(R.id.et_old_password);
        etNewPassword = findViewById(R.id.et_new_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        btnSave = findViewById(R.id.btn_save);
        ivOldPasswordVisibility = findViewById(R.id.iv_old_password_visibility);
        ivNewPasswordVisibility = findViewById(R.id.iv_new_password_visibility);
        ivConfirmPasswordVisibility = findViewById(R.id.iv_confirm_password_visibility);
    }

    private void initListeners() {
        // 返回按钮点击事件
        ivBack.setOnClickListener(v -> finish());

        // 原密码可见性切换
        ivOldPasswordVisibility.setOnClickListener(v -> {
            isOldPasswordVisible = !isOldPasswordVisible;
            togglePasswordVisibility(etOldPassword, ivOldPasswordVisibility, isOldPasswordVisible);
        });

        // 新密码可见性切换
        ivNewPasswordVisibility.setOnClickListener(v -> {
            isNewPasswordVisible = !isNewPasswordVisible;
            togglePasswordVisibility(etNewPassword, ivNewPasswordVisibility, isNewPasswordVisible);
        });

        // 确认密码可见性切换
        ivConfirmPasswordVisibility.setOnClickListener(v -> {
            isConfirmPasswordVisible = !isConfirmPasswordVisible;
            togglePasswordVisibility(etConfirmPassword, ivConfirmPasswordVisibility, isConfirmPasswordVisible);
        });

        // 保存按钮点击事件
        btnSave.setOnClickListener(v -> {
            // 获取输入的密码
            String oldPassword = etOldPassword.getText().toString().trim();
            String newPassword = etNewPassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();

            // 验证输入
            if (oldPassword.isEmpty()) {
                Toast.makeText(this, "请输入原密码", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newPassword.isEmpty()) {
                Toast.makeText(this, "请输入新密码", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newPassword.length() < 6) {
                Toast.makeText(this, "新密码长度不能少于6位", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                Toast.makeText(this, "两次输入的新密码不一致", Toast.LENGTH_SHORT).show();
                return;
            }

            // 调用API修改密码
            changePassword(oldPassword, newPassword);
        });
    }

    private void togglePasswordVisibility(EditText editText, ImageView imageView, boolean isVisible) {
        if (isVisible) {
            // 显示密码
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            imageView.setImageResource(android.R.drawable.ic_menu_view);
        } else {
            // 隐藏密码
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            imageView.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        }
        // 将光标移动到文本末尾
        editText.setSelection(editText.getText().length());
    }

    private void changePassword(String oldPassword, String newPassword) {
        // 构建请求参数
        Map<String, String> params = new HashMap<>();
        params.put("oldPassword", oldPassword);
        params.put("newPassword", newPassword);

        Log.d("ChangePasswordActivity", "开始修改密码");

        // 调用API
        ApiClient.getInstance(this).getApiService().updatePassword(params)
                .enqueue(new retrofit2.Callback<ApiService.Response<Void>>() {
                    @Override
                    public void onResponse(retrofit2.Call<ApiService.Response<Void>> call, retrofit2.Response<ApiService.Response<Void>> response) {
                        Log.d("ChangePasswordActivity", "响应状态码：" + response.code());
                        Log.d("ChangePasswordActivity", "响应成功：" + response.isSuccessful());
                        
                        if (response.isSuccessful() && response.body() != null) {
                            int code = response.body().getCode();
                            String message = response.body().getMessage();
                            Log.d("ChangePasswordActivity", "响应code：" + code);
                            Log.d("ChangePasswordActivity", "响应message：" + message);
                            
                            if (code == 200) {
                                // 修改成功
                                Toast.makeText(ChangePasswordActivity.this, R.string.success_save, Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                // 修改失败
                                if (message != null && !message.isEmpty()) {
                                    Toast.makeText(ChangePasswordActivity.this, message, Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(ChangePasswordActivity.this, "原密码错误或修改失败", Toast.LENGTH_SHORT).show();
                                }
                            }
                        } else {
                            // 修改失败
                            Log.d("ChangePasswordActivity", "响应失败，response.body()：" + response.body());
                            Toast.makeText(ChangePasswordActivity.this, "原密码错误或修改失败", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<ApiService.Response<Void>> call, Throwable t) {
                        // 网络错误
                        Log.e("ChangePasswordActivity", "网络错误：" + t.getMessage());
                        Toast.makeText(ChangePasswordActivity.this, R.string.error_network_disconnected, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}