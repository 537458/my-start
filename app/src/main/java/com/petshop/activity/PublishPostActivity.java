package com.petshop.activity;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.petshop.R;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.util.GlideUtil;
import com.petshop.util.ImageCompressor;
import com.petshop.util.LogUtil;
import com.petshop.util.ToastUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 发布 / 编辑动态：文字必填，配图最多 6 张（上传前先压缩，和后端上限保持一致）；
 * 编辑模式可以改文字、改可见范围，也可以删掉旧图 / 追加新图。
 */
public class PublishPostActivity extends AppCompatActivity {

    private static final String TAG = "PublishPost";
    /** 与后端 CommunityController.MAX_IMAGES 保持一致 */
    private static final int MAX_IMAGES = 6;
    /** 配图预览每行最多 3 张，超过自动换行 */
    private static final int IMAGE_COLUMNS = 3;

    /** 编辑模式传入的动态 ID，大于 0 表示在编辑已有动态 */
    public static final String EXTRA_POST_ID = "post_id";
    public static final String EXTRA_CONTENT = "content";
    public static final String EXTRA_VISIBILITY = "visibility";
    public static final String EXTRA_IMAGE_COUNT = "image_count";
    /** 服务端配图版本号（updatedAt），只用于拼预览地址避开旧缓存 */
    public static final String EXTRA_IMAGE_VERSION = "image_version";

    private EditText etContent;
    private TextView tvCount;
    private LinearLayout llImages;
    private MaterialButton btnPublish;
    private TextView tvImageLabel, tvVisibilityValue;

    private final List<Uri> pickedImages = new ArrayList<>();
    /** 编辑时保留的旧图序号，提交时按这个顺序回传给服务端 */
    private final List<Integer> keptImageIndexes = new ArrayList<>();
    private ActivityResultLauncher<Intent> pickerLauncher;
    private ExecutorService executor;
    private boolean uploading = false;

    private int editingPostId = 0;
    private int existingImageCount = 0;
    private String imageVersion = "";
    private int visibility = ApiService.CommunityPostItem.VISIBILITY_PUBLIC;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_publish_post);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        // 白底工具栏用深色返回箭头，白色箭头会看不见
        toolbar.setNavigationIcon(R.drawable.ic_back_dark);
        toolbar.setNavigationOnClickListener(v -> finish());

        // 带上 post_id 就是「编辑已有动态」，否则是「发布新动态」
        editingPostId = getIntent().getIntExtra(EXTRA_POST_ID, 0);
        existingImageCount = getIntent().getIntExtra(EXTRA_IMAGE_COUNT, 0);
        visibility = getIntent().getIntExtra(EXTRA_VISIBILITY, ApiService.CommunityPostItem.VISIBILITY_PUBLIC);
        String version = getIntent().getStringExtra(EXTRA_IMAGE_VERSION);
        imageVersion = version == null ? "" : version;

        etContent = findViewById(R.id.et_content);
        tvCount = findViewById(R.id.tv_count);
        llImages = findViewById(R.id.ll_images);
        btnPublish = findViewById(R.id.btn_publish);
        tvImageLabel = findViewById(R.id.tv_image_label);
        tvVisibilityValue = findViewById(R.id.tv_visibility_value);

        // XML 里的 android:title 在部分主题下不生效，这里显式设置标题
        toolbar.setTitle(editingPostId > 0 ? "编辑动态" : "发布动态");

        if (editingPostId > 0) {
            btnPublish.setText("保存");
            tvImageLabel.setText("配图（最多 " + MAX_IMAGES + " 张，点 × 可删除）");
            // 默认全部保留，删掉的那张从列表里移除即可
            for (int i = 0; i < existingImageCount; i++) {
                keptImageIndexes.add(i);
            }
            String origin = getIntent().getStringExtra(EXTRA_CONTENT);
            if (origin != null) {
                etContent.setText(origin);
                etContent.setSelection(origin.length());
                // 文本监听在下面才注册，这里手动同步一次字数
                tvCount.setText(origin.length() + "/1000");
            }
        }
        updateVisibilityText();
        findViewById(R.id.ll_visibility).setOnClickListener(v -> chooseVisibility());

        pickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        collectPicked(result.getData());
                    }
                });
        executor = Executors.newSingleThreadExecutor();

        etContent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                tvCount.setText(s.length() + "/1000");
            }
        });

        btnPublish.setOnClickListener(v -> publish());
        renderImages();
    }

    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        pickerLauncher.launch(intent);
    }

    private void collectPicked(Intent data) {
        boolean overflow = false;
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                if (totalImageCount() >= MAX_IMAGES) {
                    overflow = true;
                    break;
                }
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null && !pickedImages.contains(uri)) {
                    pickedImages.add(uri);
                }
            }
        } else if (data.getData() != null) {
            if (totalImageCount() >= MAX_IMAGES) {
                overflow = true;
            } else if (!pickedImages.contains(data.getData())) {
                pickedImages.add(data.getData());
            }
        }
        if (overflow) {
            ToastUtil.showWarning(this, "最多只能选 " + MAX_IMAGES + " 张图片");
        }
        renderImages();
    }

    /** 旧图 + 新选图的总数，编辑和发布共用同一个上限 */
    private int totalImageCount() {
        return keptImageIndexes.size() + pickedImages.size();
    }

    /**
     * 配图预览：先展示保留的旧图、再展示新选图，末尾是「添加」占位；
     * 每行最多 3 张，图片尺寸随行宽自适应。
     */
    private void renderImages() {
        llImages.removeAllViews();
        llImages.setOrientation(LinearLayout.VERTICAL);
        int size = previewCellSize();
        int gap = dp(8);

        int slot = 0;
        LinearLayout row = null;
        for (int i = 0; i < keptImageIndexes.size(); i++) {
            final int keptIndex = keptImageIndexes.get(i);
            FrameLayout cell = buildThumbFrame(size, gap, slot);

            ImageView thumb = new ImageView(this);
            thumb.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GlideUtil.loadRounded(this, buildExistingImageUrl(keptIndex), thumb);
            cell.addView(thumb);
            cell.addView(buildRemoveButton(() -> {
                keptImageIndexes.remove(Integer.valueOf(keptIndex));
                renderImages();
            }));

            row = attachCell(row, cell, slot, gap);
            slot++;
        }
        for (int i = 0; i < pickedImages.size(); i++) {
            final int pickedIndex = i;
            FrameLayout cell = buildThumbFrame(size, gap, slot);

            ImageView thumb = new ImageView(this);
            thumb.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this).load(pickedImages.get(i)).centerCrop().into(thumb);
            cell.addView(thumb);
            cell.addView(buildRemoveButton(() -> {
                pickedImages.remove(pickedIndex);
                renderImages();
            }));

            row = attachCell(row, cell, slot, gap);
            slot++;
        }

        if (totalImageCount() < MAX_IMAGES) {
            attachCell(row, buildAddCell(size, gap, slot), slot, gap);
        }
    }

    /** 按列数把格子放到当前行，满一行就新建一行 */
    private LinearLayout attachCell(LinearLayout row, View cell, int slot, int gap) {
        if (slot % IMAGE_COLUMNS == 0) {
            row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (slot > 0) {
                rowParams.topMargin = gap;
            }
            row.setLayoutParams(rowParams);
            llImages.addView(row);
        }
        row.addView(cell);
        return row;
    }

    private FrameLayout buildThumbFrame(int size, int gap, int slot) {
        FrameLayout cell = new FrameLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        if (slot % IMAGE_COLUMNS != IMAGE_COLUMNS - 1) {
            params.setMarginEnd(gap);
        }
        cell.setLayoutParams(params);
        return cell;
    }

    private View buildRemoveButton(Runnable onRemove) {
        TextView remove = new TextView(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(20), dp(20));
        params.gravity = Gravity.TOP | Gravity.END;
        params.setMargins(0, dp(2), dp(2), 0);
        remove.setLayoutParams(params);
        remove.setText("×");
        remove.setTextColor(Color.WHITE);
        remove.setTextSize(13);
        remove.setGravity(Gravity.CENTER);
        remove.setBackgroundResource(R.drawable.bg_circle_dark);
        remove.setOnClickListener(v -> onRemove.run());
        return remove;
    }

    private View buildAddCell(int size, int gap, int slot) {
        FrameLayout add = new FrameLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        if (slot % IMAGE_COLUMNS != IMAGE_COLUMNS - 1) {
            params.setMarginEnd(gap);
        }
        add.setLayoutParams(params);
        add.setBackgroundResource(R.drawable.bg_dashed_add);
        add.setOnClickListener(v -> openPicker());

        ImageView icon = new ImageView(this);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(26), dp(26));
        iconParams.gravity = Gravity.CENTER;
        icon.setLayoutParams(iconParams);
        icon.setImageResource(R.drawable.ic_add);
        icon.setColorFilter(getColor(R.color.colorTextLight));
        add.addView(icon);
        return add;
    }

    /** 格子边长：3 列铺满行宽，最大不超过 92dp */
    private int previewCellSize() {
        int width = llImages.getWidth();
        if (width <= 0) {
            width = getResources().getDisplayMetrics().widthPixels - dp(64);
        }
        int gap = dp(8);
        int cell = (width - gap * (IMAGE_COLUMNS - 1)) / IMAGE_COLUMNS;
        return Math.min(cell, dp(92));
    }

    private String buildExistingImageUrl(int index) {
        String baseUrl = ApiClient.getCurrentBaseUrl(this);
        String url = (baseUrl == null ? "" : baseUrl) + "community/posts/" + editingPostId + "/images/" + index;
        // 配图改过后 index 不变，用版本号避开旧图缓存
        if (imageVersion != null && !imageVersion.isEmpty()) {
            url = url + "?v=" + imageVersion.replace(' ', '_').replace(':', '-');
        }
        return url;
    }

    private void chooseVisibility() {
        String[] labels = {"公开（所有人可见）", "仅自己可见"};
        int checked = visibility == ApiService.CommunityPostItem.VISIBILITY_PRIVATE ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle("谁可以看")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    visibility = which == 1
                            ? ApiService.CommunityPostItem.VISIBILITY_PRIVATE
                            : ApiService.CommunityPostItem.VISIBILITY_PUBLIC;
                    updateVisibilityText();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateVisibilityText() {
        tvVisibilityValue.setText(visibility == ApiService.CommunityPostItem.VISIBILITY_PRIVATE
                ? "仅自己可见" : "公开");
    }

    private void publish() {
        if (uploading) {
            return;
        }
        final String content = etContent.getText().toString().trim();
        if (content.isEmpty()) {
            ToastUtil.showWarning(this, "说点什么再发布吧");
            return;
        }
        if (editingPostId > 0) {
            saveEdit(content);
            return;
        }

        uploading = true;
        btnPublish.setEnabled(false);
        btnPublish.setText("发布中…");

        final List<Uri> uris = new ArrayList<>(pickedImages);
        executor.execute(() -> {
            List<File> files = compressImages(uris);
            runOnUiThread(() -> uploadPost(content, files));
        });
    }

    /** 逐张压缩待上传的图片，失败的直接跳过 */
    private List<File> compressImages(List<Uri> uris) {
        List<File> files = new ArrayList<>();
        for (Uri uri : uris) {
            File picked = ImageCompressor.copyToCache(this, uri);
            if (picked == null) {
                continue;
            }
            String compressedPath = ImageCompressor.compressImage(this, picked);
            File uploadFile = compressedPath != null ? new File(compressedPath) : picked;
            if (uploadFile.exists()) {
                files.add(uploadFile);
            }
        }
        return files;
    }

    private void uploadPost(String content, List<File> files) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        RequestBody contentBody = RequestBody.create(MediaType.parse("text/plain"), content);
        RequestBody visibilityBody = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(visibility));
        List<MultipartBody.Part> parts = new ArrayList<>();
        for (File file : files) {
            RequestBody fileBody = RequestBody.create(MediaType.parse("image/jpeg"), file);
            parts.add(MultipartBody.Part.createFormData("images", file.getName(), fileBody));
        }

        ApiClient.getInstance(this).getApiService().createCommunityPost(contentBody, visibilityBody, parts)
                .enqueue(new Callback<ApiService.Response<Integer>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Integer>> call,
                                           Response<ApiService.Response<Integer>> response) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200) {
                            ToastUtil.showSuccess(PublishPostActivity.this, "发布成功");
                            setResult(Activity.RESULT_OK);
                            finish();
                        } else {
                            resetPublishButton();
                            String message = response.body() == null
                                    ? "发布失败" : response.body().getMessage();
                            ToastUtil.showError(PublishPostActivity.this, message);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Integer>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        resetPublishButton();
                        LogUtil.e(TAG, "发布动态失败", t);
                        ToastUtil.showError(PublishPostActivity.this, "网络异常，请稍后重试");
                    }
                });
    }

    /**
     * 编辑保存：先压缩新图（没有新图就不用压缩），
     * 文字与可见范围先提交，配图有变动再单独提交一次。
     */
    private void saveEdit(String content) {
        uploading = true;
        btnPublish.setEnabled(false);
        btnPublish.setText("保存中…");

        boolean imagesChanged = !pickedImages.isEmpty()
                || keptImageIndexes.size() != existingImageCount;
        if (!imagesChanged) {
            updatePost(content, null);
            return;
        }
        final List<Uri> uris = new ArrayList<>(pickedImages);
        executor.execute(() -> {
            List<File> files = compressImages(uris);
            runOnUiThread(() -> updatePost(content, files));
        });
    }

    /** files 为 null 表示配图没动过；否则等文字保存成功后接着提交配图 */
    private void updatePost(String content, List<File> files) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        payload.put("visibility", visibility);

        ApiClient.getInstance(this).getApiService().updateCommunityPost(editingPostId, payload)
                .enqueue(new Callback<ApiService.Response<Void>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Void>> call,
                                           Response<ApiService.Response<Void>> response) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200) {
                            if (files == null) {
                                String message = response.body().getMessage();
                                ToastUtil.showSuccess(PublishPostActivity.this,
                                        message == null ? "已保存" : message);
                                setResult(Activity.RESULT_OK);
                                finish();
                            } else {
                                updatePostImages(files);
                            }
                        } else {
                            resetPublishButton();
                            String message = response.body() == null
                                    ? "保存失败" : response.body().getMessage();
                            ToastUtil.showError(PublishPostActivity.this, message);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Void>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        resetPublishButton();
                        LogUtil.e(TAG, "编辑动态失败", t);
                        ToastUtil.showError(PublishPostActivity.this, "网络异常，请稍后重试");
                    }
                });
    }

    /** 提交保留的旧图序号 + 新上传的图片，后端会重排配图顺序 */
    private void updatePostImages(List<File> files) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        RequestBody keepBody = RequestBody.create(MediaType.parse("text/plain"), buildKeepIndexes());
        List<MultipartBody.Part> parts = new ArrayList<>();
        for (File file : files) {
            RequestBody fileBody = RequestBody.create(MediaType.parse("image/jpeg"), file);
            parts.add(MultipartBody.Part.createFormData("images", file.getName(), fileBody));
        }

        ApiClient.getInstance(this).getApiService().updateCommunityPostImages(editingPostId, keepBody, parts)
                .enqueue(new Callback<ApiService.Response<Integer>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Integer>> call,
                                           Response<ApiService.Response<Integer>> response) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200) {
                            ToastUtil.showSuccess(PublishPostActivity.this, "已保存");
                            setResult(Activity.RESULT_OK);
                            finish();
                        } else {
                            resetPublishButton();
                            String message = response.body() == null
                                    ? "配图更新失败" : response.body().getMessage();
                            ToastUtil.showError(PublishPostActivity.this, "文字已保存，" + message);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Integer>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        resetPublishButton();
                        LogUtil.e(TAG, "更新动态配图失败", t);
                        ToastUtil.showError(PublishPostActivity.this, "文字已保存，配图更新失败，请重试");
                    }
                });
    }

    /** keepIndexes 是保留的旧图序号，逗号分隔；空串表示全部删掉 */
    private String buildKeepIndexes() {
        StringBuilder builder = new StringBuilder();
        for (Integer index : keptImageIndexes) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(index);
        }
        return builder.toString();
    }

    private void resetPublishButton() {
        uploading = false;
        btnPublish.setEnabled(true);
        btnPublish.setText(editingPostId > 0 ? "保存" : "发布");
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
