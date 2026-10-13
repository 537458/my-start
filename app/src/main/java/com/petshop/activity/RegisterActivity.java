package com.petshop.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;
import com.petshop.R;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.api.NetworkCallback;
import com.petshop.model.User;
import com.petshop.util.ToastUtil;
import com.petshop.util.ValidationUtil;
import com.petshop.util.SecurePreferences;

public class RegisterActivity extends AppCompatActivity implements View.OnClickListener {

    private TextInputLayout tilUsername;
    private TextInputLayout tilPhone;
    private TextInputLayout tilPassword;
    private TextInputLayout tilConfirmPassword;
    private TextView tvLogin;

    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initView();

        apiService = ApiClient.getInstance(this).getApiService();
    }

    private void initView() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }
        tilUsername = findViewById(R.id.til_username);
        tilPhone = findViewById(R.id.til_phone);
        tilPassword = findViewById(R.id.til_password);
        tilConfirmPassword = findViewById(R.id.til_confirm_password);
        tvLogin = findViewById(R.id.tv_login);

        findViewById(R.id.btn_register).setOnClickListener(this);
        tvLogin.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_register) {
            register();
        } else if (id == R.id.tv_login) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    private void register() {
        if (!validateInput()) {
            return;
        }

        String username = tilUsername.getEditText().getText().toString().trim();
        String password = tilPassword.getEditText().getText().toString().trim();
        String phone = tilPhone.getEditText().getText().toString().trim();

        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        // 手机号为选填，留空时不下发，避免后端按空串做唯一性校验
        if (!phone.isEmpty()) {
            user.setPhone(phone);
        }

        apiService.register(user).enqueue(new NetworkCallback<User>(this) {
            @Override
            protected void onSuccess(User data) {
                ToastUtil.showSuccess(RegisterActivity.this, "注册成功");
                startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                finish();
            }

            @Override
            protected void onBusinessError(int code, String message) {
                ToastUtil.showError(RegisterActivity.this, "注册失败: " + message);
            }
        });
    }

    private boolean validateInput() {
        boolean isValid = true;

        if (!ValidationUtil.validateUsername(tilUsername)) {
            isValid = false;
        }

        // 手机号选填，只在用户填了的时候校验格式
        String phone = tilPhone.getEditText() != null
                ? tilPhone.getEditText().getText().toString().trim() : "";
        if (!phone.isEmpty() && !ValidationUtil.validatePhone(tilPhone)) {
            isValid = false;
        }

        if (!ValidationUtil.validatePassword(tilPassword)) {
            isValid = false;
        }

        if (!ValidationUtil.validateConfirmPassword(tilPassword, tilConfirmPassword)) {
            isValid = false;
        }

        return isValid;
    }
}
