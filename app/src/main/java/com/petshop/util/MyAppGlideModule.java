package com.petshop.util;

import android.content.Context;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;

import java.io.InputStream;

import androidx.annotation.NonNull;

import okhttp3.OkHttpClient;
import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Glide 配置类
 * 使用 OkHttp 作为网络层，利用其内置缓存避免重复下载图片
 */
@GlideModule
public class MyAppGlideModule extends AppGlideModule {

    // 内存缓存大小：100MB
    private static final int MEMORY_CACHE_SIZE = 100 * 1024 * 1024;

    // 磁盘缓存大小：500MB
    private static final int DISK_CACHE_SIZE = 500 * 1024 * 1024;

    // HTTP 缓存大小：50MB
    private static final long HTTP_CACHE_SIZE = 50 * 1024 * 1024;

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        // 配置内存缓存
        builder.setMemoryCache(new LruResourceCache(MEMORY_CACHE_SIZE));

        // 配置磁盘缓存
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, DISK_CACHE_SIZE));
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        // 创建带缓存的 OkHttpClient，Glide 用它来加载图片
        File cacheDir = new File(context.getCacheDir(), "glide_http_cache");
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .cache(new okhttp3.Cache(cacheDir, HTTP_CACHE_SIZE))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        // 注册 OkHttp 作为 Glide 的网络加载器（替换默认的 HttpURLConnection）
        registry.replace(
                GlideUrl.class,
                InputStream.class,
                new OkHttpUrlLoader.Factory(okHttpClient)
        );
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
