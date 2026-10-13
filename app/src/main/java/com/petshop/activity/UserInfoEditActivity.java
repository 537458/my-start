package com.petshop.activity;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;
import com.petshop.R;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.util.AuthUtil;
import com.petshop.util.AvatarUtil;
import com.petshop.util.GlideUtil;
import com.petshop.util.ImageCompressor;

import java.io.File;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 编辑个人信息。
 * 头像改为「选完即上传」，与资料保存解耦：头像走独立接口，资料只提交文本字段。
 */
public class UserInfoEditActivity extends AppCompatActivity {

    private static final String TAG = "UserInfoEditActivity";
    private static final int REQUEST_CAMERA = 200;
    private static final int REQUEST_PICK_IMAGE = 201;
    private static final int REQUEST_CROP = 202;
    private static final String DEFAULT_AVATAR = "default_avatar";
    private static final String AVATAR_URL = "/api/users/me/avatar/data";

    private CircleImageView ivAvatar;
    private ImageView ivCameraBadge;
    private TextView tvChangeAvatar, tvResetAvatar;
    private EditText etUsername, etPhone, etAddress;
    private Button btnSave;
    private LinearLayout llChangePassword;

    private ProgressDialog uploadDialog;
    private Uri pendingCameraUri;
    private File pendingCameraFile;
    /** 当前是否设置了自定义头像（决定是否展示“恢复默认头像”入口） */
    private boolean hasCustomAvatar = false;
    /** 头像上传中，避免重复提交 */
    private boolean uploadingAvatar = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_info_edit);

        if (!AuthUtil.checkLoginWithPddStyle(this)) {
            finish();
            return;
        }

        initView();
        initListeners();
        loadUserInfo();
    }

    private void initView() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }
        ivAvatar = findViewById(R.id.iv_avatar);
        ivCameraBadge = findViewById(R.id.iv_camera_badge);
        tvChangeAvatar = findViewById(R.id.tv_change_avatar);
        tvResetAvatar = findViewById(R.id.tv_reset_avatar);
        etUsername = findViewById(R.id.et_username);
        etPhone = findViewById(R.id.et_phone);
        etAddress = findViewById(R.id.et_address);
        btnSave = findViewById(R.id.btn_save);
        llChangePassword = findViewById(R.id.ll_change_password);
    }

    private void initListeners() {
        ivAvatar.setOnClickListener(v -> showAvatarDialog());
        ivCameraBadge.setOnClickListener(v -> showAvatarDialog());
        tvChangeAvatar.setOnClickListener(v -> showAvatarDialog());
        tvResetAvatar.setOnClickListener(v -> confirmResetAvatar());

        llChangePassword.setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));

        btnSave.setOnClickListener(v -> saveProfile());
    }

    // ---------------- 数据加载 ----------------

    /**
     * 优先读服务端最新资料，失败时回落到本地缓存，避免编辑页展示空白
     */
    private void loadUserInfo() {
        ApiClient.getInstance(this).getApiService().getUserInfo()
                .enqueue(new Callback<ApiService.Response<com.petshop.model.User>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<com.petshop.model.User>> call,
                                           Response<ApiService.Response<com.petshop.model.User>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200 && response.body().getData() != null) {
                            fillUser(response.body().getData());
                            saveLocalUser(response.body().getData());
                        } else {
                            fillUserFromCache();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<com.petshop.model.User>> call, Throwable t) {
                        Log.w(TAG, "加载用户资料失败，使用本地缓存", t);
                        fillUserFromCache();
                    }
                });
    }

    private void fillUserFromCache() {
        String cached = ApiClient.getInstance(this).getUserInfo();
        if (cached == null || cached.isEmpty()) {
            return;
        }
        try {
            fillUser(new Gson().fromJson(cached, com.petshop.model.User.class));
        } catch (Exception e) {
            Log.w(TAG, "本地用户信息解析失败", e);
        }
    }

    private void fillUser(com.petshop.model.User user) {
        if (user == null) {
            return;
        }
        if (user.getUsername() != null && !user.getUsername().isEmpty()) {
            etUsername.setText(user.getUsername());
        }
        etPhone.setText(user.getPhone() != null ? user.getPhone() : "");
        etAddress.setText(user.getAddress() != null ? user.getAddress() : "");
        loadAvatar(user.getAvatar());
    }

    private void loadAvatar(String avatar) {
        hasCustomAvatar = avatar != null && !avatar.isEmpty() && !DEFAULT_AVATAR.equals(avatar);
        if (hasCustomAvatar) {
            // 统一走 AvatarUtil：地址里固定带账号 ID 与版本号，不会命中旧账号的头像缓存
            AvatarUtil.loadCurrentAvatar(this, ivAvatar);
        } else {
            AvatarUtil.showDefaultAvatar(ivAvatar);
        }
        updateResetEntry();
    }

    private void updateResetEntry() {
        if (tvResetAvatar != null) {
            tvResetAvatar.setVisibility(hasCustomAvatar ? View.VISIBLE : View.GONE);
        }
    }

    // ---------------- 头像选择 ----------------

    private void showAvatarDialog() {
        if (uploadingAvatar) {
            Toast.makeText(this, "头像正在上传，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = hasCustomAvatar
                ? new String[]{"拍照", "从相册选择", "恢复默认头像"}
                : new String[]{"拍照", "从相册选择"};
        new AlertDialog.Builder(this)
                .setTitle("更换头像")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        takePhoto();
                    } else if (which == 1) {
                        pickImage();
                    } else {
                        confirmResetAvatar();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 拍照：用 FileProvider 输出原图，避免只拿到低分辨率缩略图
     */
    private void takePhoto() {
        try {
            File dir = new File(getCacheDir(), "avatar");
            if (!dir.exists() && !dir.mkdirs()) {
                Toast.makeText(this, "无法创建临时目录，请检查存储空间", Toast.LENGTH_SHORT).show();
                return;
            }
            pendingCameraFile = new File(dir, "camera_" + System.currentTimeMillis() + ".jpg");
            pendingCameraUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", pendingCameraFile);

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            intent.setClipData(ClipData.newRawUri("", pendingCameraUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(intent, REQUEST_CAMERA);
            } else {
                Toast.makeText(this, "没有找到可用的相机应用", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "打开相机失败", e);
            Toast.makeText(this, "打开相机失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 从相册选择：使用系统文件选择器，不依赖存储权限和真实路径
     */
    private void pickImage() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            Intent chooser = Intent.createChooser(intent, "选择头像");
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(chooser, REQUEST_PICK_IMAGE);
            } else {
                Toast.makeText(this, "没有找到可用的图片选择器", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "打开相册失败", e);
            Toast.makeText(this, "打开相册失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }
        if (requestCode == REQUEST_CAMERA) {
            if (pendingCameraFile != null && pendingCameraFile.exists() && pendingCameraFile.length() > 0) {
                startCrop(null, pendingCameraFile);
            } else {
                Toast.makeText(this, "拍照未生成图片，请重试", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_PICK_IMAGE) {
            Uri uri = data != null ? data.getData() : null;
            if (uri == null) {
                Toast.makeText(this, "未选择图片", Toast.LENGTH_SHORT).show();
                return;
            }
            startCrop(uri, null);
        } else if (requestCode == REQUEST_CROP) {
            String path = data != null ? data.getStringExtra(AvatarCropActivity.EXTRA_RESULT_PATH) : null;
            if (path == null || path.isEmpty()) {
                Toast.makeText(this, "裁剪未完成，请重试", Toast.LENGTH_SHORT).show();
                return;
            }
            File cropped = new File(path);
            Glide.with(this).load(cropped).circleCrop().into(ivAvatar);
            uploadAvatar(cropped);
        }
    }

    /**
     * 先把待裁剪图片落成缓存文件，再进入裁剪页：
     * 相册返回的 content Uri 先复制到缓存，避免跨 Activity 后授权失效
     */
    private void startCrop(@Nullable Uri uri, @Nullable File file) {
        showUploading(true, "正在准备图片...");
        new Thread(() -> {
            File source = file;
            if (source == null && uri != null) {
                source = ImageCompressor.copyToCache(UserInfoEditActivity.this, uri);
            }
            final File src = source;
            runOnUiThread(() -> {
                showUploading(false, null);
                if (isFinishing()) {
                    return;
                }
                if (src == null || !src.exists()) {
                    Toast.makeText(this, "图片读取失败，请换一张图片试试", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(this, AvatarCropActivity.class);
                intent.putExtra(AvatarCropActivity.EXTRA_SOURCE_PATH, src.getAbsolutePath());
                startActivityForResult(intent, REQUEST_CROP);
            });
        }).start();
    }

    // ---------------- 头像上传 / 恢复默认 ----------------

    private void uploadAvatar(File avatarFile) {
        if (avatarFile == null || !avatarFile.exists()) {
            Toast.makeText(this, "头像文件不存在，请重新选择", Toast.LENGTH_SHORT).show();
            return;
        }
        uploadingAvatar = true;
        showUploading(true, "正在上传头像...");

        RequestBody requestFile = RequestBody.create(MediaType.parse("image/jpeg"), avatarFile);
        MultipartBody.Part body = MultipartBody.Part.createFormData("avatar", avatarFile.getName(), requestFile);

        ApiClient.getInstance(this).getApiService().uploadAvatar(body)
                .enqueue(new Callback<ApiService.Response<Map<String, String>>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Map<String, String>>> call,
                                           Response<ApiService.Response<Map<String, String>>> response) {
                        uploadingAvatar = false;
                        showUploading(false, null);
                        if (isFinishing()) {
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200) {
                            hasCustomAvatar = true;
                            updateResetEntry();
                            updateLocalUserInfo(AVATAR_URL);
                            // 头像已变，让其他页面（个人中心 / 社区）重新拉取
                            ApiClient.refreshAvatarVersion();
                            GlideUtil.clearMemoryCache(UserInfoEditActivity.this);
                            Toast.makeText(UserInfoEditActivity.this, "头像已更新", Toast.LENGTH_SHORT).show();
                        } else {
                            String message = response.body() != null && response.body().getMessage() != null
                                    ? response.body().getMessage() : "服务器返回异常";
                            Toast.makeText(UserInfoEditActivity.this,
                                    "头像上传失败：" + message + "，可点击头像重试", Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Map<String, String>>> call, Throwable t) {
                        uploadingAvatar = false;
                        showUploading(false, null);
                        Log.e(TAG, "头像上传失败", t);
                        Toast.makeText(UserInfoEditActivity.this,
                                "头像上传失败，请检查网络后重试", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void confirmResetAvatar() {
        if (uploadingAvatar) {
            Toast.makeText(this, "头像正在上传，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("恢复默认头像")
                .setMessage("恢复后将使用系统默认头像，确定继续吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("恢复默认", (dialog, which) -> resetAvatar())
                .show();
    }

    private void resetAvatar() {
        showUploading(true, "正在恢复默认头像...");
        ApiClient.getInstance(this).getApiService().resetAvatar()
                .enqueue(new Callback<ApiService.Response<Map<String, String>>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Map<String, String>>> call,
                                           Response<ApiService.Response<Map<String, String>>> response) {
                        showUploading(false, null);
                        if (isFinishing()) {
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200) {
                            hasCustomAvatar = false;
                            ivAvatar.setImageResource(R.drawable.ic_avatar_placeholder);
                            updateResetEntry();
                            updateLocalUserInfo(DEFAULT_AVATAR);
                            ApiClient.refreshAvatarVersion();
                            GlideUtil.clearMemoryCache(UserInfoEditActivity.this);
                            Toast.makeText(UserInfoEditActivity.this, "已恢复默认头像", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(UserInfoEditActivity.this, "恢复失败，请稍后重试", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Map<String, String>>> call, Throwable t) {
                        showUploading(false, null);
                        Log.e(TAG, "恢复默认头像失败", t);
                        Toast.makeText(UserInfoEditActivity.this, "网络异常，恢复失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ---------------- 资料保存 ----------------

    private void saveProfile() {
        if (uploadingAvatar) {
            Toast.makeText(this, "头像正在上传，请稍候再保存", Toast.LENGTH_SHORT).show();
            return;
        }
        String username = etUsername.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String address = etAddress.getText().toString().trim();

        if (username.isEmpty()) {
            Toast.makeText(this, "请输入用户名", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!phone.matches("\\d{11}")) {
            Toast.makeText(this, "请输入 11 位手机号", Toast.LENGTH_SHORT).show();
            return;
        }

        com.petshop.model.User user = new com.petshop.model.User();
        user.setId(ApiClient.getInstance(this).getUserId());
        user.setUsername(username);
        user.setPhone(phone);
        user.setAddress(address);
        // 头像走独立接口，这里不再提交，避免覆盖已上传的头像

        btnSave.setEnabled(false);
        btnSave.setText("保存中...");

        ApiClient.getInstance(this).getApiService().updateUserInfo(user)
                .enqueue(new Callback<ApiService.Response<com.petshop.model.User>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<com.petshop.model.User>> call,
                                           Response<ApiService.Response<com.petshop.model.User>> response) {
                        btnSave.setEnabled(true);
                        btnSave.setText("保存");
                        if (isFinishing()) {
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200) {
                            com.petshop.model.User updated = response.body().getData();
                            if (updated != null) {
                                saveLocalUser(updated);
                            } else {
                                updateLocalUserInfo(hasCustomAvatar ? AVATAR_URL : DEFAULT_AVATAR);
                            }
                            Toast.makeText(UserInfoEditActivity.this, "保存成功", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            String message = response.body() != null && response.body().getMessage() != null
                                    ? response.body().getMessage() : "保存失败";
                            Toast.makeText(UserInfoEditActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<com.petshop.model.User>> call, Throwable t) {
                        btnSave.setEnabled(true);
                        btnSave.setText("保存");
                        Log.e(TAG, "保存资料失败", t);
                        Toast.makeText(UserInfoEditActivity.this, "网络异常，保存失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ---------------- 本地缓存与进度 ----------------

    /** 用 Gson 序列化，避免手工拼 JSON 时字段含引号导致缓存损坏 */
    private void saveLocalUser(com.petshop.model.User user) {
        if (user == null) {
            return;
        }
        user.setPassword(null);
        ApiClient.getInstance(this).saveUserInfo(new Gson().toJson(user));
    }

    private void updateLocalUserInfo(String avatarValue) {
        com.petshop.model.User user = new com.petshop.model.User();
        user.setId(ApiClient.getInstance(this).getUserId());
        user.setUsername(etUsername.getText().toString().trim());
        user.setPhone(etPhone.getText().toString().trim());
        user.setAddress(etAddress.getText().toString().trim());
        user.setAvatar(avatarValue);
        saveLocalUser(user);
    }

    private void showUploading(boolean show, String message) {
        if (show) {
            if (uploadDialog == null) {
                uploadDialog = new ProgressDialog(this);
                uploadDialog.setCancelable(false);
            }
            if (message != null) {
                uploadDialog.setMessage(message);
            }
            if (!uploadDialog.isShowing()) {
                uploadDialog.show();
            }
        } else if (uploadDialog != null && uploadDialog.isShowing()) {
            uploadDialog.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (uploadDialog != null && uploadDialog.isShowing()) {
            uploadDialog.dismiss();
        }
        uploadDialog = null;
    }
}
