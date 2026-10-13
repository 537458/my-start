package com.petshop.activity;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.petshop.R;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.util.ToastUtil;
import com.petshop.util.ValidationUtil;

/**
 * 找回密码：手机号 -> 短信验证码 -> 设置新密码。
 * 接口免登录，因此这里不能依赖 ApiClient 里已有的 token。
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    private static final String TAG = "ForgotPasswordActivity";

    /** 与后端 SmsService 的重发间隔保持一致 */
    private static final long RESEND_INTERVAL_MILLIS = 60_000L;

    private TextInputLayout tilPhone;
    private TextInputLayout tilCode;
    private TextInputLayout tilNewPassword;
    private TextInputLayout tilConfirmPassword;
    private MaterialButton btnSendCode;
    private MaterialButton btnReset;

    private CountDownTimer countDownTimer;
    /** 验证码请求在途时禁止重复点击 */
    private boolean sending;
    private boolean resetting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        initView();
        initListeners();
    }

    private void initView() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        tilPhone = findViewById(R.id.til_phone);
        tilCode = findViewById(R.id.til_code);
        tilNewPassword = findViewById(R.id.til_new_password);
        tilConfirmPassword = findViewById(R.id.til_confirm_password);
        btnSendCode = findViewById(R.id.btn_send_code);
        btnReset = findViewById(R.id.btn_reset);
        TextView tvBackLogin = findViewById(R.id.tv_back_login);

        toolbar.setNavigationOnClickListener(v -> finish());
        tvBackLogin.setOnClickListener(v -> finish());
    }

    private void initListeners() {
        btnSendCode.setOnClickListener(v -> sendCode());
        btnReset.setOnClickListener(v -> resetPassword());
    }

    private String phone() {
        return tilPhone.getEditText() != null
                ? tilPhone.getEditText().getText().toString().trim() : "";
    }

    private String code() {
        return tilCode.getEditText() != null
                ? tilCode.getEditText().getText().toString().trim() : "";
    }

    /**
     * 发送验证码。演示模式下后端会把验证码写在提示语里返回，
     * 因此这里直接展示 message，接入真实短信网关后同一行代码仍然成立。
     */
    private void sendCode() {
        if (sending) {
            return;
        }
        if (!ValidationUtil.validatePhone(tilPhone)) {
            return;
        }

        sending = true;
        ApiClient.getInstance(this).getApiService().sendResetCode(phone())
                .enqueue(new retrofit2.Callback<ApiService.Response<Void>>() {
                    @Override
                    public void onResponse(retrofit2.Call<ApiService.Response<Void>> call,
                                           retrofit2.Response<ApiService.Response<Void>> response) {
                        sending = false;
                        if (isFinishing()) {
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200) {
                            String message = response.body().getMessage();
                            ToastUtil.show(ForgotPasswordActivity.this,
                                    message != null && !message.isEmpty()
                                            ? message : getString(R.string.code_sent));
                            startCountDown();
                        } else {
                            ToastUtil.show(ForgotPasswordActivity.this, messageOf(response));
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<ApiService.Response<Void>> call, Throwable t) {
                        sending = false;
                        if (isFinishing()) {
                            return;
                        }
                        Log.e(TAG, "发送验证码失败", t);
                        ToastUtil.show(ForgotPasswordActivity.this,
                                getString(R.string.error_network_disconnected));
                    }
                });
    }

    private void resetPassword() {
        if (resetting) {
            return;
        }
        if (!validateInput()) {
            return;
        }

        resetting = true;
        btnReset.setEnabled(false);
        String newPassword = tilNewPassword.getEditText() != null
                ? tilNewPassword.getEditText().getText().toString() : "";

        ApiClient.getInstance(this).getApiService().resetPassword(phone(), code(), newPassword)
                .enqueue(new retrofit2.Callback<ApiService.Response<Void>>() {
                    @Override
                    public void onResponse(retrofit2.Call<ApiService.Response<Void>> call,
                                           retrofit2.Response<ApiService.Response<Void>> response) {
                        resetting = false;
                        if (isFinishing()) {
                            return;
                        }
                        btnReset.setEnabled(true);
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200) {
                            ToastUtil.show(ForgotPasswordActivity.this,
                                    getString(R.string.reset_password_success));
                            finish();
                        } else {
                            // 验证码错误时清空输入，避免对着同一个废码反复试
                            tilCode.getEditText().setText("");
                            ToastUtil.show(ForgotPasswordActivity.this, messageOf(response));
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<ApiService.Response<Void>> call, Throwable t) {
                        resetting = false;
                        if (isFinishing()) {
                            return;
                        }
                        btnReset.setEnabled(true);
                        Log.e(TAG, "重置密码失败", t);
                        ToastUtil.show(ForgotPasswordActivity.this,
                                getString(R.string.error_network_disconnected));
                    }
                });
    }

    private boolean validateInput() {
        boolean isValid = true;

        if (!ValidationUtil.validatePhone(tilPhone)) {
            isValid = false;
        }

        String code = code();
        if (ValidationUtil.isEmpty(code)) {
            ValidationUtil.setEditTextError(tilCode, "请输入验证码");
            isValid = false;
        } else if (code.length() != 6) {
            ValidationUtil.setEditTextError(tilCode, "验证码为6位数字");
            isValid = false;
        } else {
            ValidationUtil.clearEditTextError(tilCode);
        }

        if (!ValidationUtil.validatePassword(tilNewPassword)) {
            isValid = false;
        }

        if (!ValidationUtil.validateConfirmPassword(tilNewPassword, tilConfirmPassword)) {
            isValid = false;
        }

        return isValid;
    }

    /** 后端把失败原因放在 message 里，优先展示它，退化为通用提示 */
    private String messageOf(retrofit2.Response<ApiService.Response<Void>> response) {
        if (response.body() != null) {
            String message = response.body().getMessage();
            if (message != null && !message.isEmpty()) {
                return message;
            }
        }
        return getString(R.string.reset_password_failed);
    }

    private void startCountDown() {
        btnSendCode.setEnabled(false);
        countDownTimer = new CountDownTimer(RESEND_INTERVAL_MILLIS, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                btnSendCode.setText(getString(R.string.code_countdown,
                        (int) Math.ceil(millisUntilFinished / 1000.0)));
            }

            @Override
            public void onFinish() {
                btnSendCode.setText(R.string.get_code);
                btnSendCode.setEnabled(true);
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        super.onDestroy();
    }
}
