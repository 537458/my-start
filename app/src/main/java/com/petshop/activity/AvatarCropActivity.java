package com.petshop.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.petshop.R;
import com.petshop.util.ImageCompressor;
import com.petshop.util.ToastUtil;
import com.petshop.view.CropImageView;

import java.io.File;

/**
 * 头像裁剪页：拖动/缩放调整位置后输出 1:1 正方形头像。
 * 输入是缓存目录里的图片文件，输出同样是缓存文件路径（通过 setResult 回传）。
 */
public class AvatarCropActivity extends AppCompatActivity {

    public static final String EXTRA_SOURCE_PATH = "source_path";
    public static final String EXTRA_RESULT_PATH = "result_path";

    /** 解码上限：1600px 足够裁剪清晰，又不至于占用过多内存 */
    private static final int MAX_DECODE_SIZE = 1600;
    private static final int OUTPUT_QUALITY = 90;

    private CropImageView cropView;
    private View progress;
    private TextView btnDone;

    private String sourcePath;
    private Bitmap workingBitmap;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar_crop);

        sourcePath = getIntent().getStringExtra(EXTRA_SOURCE_PATH);
        if (sourcePath == null || sourcePath.isEmpty()) {
            ToastUtil.showError(this, "图片读取失败，请重新选择");
            finish();
            return;
        }

        cropView = findViewById(R.id.crop_view);
        progress = findViewById(R.id.pb_loading);
        btnDone = findViewById(R.id.btn_done);
        findViewById(R.id.btn_cancel).setOnClickListener(v -> finish());
        btnDone.setOnClickListener(v -> confirmCrop());

        loadImage();
    }

    private void loadImage() {
        setBusy(true);
        new Thread(() -> {
            Bitmap bitmap = ImageCompressor.decodeForCrop(new File(sourcePath), MAX_DECODE_SIZE);
            runOnUiThread(() -> {
                setBusy(false);
                if (isFinishing() || isDestroyed()) {
                    if (bitmap != null) {
                        bitmap.recycle();
                    }
                    return;
                }
                if (bitmap == null) {
                    ToastUtil.showError(this, "图片读取失败，请换一张图片试试");
                    finish();
                    return;
                }
                workingBitmap = bitmap;
                cropView.setCropBitmap(bitmap);
            });
        }).start();
    }

    private void confirmCrop() {
        if (busy || workingBitmap == null) {
            return;
        }
        final Bitmap cropped = cropView.crop(ImageCompressor.AVATAR_SIZE);
        if (cropped == null) {
            ToastUtil.showError(this, "裁剪失败，请调整后重试");
            return;
        }
        setBusy(true);
        new Thread(() -> {
            File output = ImageCompressor.saveJpeg(this, cropped, OUTPUT_QUALITY);
            runOnUiThread(() -> {
                setBusy(false);
                // createBitmap 在整图未缩放时可能返回同一个对象，这里避免误回收正在展示的位图
                if (cropped != workingBitmap) {
                    cropped.recycle();
                }
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (output == null) {
                    ToastUtil.showError(this, "保存失败，请重试");
                    return;
                }
                Intent data = new Intent();
                data.putExtra(EXTRA_RESULT_PATH, output.getAbsolutePath());
                setResult(RESULT_OK, data);
                finish();
            });
        }).start();
    }

    private void setBusy(boolean value) {
        busy = value;
        if (progress != null) {
            progress.setVisibility(value ? View.VISIBLE : View.GONE);
        }
        if (btnDone != null) {
            btnDone.setEnabled(!value);
            btnDone.setAlpha(value ? 0.5f : 1f);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (workingBitmap != null && !workingBitmap.isRecycled()) {
            workingBitmap.recycle();
        }
        workingBitmap = null;
    }
}
