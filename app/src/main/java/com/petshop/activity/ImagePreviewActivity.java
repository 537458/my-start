package com.petshop.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.petshop.R;
import com.petshop.view.ZoomImageView;

/**
 * 大图预览：社区动态配图点击后全屏查看，支持双指缩放 / 双击放大 / 拖动。
 * 单击图片时，放大状态先还原，整图状态则退出。
 */
public class ImagePreviewActivity extends AppCompatActivity {

    private static final String EXTRA_IMAGE_URL = "extra_image_url";

    /** 统一的入口，避免各处重复拼装 Intent */
    public static void open(Context context, String imageUrl) {
        if (context == null || imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        Intent intent = new Intent(context, ImagePreviewActivity.class);
        intent.putExtra(EXTRA_IMAGE_URL, imageUrl);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String imageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        if (imageUrl == null || imageUrl.isEmpty()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_image_preview);
        applyDarkSystemBar();

        final View progress = findViewById(R.id.pb_preview);
        ZoomImageView imageView = findViewById(R.id.iv_preview);
        imageView.setOnTapListener(zoomed -> {
            // 放大时第一次点击只做还原，保持整图状态再点才关闭
            if (!zoomed) {
                finish();
            }
        });
        findViewById(R.id.tv_close).setOnClickListener(v -> finish());

        Glide.with(this)
                .load(imageUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                Target<Drawable> target, boolean isFirstResource) {
                        progress.setVisibility(View.GONE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target,
                                                   DataSource dataSource, boolean isFirstResource) {
                        progress.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(imageView);
    }

    /** 黑色背景配白色状态栏图标，避免深色文字糊在黑底上 */
    private void applyDarkSystemBar() {
        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }
}
