package com.petshop.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.petshop.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Glide 图片加载工具类
 * 统一配置图片加载参数，优化内存和磁盘缓存策略
 */
public class GlideUtil {

    // 默认加载配置
    private static final RequestOptions DEFAULT_OPTIONS = new RequestOptions()
            .placeholder(R.drawable.ic_avatar_placeholder)
            .error(R.drawable.ic_avatar_placeholder)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .skipMemoryCache(false)
            .priority(Priority.NORMAL)
            .centerCrop();

    // 高优先级加载配置（用于首屏图片）
    private static final RequestOptions HIGH_PRIORITY_OPTIONS = new RequestOptions()
            .placeholder(R.drawable.ic_avatar_placeholder)
            .error(R.drawable.ic_avatar_placeholder)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .skipMemoryCache(false)
            .priority(Priority.HIGH)
            .centerCrop();

    // 圆形图片配置
    private static final RequestOptions CIRCLE_OPTIONS = new RequestOptions()
            .placeholder(R.drawable.ic_avatar_placeholder)
            .error(R.drawable.ic_avatar_placeholder)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .skipMemoryCache(false)
            .circleCrop();

    // 圆角图片配置
    private static final RequestOptions ROUNDED_OPTIONS = new RequestOptions()
            .placeholder(R.drawable.ic_avatar_placeholder)
            .error(R.drawable.ic_avatar_placeholder)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .skipMemoryCache(false)
            .centerCrop()
            .transform(new com.bumptech.glide.load.resource.bitmap.RoundedCorners(8));

    // 缩略图配置（用于列表中的小图）
    private static final RequestOptions THUMBNAIL_OPTIONS = new RequestOptions()
            .placeholder(R.drawable.ic_avatar_placeholder)
            .error(R.drawable.ic_avatar_placeholder)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .skipMemoryCache(false)
            .override(400, 400)
            .centerCrop();

    // Base64图片配置（禁用内存缓存，避免重复解码）
    private static final RequestOptions BASE64_OPTIONS = new RequestOptions()
            .placeholder(R.drawable.ic_avatar_placeholder)
            .error(R.drawable.ic_avatar_placeholder)
            .diskCacheStrategy(DiskCacheStrategy.DATA) // 只缓存原始数据
            .skipMemoryCache(true) // 禁用内存缓存，因为Base64已经在内存中
            .priority(Priority.LOW) // Base64解码较慢，设为低优先级
            .centerCrop();

    // 线程池用于后台解码Base64
    private static final ExecutorService BASE64_DECODE_EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /**
     * 加载普通图片
     */
    public static void load(Context context, String url, ImageView imageView) {
        try {
            LogUtil.d("GlideUtil", "加载图片: " + url);
            Glide.with(context)
                    .load(url)
                    .apply(DEFAULT_OPTIONS)
                    .into(imageView);
        } catch (Exception e) {
            LogUtil.e("GlideUtil", "加载图片失败: " + url, e);
            imageView.setImageResource(R.drawable.ic_launcher);
        }
    }

    /**
     * 加载普通图片（带占位图）
     */
    public static void load(Context context, String url, ImageView imageView, @DrawableRes int placeholder) {
        RequestOptions options = new RequestOptions()
                .placeholder(placeholder)
                .error(placeholder)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .skipMemoryCache(false)
                .centerCrop();

        Glide.with(context)
                .load(url)
                .apply(options)
                .into(imageView);
    }

    /**
     * 加载高优先级图片（用于首屏图片）
     */
    public static void loadHighPriority(Context context, String url, ImageView imageView) {
        Glide.with(context)
                .load(url)
                .apply(HIGH_PRIORITY_OPTIONS)
                .into(imageView);
    }

    /**
     * 加载圆形图片
     */
    public static void loadCircle(Context context, String url, ImageView imageView) {
        Glide.with(context)
                .load(url)
                .apply(CIRCLE_OPTIONS)
                .into(imageView);
    }

    /**
     * 加载圆形图片（可选择是否禁用缓存）
     */
    public static void loadCircle(Context context, String url, ImageView imageView, boolean skipCache) {
        if (skipCache) {
            RequestOptions options = new RequestOptions()
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .error(R.drawable.ic_avatar_placeholder)
                    .diskCacheStrategy(DiskCacheStrategy.NONE) // 禁用磁盘缓存
                    .skipMemoryCache(true) // 禁用内存缓存
                    .circleCrop();

            Glide.with(context)
                    .load(url)
                    .apply(options)
                    .into(imageView);
        } else {
            loadCircle(context, url, imageView);
        }
    }

    /**
     * 加载圆角图片
     */
    public static void loadRounded(Context context, String url, ImageView imageView) {
        Glide.with(context)
                .load(url)
                .apply(ROUNDED_OPTIONS)
                .into(imageView);
    }

    /**
     * 加载头像（社区列表 / 评论）：只走内存缓存，不落磁盘。
     * 头像地址带版本号，改头像或换账号后 key 会变，因此不会显示旧头像，
     * 同时避免磁盘缓存把很久之前的头像长期留在本地。
     */
    public static void loadAvatar(Context context, String url, ImageView imageView) {
        RequestOptions options = new RequestOptions()
                .placeholder(R.drawable.ic_avatar_placeholder)
                .error(R.drawable.ic_avatar_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(false)
                .circleCrop();

        Glide.with(context)
                .load(url)
                .apply(options)
                .into(imageView);
    }

    /**
     * 清空内存缓存（账号切换时调用），磁盘缓存靠 URL 版本号隔离
     */
    public static void clearMemoryCache(Context context) {
        if (context == null) {
            return;
        }
        Glide.get(context).clearMemory();
    }

    /**
     * 加载缩略图（用于列表中的小图）
     */
    public static void loadThumbnail(Context context, String url, ImageView imageView) {
        final long startTime = System.currentTimeMillis();
        LogUtil.i("GlideUtil", "[BLOB-LOAD] ========== 开始加载图片 ==========");
        LogUtil.i("GlideUtil", "[BLOB-LOAD] 请求URL: " + url);
        LogUtil.i("GlideUtil", "[BLOB-LOAD] 开始时间: " + new java.util.Date(startTime));

        try {
            // 如果是Base64图片，使用专门的优化配置
            if (isBase64Image(url)) {
                LogUtil.d("GlideUtil", "[BLOB-LOAD] 检测到Base64图片，使用专用加载器");
                loadBase64Thumbnail(context, url, imageView);
                return;
            }

            Glide.with(context)
                    .load(url)
                    .apply(THUMBNAIL_OPTIONS)
                    .listener(new com.bumptech.glide.request.RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<Drawable> target, boolean isFirstResource) {
                            long endTime = System.currentTimeMillis();
                            long duration = endTime - startTime;
                            LogUtil.e("GlideUtil", "[BLOB-LOAD] ========== 图片加载失败 ==========");
                            LogUtil.e("GlideUtil", "[BLOB-LOAD] 失败URL: " + url);
                            LogUtil.e("GlideUtil", "[BLOB-LOAD] 加载时长: " + duration + "ms");
                            LogUtil.e("GlideUtil", "[BLOB-LOAD] 结束时间: " + new java.util.Date(endTime));
                            if (e != null) {
                                LogUtil.e("GlideUtil", "[BLOB-LOAD] 错误类型: " + e.getClass().getSimpleName());
                                LogUtil.e("GlideUtil", "[BLOB-LOAD] 错误消息: " + e.getMessage());
                                if (e.getRootCauses() != null) {
                                    for (Throwable cause : e.getRootCauses()) {
                                        LogUtil.e("GlideUtil", "[BLOB-LOAD] 根本原因: " + cause.getMessage(), cause);
                                    }
                                }
                            }
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, com.bumptech.glide.request.target.Target<Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            long endTime = System.currentTimeMillis();
                            long duration = endTime - startTime;
                            LogUtil.i("GlideUtil", "[BLOB-LOAD] ========== 图片加载成功 ==========");
                            LogUtil.i("GlideUtil", "[BLOB-LOAD] 成功URL: " + url);
                            LogUtil.i("GlideUtil", "[BLOB-LOAD] 数据源: " + dataSource);
                            LogUtil.i("GlideUtil", "[BLOB-LOAD] 加载时长: " + duration + "ms");
                            LogUtil.i("GlideUtil", "[BLOB-LOAD] 结束时间: " + new java.util.Date(endTime));
                            LogUtil.i("GlideUtil", "[BLOB-LOAD] Drawable类型: " + resource.getClass().getSimpleName());
                            return false;
                        }
                    })
                    .into(imageView);
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            LogUtil.e("GlideUtil", "[BLOB-LOAD] ========== 异常加载失败 ==========");
            LogUtil.e("GlideUtil", "[BLOB-LOAD] 异常URL: " + url);
            LogUtil.e("GlideUtil", "[BLOB-LOAD] 加载时长: " + duration + "ms");
            LogUtil.e("GlideUtil", "[BLOB-LOAD] 异常类型: " + e.getClass().getSimpleName());
            LogUtil.e("GlideUtil", "[BLOB-LOAD] 异常消息: " + e.getMessage(), e);
            imageView.setImageResource(R.drawable.ic_avatar_placeholder);
        }
    }

    /**
     * 判断是否为Base64图片
     */
    public static boolean isBase64Image(String url) {
        return url != null && url.startsWith("data:image/");
    }

    /**
     * 优化加载Base64缩略图
     * 使用后台线程解码，避免阻塞主线程
     */
    private static void loadBase64Thumbnail(Context context, String base64Data, ImageView imageView) {
        final long startTime = System.currentTimeMillis();
        LogUtil.i("GlideUtil", "[BLOB-LOAD] ========== 开始Base64图片解码 ==========");
        LogUtil.i("GlideUtil", "[BLOB-LOAD] Base64数据长度: " + base64Data.length() + " 字符");
        LogUtil.i("GlideUtil", "[BLOB-LOAD] 开始时间: " + new java.util.Date(startTime));

        // 设置占位图
        imageView.setImageResource(R.drawable.ic_avatar_placeholder);

        // 在后台线程解码Base64
        BASE64_DECODE_EXECUTOR.execute(() -> {
            try {
                // 提取Base64数据部分
                LogUtil.d("GlideUtil", "[BLOB-LOAD] 正在提取Base64数据...");
                String base64String = base64Data.split(",")[1];
                LogUtil.d("GlideUtil", "[BLOB-LOAD] 正在解码Base64数据...");
                byte[] imageBytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT);
                LogUtil.i("GlideUtil", "[BLOB-LOAD] Base64解码完成，数据大小: " + imageBytes.length + " 字节");

                // 使用BitmapFactory解码并限制尺寸
                android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);

                LogUtil.d("GlideUtil", "[BLOB-LOAD] 原始图片尺寸: " + options.outWidth + "x" + options.outHeight);

                // 计算inSampleSize，将图片缩小到最大400x400
                int maxSize = 400;
                int width = options.outWidth;
                int height = options.outHeight;
                int inSampleSize = 1;

                if (height > maxSize || width > maxSize) {
                    int halfHeight = height / 2;
                    int halfWidth = width / 2;
                    while ((halfHeight / inSampleSize) >= maxSize && (halfWidth / inSampleSize) >= maxSize) {
                        inSampleSize *= 2;
                    }
                }
                LogUtil.d("GlideUtil", "[BLOB-LOAD] 缩放比例: inSampleSize=" + inSampleSize);

                options.inJustDecodeBounds = false;
                options.inSampleSize = inSampleSize;
                options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565; // 使用更节省内存的配置

                final Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(
                        imageBytes, 0, imageBytes.length, options);

                // 在主线程设置图片
                MAIN_HANDLER.post(() -> {
                    long endTime = System.currentTimeMillis();
                    long duration = endTime - startTime;

                    if (bitmap != null && !bitmap.isRecycled()) {
                        LogUtil.i("GlideUtil", "[BLOB-LOAD] ========== Base64图片加载成功 ==========");
                        LogUtil.i("GlideUtil", "[BLOB-LOAD] 最终图片尺寸: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                        LogUtil.i("GlideUtil", "[BLOB-LOAD] 加载时长: " + duration + "ms");
                        LogUtil.i("GlideUtil", "[BLOB-LOAD] 结束时间: " + new java.util.Date(endTime));
                        imageView.setImageBitmap(bitmap);
                    } else {
                        LogUtil.e("GlideUtil", "[BLOB-LOAD] ========== Base64图片加载失败 ==========");
                        LogUtil.e("GlideUtil", "[BLOB-LOAD] Bitmap为null或已被回收");
                        LogUtil.e("GlideUtil", "[BLOB-LOAD] 加载时长: " + duration + "ms");
                        LogUtil.e("GlideUtil", "[BLOB-LOAD] 结束时间: " + new java.util.Date(endTime));
                        imageView.setImageResource(R.drawable.ic_avatar_placeholder);
                    }
                });

            } catch (Exception e) {
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;
                LogUtil.e("GlideUtil", "[BLOB-LOAD] ========== Base64解码异常 ==========");
                LogUtil.e("GlideUtil", "[BLOB-LOAD] 异常类型: " + e.getClass().getSimpleName());
                LogUtil.e("GlideUtil", "[BLOB-LOAD] 异常消息: " + e.getMessage());
                LogUtil.e("GlideUtil", "[BLOB-LOAD] 加载时长: " + duration + "ms");
                LogUtil.e("GlideUtil", "[BLOB-LOAD] 结束时间: " + new java.util.Date(endTime), e);
                MAIN_HANDLER.post(() -> {
                    imageView.setImageResource(R.drawable.ic_avatar_placeholder);
                });
            }
        });
    }

    /**
     * 优化加载Base64大图（用于详情页）
     */
    public static void loadBase64Optimized(Context context, String base64Data, ImageView imageView, int maxSize) {
        // 设置占位图
        imageView.setImageResource(R.drawable.ic_avatar_placeholder);

        // 在后台线程解码Base64
        BASE64_DECODE_EXECUTOR.execute(() -> {
            try {
                // 提取Base64数据部分
                String base64String = base64Data.split(",")[1];
                byte[] imageBytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT);

                // 使用BitmapFactory解码并限制尺寸
                android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);

                // 计算inSampleSize
                int width = options.outWidth;
                int height = options.outHeight;
                int inSampleSize = 1;

                if (height > maxSize || width > maxSize) {
                    int halfHeight = height / 2;
                    int halfWidth = width / 2;
                    while ((halfHeight / inSampleSize) >= maxSize && (halfWidth / inSampleSize) >= maxSize) {
                        inSampleSize *= 2;
                    }
                }

                options.inJustDecodeBounds = false;
                options.inSampleSize = inSampleSize;
                options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;

                final Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(
                        imageBytes, 0, imageBytes.length, options);

                // 在主线程设置图片
                MAIN_HANDLER.post(() -> {
                    if (bitmap != null && !bitmap.isRecycled()) {
                        imageView.setImageBitmap(bitmap);
                    } else {
                        imageView.setImageResource(R.drawable.ic_avatar_placeholder);
                    }
                });

            } catch (Exception e) {
                LogUtil.e("GlideUtil", "Base64解码失败", e);
                MAIN_HANDLER.post(() -> {
                    imageView.setImageResource(R.drawable.ic_avatar_placeholder);
                });
            }
        });
    }

    /**
     * 加载本地资源图片
     */
    public static void loadResource(Context context, @DrawableRes int resId, ImageView imageView) {
        Glide.with(context)
                .load(resId)
                .apply(DEFAULT_OPTIONS)
                .into(imageView);
    }

    /**
     * 加载图片（自定义配置）
     */
    public static void loadWithOptions(Context context, String url, ImageView imageView, RequestOptions options) {
        Glide.with(context)
                .load(url)
                .apply(options)
                .into(imageView);
    }

    /**
     * 清除图片缓存
     */
    public static void clearCache(Context context) {
        Glide.get(context).clearMemory();
        new Thread(() -> {
            Glide.get(context).clearDiskCache();
        }).start();
    }

    /**
     * 暂停图片加载
     */
    public static void pauseRequests(Context context) {
        Glide.with(context).pauseRequests();
    }

    /**
     * 恢复图片加载
     */
    public static void resumeRequests(Context context) {
        Glide.with(context).resumeRequests();
    }

    /**
     * 加载需要认证的图片（如用户头像）
     * 使用Authorization请求头进行认证
     */
    public static void loadWithAuth(Context context, String url, ImageView imageView, String token) {
        try {
            LogUtil.d("GlideUtil", "加载需要认证的图片: " + url);

            // 创建带Authorization头的GlideUrl
            GlideUrl glideUrl = new GlideUrl(url, new LazyHeaders.Builder()
                    .addHeader("Authorization", "Bearer " + token)
                    .build());

            RequestOptions options = new RequestOptions()
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .error(R.drawable.ic_avatar_placeholder)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .circleCrop();

            Glide.with(context)
                    .load(glideUrl)
                    .apply(options)
                    .listener(new com.bumptech.glide.request.RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<Drawable> target, boolean isFirstResource) {
                            LogUtil.e("GlideUtil", "加载认证图片失败: " + url, e);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, com.bumptech.glide.request.target.Target<Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            LogUtil.d("GlideUtil", "加载认证图片成功: " + url);
                            return false;
                        }
                    })
                    .into(imageView);
        } catch (Exception e) {
            LogUtil.e("GlideUtil", "加载认证图片异常: " + url, e);
            imageView.setImageResource(R.drawable.ic_avatar_placeholder);
        }
    }

    /**
     * 加载需要认证的圆形图片（如用户头像）
     */
    public static void loadCircleWithAuth(Context context, String url, ImageView imageView, String token) {
        loadWithAuth(context, url, imageView, token);
    }
}