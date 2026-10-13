package com.petshop.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.content.Intent;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.petshop.R;
import com.petshop.activity.AboutUsActivity;
import com.petshop.api.ApiClient;
import com.petshop.util.PreferenceUtil;
import com.petshop.util.ToastUtil;

import java.net.URI;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREF_NAME = "petshop_prefs";
    private static final String DEVICE_TYPE_KEY = "device_type";
    private static final String CUSTOM_API_URL_KEY = "custom_base_url";

    private LinearLayout llPrivacy, llFeedback, llDeviceType, llApiUrl;
    private TextView tvDeviceType, tvApiUrl;

    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("设置");
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        initView();
        initListeners();
    }

    private void initView() {
        llPrivacy = findViewById(R.id.ll_privacy);
        llFeedback = findViewById(R.id.ll_feedback);
        llDeviceType = findViewById(R.id.ll_device_type);
        llApiUrl = findViewById(R.id.ll_api_url);
        tvDeviceType = findViewById(R.id.tv_device_type);
        tvApiUrl = findViewById(R.id.tv_api_url);
        
        updateDeviceTypeDisplay();
        updateApiUrlDisplay();
    }

    private void initListeners() {
        llPrivacy.setOnClickListener(v -> {
            showPrivacyPolicy();
        });

        llFeedback.setOnClickListener(v -> {
            showFeedbackDialog();
        });

        llDeviceType.setOnClickListener(v -> {
            switchDeviceType();
        });

        llApiUrl.setOnClickListener(v -> {
            showApiUrlDialog();
        });
    }

    private void updateDeviceTypeDisplay() {
        String deviceType = sharedPreferences.getString(DEVICE_TYPE_KEY, "emulator");
        String displayText = "emulator".equals(deviceType) ? "模拟器" : "真机";
        tvDeviceType.setText(displayText);
    }

    private void updateApiUrlDisplay() {
        String customUrl = sharedPreferences.getString(CUSTOM_API_URL_KEY, "");
        if (customUrl == null || customUrl.isEmpty()) {
            tvApiUrl.setText("自动检测（仅本机可用）");
        } else {
            tvApiUrl.setText(customUrl);
        }
    }

    private void switchDeviceType() {
        String currentType = sharedPreferences.getString(DEVICE_TYPE_KEY, "emulator");
        String newType = "emulator".equals(currentType) ? "real_device" : "emulator";
        sharedPreferences.edit().putString(DEVICE_TYPE_KEY, newType).apply();
        
        String displayText = "emulator".equals(newType) ? "模拟器" : "真机";
        tvDeviceType.setText(displayText);
        
        ToastUtil.showSuccess(this, "设备类型已切换为：" + displayText);
        
        ApiClient.getInstance(this).clearInstance();
    }

    private void showApiUrlDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("服务器地址");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("例：https://xxxx.cpolar.top/api/");
        input.setSingleLine(true);
        String currentUrl = sharedPreferences.getString(CUSTOM_API_URL_KEY, "");
        if (currentUrl != null && !currentUrl.isEmpty()) {
            input.setText(currentUrl);
        }
        // 地址很长，给输入框留点宽度
        input.setMinEms(24);

        builder.setView(input);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String newUrl = normalizeServerUrl(input.getText().toString());
            if (newUrl.isEmpty()) {
                ApiClient.clearCustomBaseUrl(SettingsActivity.this);
                updateApiUrlDisplay();
                ToastUtil.showSuccess(SettingsActivity.this, "已恢复自动检测");
                return;
            }
            // 走 setCustomBaseUrl：它会补结尾斜杠、刷新图片地址缓存并重建 Retrofit，
            // 直接写 SharedPreferences 会让 baseUrl 少一个 / 而当场抛异常
            ApiClient.setCustomBaseUrl(SettingsActivity.this, newUrl);
            updateApiUrlDisplay();
            testConnection();
        });

        builder.setNeutralButton("恢复自动检测", (dialog, which) -> {
            ApiClient.clearCustomBaseUrl(SettingsActivity.this);
            updateApiUrlDisplay();
            ToastUtil.showSuccess(SettingsActivity.this, "已恢复自动检测，只有本机能连上");
        });

        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    /**
     * 把用户手填的地址补成 Retrofit 能用的形式：
     * 缺协议时局域网 IP / 带端口默认 http、隧道域名默认 https；
     * 缺路径补 /api/，结尾补斜杠。
     */
    private String normalizeServerUrl(String raw) {
        String url = raw == null ? "" : raw.trim();
        if (url.isEmpty()) {
            return "";
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = looksLikeLocalAddress(url) ? "http://" + url : "https://" + url;
        }
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        try {
            String path = URI.create(url).getPath();
            if (path == null || path.isEmpty() || "/".equals(path)) {
                url = url + "api/";
            }
        } catch (Exception ignored) {
            // 地址解析不了就按原样交给后端，失败时有提示
        }
        return url;
    }

    /** 本机、局域网 IP、模拟器回环地址，或者写了端口的一般都是明文 http */
    private boolean looksLikeLocalAddress(String url) {
        String host = url.toLowerCase();
        int slash = host.indexOf('/');
        if (slash > 0) {
            host = host.substring(0, slash);
        }
        int colon = host.indexOf(':');
        boolean hasPort = colon > 0 && colon < host.length() - 1;
        String authority = colon > 0 ? host.substring(0, colon) : host;
        return "localhost".equals(authority)
                || "127.0.0.1".equals(authority)
                || "10.0.2.2".equals(authority)
                || authority.startsWith("192.168.")
                || authority.startsWith("10.")
                || authority.startsWith("172.")
                || hasPort;
    }

    /**
     * 保存后立即探一次 /health，免得填错了要走到登录页才发现
     */
    private void testConnection() {
        ToastUtil.showInfo(this, "正在测试连接…");
        ApiClient.getInstance(this).getApiService().checkHealth()
                .enqueue(new Callback<Map<String, String>>() {
                    @Override
                    public void onResponse(Call<Map<String, String>> call,
                                           Response<Map<String, String>> response) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        Map<String, String> body = response.body();
                        if (response.isSuccessful() && body != null) {
                            String database = body.get("database");
                            ToastUtil.showSuccess(SettingsActivity.this,
                                    "连接成功，数据库：" + (database == null ? "未知" : database)
                                            + "\n建议重新打开一次 App，让所有页面都用新地址");
                        } else {
                            ToastUtil.showError(SettingsActivity.this,
                                    "服务器有响应但异常（" + response.code() + "），检查地址是否多了一段路径");
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, String>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        ToastUtil.showError(SettingsActivity.this,
                                "连不上这个地址：确认隧道工具在跑、后端没关、地址没多字");
                    }
                });
    }

    private void showPrivacyPolicy() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("隐私政策");
        builder.setMessage("宠物乐园致力于保护用户隐私。我们收集的信息仅用于提供更好的服务体验，不会与第三方共享。\n\n" +
                "1. 我们会收集您的登录信息以提供个性化服务\n" +
                "2. 我们会记录您的使用习惯以改进产品体验\n" +
                "3. 您的宠物信息仅存储在本地和我们的安全服务器上\n" +
                "4. 您可以随时删除您的账户和所有相关数据");
        
        builder.setPositiveButton("我知道了", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        
        builder.show();
    }

    private void showFeedbackDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("意见反馈");
        
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE);
        input.setHint("请输入您的意见或建议...");
        input.setMinLines(5);
        
        builder.setView(input);
        
        builder.setPositiveButton("提交", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String feedback = input.getText().toString().trim();
                if (feedback.isEmpty()) {
                    ToastUtil.showWarning(SettingsActivity.this, "请输入反馈内容");
                    return;
                }
                
                ToastUtil.showSuccess(SettingsActivity.this, "感谢您的反馈！我们会认真处理您的建议");
                dialog.dismiss();
            }
        });
        
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        
        builder.show();
    }
}
