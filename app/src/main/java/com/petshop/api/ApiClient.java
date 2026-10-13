package com.petshop.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petshop.BuildConfig;
import com.petshop.util.SecurePreferences;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import okhttp3.Cache;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * API客户端工具类
 * 实现数据隔离：私有数据接口强制携带 user_id
 */
public class ApiClient {
    private static final String TAG = "ApiClient";
    // API地址配置（可通过配置文件或运行时设置修改）
    private static String BASE_URL_EMULATOR = "http://10.0.2.2:8080/api/";
    // 真机通过 USB 端口转发访问本机后端：adb reverse tcp:8080 tcp:8080
    private static String BASE_URL_REAL_DEVICE = "http://127.0.0.1:8080/api/";

    static {
        // 打 prod 包时可以把公网服务器地址烧进 BuildConfig.API_BASE_URL，
        // 别人装上就能直接用，不必自己去设置里填。
        // 烧的还是模拟器地址（10.0.2.2 只有模拟器认）或压根没烧时，保持原来的本机回环默认值。
        String baked = BuildConfig.API_BASE_URL;
        if (baked != null && !baked.trim().isEmpty() && !baked.contains("10.0.2.2")) {
            baked = baked.trim();
            if (!baked.endsWith("/")) {
                baked = baked + "/";
            }
            BASE_URL_REAL_DEVICE = baked;
        }
    }
    private static final String PREF_NAME = "petshop_prefs";
    private static final String TOKEN_KEY = "auth_token";
    private static final String USER_ID_KEY = "user_id";
    private static final String DEVICE_TYPE_KEY = "device_type";
    private static final String CUSTOM_BASE_URL_KEY = "custom_base_url";

    /**
     * 自定义地址的运行时缓存。
     * getBaseUrl() 没有 Context 参数，图片地址拼接要靠它才能与接口地址保持一致，
     * 否则设置了局域网 IP 后会出现「数据能加载、图片加载不出来」。
     */
    private static volatile String customBaseUrlCache;

    /**
     * 头像版本号。
     * 头像接口地址对每个账号都是 users/me/avatar/data，仅靠 URL 无法区分人，
     * 登录、退出、改头像时刷新版本号，让图片 URL 变化，避免 Glide 命中上一个账号的缓存。
     */
    private static volatile long avatarVersion = System.currentTimeMillis();

    private static ApiClient instance;
    private ApiService apiService;
    private OkHttpClient okHttpClient;
    private SharedPreferences sharedPreferences;
    private Context context;

    // 私有数据接口路径前缀（需要强制携带 user_id）
    private static final String[] PRIVATE_API_PATHS = {
        "user-pets",
        "orders",
        "cart",
        "addresses",
        "appointments",
        "ai/analyze"
    };

    private ApiClient(Context context) {
        this.context = context.getApplicationContext();
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // 同步自定义地址缓存，保证图片 URL 与接口地址走同一个 host
        customBaseUrlCache = getCustomBaseUrl(this.context);

        // 根据设备类型选择 API 地址
        String baseUrl = determineBaseUrl(context);

        // 创建缓存目录和缓存
        File cacheDir = new File(context.getCacheDir(), "http_cache");
        int cacheSize = 10 * 1024 * 1024; // 10MB
        Cache cache = new Cache(cacheDir, cacheSize);

        // 创建OkHttpClient
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)  // 连接超时30秒
                .readTimeout(120, TimeUnit.SECONDS)     // 读取超时120秒（AI响应可能较慢）
                .writeTimeout(30, TimeUnit.SECONDS)     // 写入超时30秒
                .retryOnConnectionFailure(true) // 自动重试连接失败
                .addInterceptor(new RetryInterceptor(3)) // 添加重试拦截器
                .cache(cache); // 添加缓存

        // 添加日志拦截器（仅 Debug 模式）
        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
                @Override
                public void log(String message) {
                    Log.d(TAG, message);
                }
            });
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            httpClient.addInterceptor(loggingInterceptor);
        }

        // 添加请求拦截器，添加认证token和user_id
        httpClient.addInterceptor(new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request original = chain.request();

                // 从SharedPreferences获取token和user_id
                String token = getToken();
                int userId = getUserId();

                // 创建新的请求，添加Authorization头
                Request.Builder requestBuilder = original.newBuilder()
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json");

                if (token != null) {
                    requestBuilder.header("Authorization", "Bearer " + token);
                }

                // 为私有数据接口自动添加 user_id 参数
                HttpUrl originalUrl = original.url();
                HttpUrl.Builder urlBuilder = originalUrl.newBuilder();
                
                // 不再自动添加 user_id 参数，后端会从 JWT token 中获取用户ID
                // if (isPrivateApi(originalUrl.encodedPath())) {
                //     if (userId != -1) {
                //         urlBuilder.addQueryParameter("user_id", String.valueOf(userId));
                //         Log.d(TAG, "Adding user_id=" + userId + " to private API: " + originalUrl.encodedPath());
                //     } else {
                //         Log.w(TAG, "Private API called but user_id not found: " + originalUrl.encodedPath());
                //     }
                // }
                
                Request request = requestBuilder.url(urlBuilder.build()).build();
                return chain.proceed(request);
            }
        });

        // 添加 Token 刷新拦截器
        httpClient.addInterceptor(new TokenRefreshInterceptor(context));
        
        // 添加缓存控制拦截器
        httpClient.addInterceptor(new CacheControlInterceptor());

        // 保存OkHttpClient实例
        okHttpClient = httpClient.build();

        // 创建Gson实例
        Gson gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd HH:mm:ss")
                .create();

        // 创建Retrofit实例
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        // 创建API服务
        apiService = retrofit.create(ApiService.class);
    }

    /**
     * 判断是否为私有数据接口
     * 私有数据接口需要强制携带 user_id 进行数据隔离
     */
    private boolean isPrivateApi(String path) {
        for (String privatePath : PRIVATE_API_PATHS) {
            if (path.contains(privatePath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据设备类型确定 API 地址
     */
    private String determineBaseUrl(Context context) {
        // 优先检查是否有自定义 API 地址（用于真机调试）
        String customUrl = getCustomBaseUrl(context);
        if (customUrl != null && !customUrl.isEmpty()) {
            return customUrl;
        }
        
        // 检测设备类型
        boolean isEmulator = isEmulator(
            Build.MODEL,
            Build.MANUFACTURER,
            Build.PRODUCT,
            Build.HARDWARE
        );
        
        // 根据设备类型选择 API 地址
        String baseUrl = isEmulator ? BASE_URL_EMULATOR : BASE_URL_REAL_DEVICE;
        
        // 如果是真机且使用的是模拟器地址，给出警告（网络错误相关日志）
        if (!isEmulator && baseUrl.contains("10.0.2.2")) {
            Log.w(TAG, "WARNING: Real device is using emulator IP (10.0.2.2). " +
                       "Please call ApiClient.setCustomBaseUrl() with your computer's IP address.");
        }
            
        return baseUrl;
    }

    /**
     * 判断是否为模拟器
     */
    private static boolean isEmulator(String model, String manufacturer, String product, String hardware) {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || (model != null && model.contains("google_sdk"))
                || (model != null && model.contains("Emulator"))
                || (model != null && model.contains("Android SDK built for x86"))
                || (manufacturer != null && manufacturer.contains("Genymotion"))
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(product)
                || (hardware != null && (hardware.contains("goldfish") || hardware.contains("ranchu")));
    }

    /**
     * 当前设备是否为模拟器（供图片地址处理等场景复用）
     */
    public static boolean isEmulatorDevice() {
        return isEmulator(Build.MODEL, Build.MANUFACTURER, Build.PRODUCT, Build.HARDWARE);
    }

    /**
     * 获取单例实例
     */
    public static synchronized ApiClient getInstance(Context context) {
        if (instance == null) {
            if (context != null) {
                instance = new ApiClient(context.getApplicationContext());
            } else {
                // 如果context为null，抛出异常
                throw new IllegalArgumentException("Context cannot be null");
            }
        }
        return instance;
    }

    /**
     * 获取API服务
     */
    public ApiService getApiService() {
        return apiService;
    }

    /**
     * 获取OkHttpClient实例（用于流式请求）
     */
    public OkHttpClient getOkHttpClient() {
        return okHttpClient;
    }

    /**
     * 保存认证token
     */
    public void saveToken(String token) {
        sharedPreferences.edit().putString(TOKEN_KEY, token).apply();
        // 同时保存到 SecurePreferences
        if (context != null) {
            SecurePreferences.saveToken(context, token);
        }
        // 换账号登录后头像必须重新拉取
        refreshAvatarVersion();
    }

    /**
     * 获取认证token
     */
    public String getToken() {
        // 优先从 SecurePreferences 获取
        if (context != null) {
            String token = SecurePreferences.getToken(context);
            if (token != null) {
                return token;
            }
        }
        // 其次从普通 SharedPreferences 获取
        return sharedPreferences.getString(TOKEN_KEY, null);
    }

    /**
     * 清除认证token（退出登录）
     */
    public void clearToken() {
        sharedPreferences.edit().remove(TOKEN_KEY).apply();
        // 同时清除 SecurePreferences 中的 token
        if (context != null) {
            SecurePreferences.clear(context);
        }
        // 退出后不允许继续命中当前账号的头像缓存
        refreshAvatarVersion();
    }

    /**
     * 获取头像版本号，供头像相关 URL 拼接缓存 key
     */
    public static long getAvatarVersion() {
        return avatarVersion;
    }

    /**
     * 刷新头像版本号：登录 / 退出 / 上传或重置头像后调用
     */
    public static void refreshAvatarVersion() {
        avatarVersion = System.currentTimeMillis();
    }

    /**
     * 检查是否已登录
     */
    public boolean isLoggedIn() {
        return getToken() != null;
    }

    /**
     * 保存用户信息到本地
     */
    public void saveUserInfo(String userJson) {
        sharedPreferences.edit().putString("user_info", userJson).apply();
    }

    /**
     * 获取本地保存的用户信息
     */
    public String getUserInfo() {
        return sharedPreferences.getString("user_info", null);
    }

    /**
     * 清除本地保存的用户信息
     */
    public void clearUserInfo() {
        sharedPreferences.edit().remove("user_info").apply();
    }

    /**
     * 保存用户ID
     */
    public void saveUserId(int userId) {
        sharedPreferences.edit().putInt(USER_ID_KEY, userId).apply();
    }

    /**
     * 获取用户ID
     */
    public int getUserId() {
        // 优先从 SecurePreferences 获取
        if (context != null) {
            int userId = SecurePreferences.getUserId(context);
            if (userId != -1) {
                return userId;
            }
        }
        // 其次从普通 SharedPreferences 获取
        return sharedPreferences.getInt(USER_ID_KEY, -1);
    }

    /**
     * 清除用户ID
     */
    public void clearUserId() {
        sharedPreferences.edit().remove(USER_ID_KEY).apply();
    }

    /**
     * 手动切换设备类型
     * @param isEmulator true-模拟器，false-真机
     */
    public static void switchDeviceType(Context context, boolean isEmulator) {
        if (context == null) return;
        
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(DEVICE_TYPE_KEY, isEmulator ? "emulator" : "real_device").apply();
        
        // 清除当前实例，强制重新创建
        instance = null;
    }

    /**
     * 获取当前设备类型
     */
    public static String getDeviceType(Context context) {
        if (context == null) return "unknown";
        
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(DEVICE_TYPE_KEY, "emulator");
    }
    
    /**
     * 设置自定义API地址
     * @param customUrl 自定义API地址，如 "http://192.168.1.5:8080/api/"
     */
    public static void setCustomBaseUrl(Context context, String customUrl) {
        if (context == null) return;
        
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (customUrl != null && !customUrl.isEmpty()) {
            // 确保URL以/结尾
            if (!customUrl.endsWith("/")) {
                customUrl = customUrl + "/";
            }
            prefs.edit().putString(CUSTOM_BASE_URL_KEY, customUrl).apply();
            customBaseUrlCache = customUrl;
        } else {
            prefs.edit().remove(CUSTOM_BASE_URL_KEY).apply();
            customBaseUrlCache = null;
        }
        
        // 清除当前实例，强制重新创建
        instance = null;
    }
    
    /**
     * 获取自定义API地址。
     * 未传 Context 时返回运行时缓存，供 getBaseUrl() 这类静态场景复用。
     */
    public static String getCustomBaseUrl(Context context) {
        if (context == null) {
            return customBaseUrlCache;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String customUrl = prefs.getString(CUSTOM_BASE_URL_KEY, null);

        // 历史遗留的错误 IP，命中后直接丢弃，避免接口与图片都指向不存在的地址
        if (customUrl != null && !customUrl.isEmpty() && customUrl.contains("10.56.35.56")) {
            prefs.edit().remove(CUSTOM_BASE_URL_KEY).apply();
            customBaseUrlCache = null;
            return null;
        }
        return customUrl;
    }
    
    /**
     * 清除自定义API地址，恢复自动检测
     */
    public static void clearCustomBaseUrl(Context context) {
        if (context == null) return;
        
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(CUSTOM_BASE_URL_KEY).apply();
        customBaseUrlCache = null;
        
        // 清除当前实例，强制重新创建
        instance = null;
    }
    
    /**
     * 获取当前使用的API地址
     */
    public static String getCurrentBaseUrl(Context context) {
        if (context == null) return null;
        
        // 自定义地址优先，内部已处理历史遗留的错误 IP
        String customUrl = getCustomBaseUrl(context);
        if (customUrl != null && !customUrl.isEmpty()) {
            return customUrl;
        }
        
        // 检测设备类型并返回对应的API地址
        boolean isEmulator = isEmulator(
            Build.MODEL,
            Build.MANUFACTURER,
            Build.PRODUCT,
            Build.HARDWARE
        );
        
        return isEmulator ? BASE_URL_EMULATOR : BASE_URL_REAL_DEVICE;
    }
    
    /**
     * 获取基础URL（用于加载图片等资源）
     * 优先使用用户设置的自定义地址，未设置时按设备类型返回默认值
     */
    public static String getBaseUrl() {
        // 优先使用自定义地址：真机调试常把地址改成电脑的局域网 IP，
        // 图片必须与接口走同一个 host，否则所有图片都会加载失败
        String customUrl = customBaseUrlCache;
        if (customUrl != null && !customUrl.isEmpty()) {
            return customUrl;
        }

        // 检测设备类型
        boolean isEmulator = isEmulator(
            Build.MODEL,
            Build.MANUFACTURER,
            Build.PRODUCT,
            Build.HARDWARE
        );
        
        String baseUrl = isEmulator ? BASE_URL_EMULATOR : BASE_URL_REAL_DEVICE;
        
        // 如果是真机且使用的是模拟器地址，给出警告
        if (!isEmulator && baseUrl.contains("10.0.2.2")) {
            Log.w(TAG, "WARNING: Real device is using emulator IP (10.0.2.2). " +
                       "Please check BASE_URL_REAL_DEVICE configuration.");
        }
        
        return baseUrl;
    }
    
    /**
     * 清除ApiClient实例，用于重新初始化
     */
    public void clearInstance() {
        instance = null;
    }
    
    /**
     * 重试拦截器，用于处理网络请求失败时的重试。
     * 仅对幂等请求（GET/HEAD/OPTIONS）重试：
     * POST 重发可能造成重复下单、重复领券、重复评价，这类请求失败后应交由用户手动重试
     */
    private static class RetryInterceptor implements Interceptor {
        private final int maxRetries;

        public RetryInterceptor(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            if (!isIdempotent(request.method())) {
                return chain.proceed(request);
            }

            Response response = null;
            IOException lastException = null;
            
            for (int i = 0; i <= maxRetries; i++) {
                try {
                    response = chain.proceed(request);
                    
                    // 如果是客户端错误（4xx），不重试
                    if (response.code() >= 400 && response.code() < 500) {
                        return response;
                    }
                    
                    // 如果成功或是最后一次尝试，返回响应
                    if (response.isSuccessful() || i == maxRetries) {
                        return response;
                    }
                    
                    // 关闭响应体，准备重试
                    response.close();
                    
                } catch (IOException e) {
                    lastException = e;
                    Log.d(TAG, "Request failed, retrying " + i + "/" + maxRetries + ": " + e.getMessage());
                    
                    // 非IO异常不重试
                    if (!(e instanceof java.net.SocketTimeoutException || 
                          e instanceof java.net.ConnectException ||
                          e instanceof java.net.UnknownHostException)) {
                        throw e;
                    }
                    
                    // 重试前等待一段时间（指数退避）
                    if (i < maxRetries) {
                        try {
                            long waitTime = 1000 * (long) Math.pow(2, i); // 1s, 2s, 4s...
                            Thread.sleep(waitTime);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new IOException(ie);
                        }
                    }
                }
            }
            
            throw lastException != null ? lastException : new IOException("Unknown error");
        }

        private static boolean isIdempotent(String method) {
            return "GET".equalsIgnoreCase(method)
                    || "HEAD".equalsIgnoreCase(method)
                    || "OPTIONS".equalsIgnoreCase(method);
        }
    }
    
    /**
     * 缓存控制拦截器，用于控制网络请求的缓存行为。
     *
     * 只有商品、分类这类「所有人看到的都一样」的公共内容才允许走磁盘缓存；
     * 购物车、订单、消息、优惠券、地址等用户私有数据一律 no-store。
     * 否则加购/下单后重新进入页面，拿到的还是 5 分钟前的旧响应，
     * 用户会看到「购物车已经加了商品却仍是空」这种假故障。
     */
    private static class CacheControlInterceptor implements Interceptor {
        private static final String CACHE_CONTROL_VALUE =
                "public, max-age=300, stale-while-revalidate=3600";

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();

            if (!"GET".equals(request.method())) {
                return chain.proceed(request);
            }

            boolean cacheable = isCacheablePath(request.url().encodedPath());
            if (cacheable) {
                request = request.newBuilder()
                        .header("Cache-Control", CACHE_CONTROL_VALUE)
                        .build();
            } else {
                // 用户私有数据：既不读缓存也不写缓存
                request = request.newBuilder()
                        .header("Cache-Control", "no-store")
                        .build();
            }

            Response response = chain.proceed(request);

            // 服务端已显式声明缓存策略时（比如头像接口的 no-store）必须原样保留，
            // 否则会被这里的 public/max-age 覆盖，导致客户端一直命中旧数据
            if (cacheable && response.isSuccessful() && response.header("Cache-Control") == null) {
                response = response.newBuilder()
                        .header("Cache-Control", CACHE_CONTROL_VALUE)
                        .build();
            }

            return response;
        }

        /**
         * 仅商品列表/详情与分类允许缓存。
         * 库存类接口（/products/{id}/stock）时效性强，不放行。
         */
        private static boolean isCacheablePath(String path) {
            if (path == null) {
                return false;
            }
            if (path.equals("/api/categories")) {
                return true;
            }
            return path.matches("^/api/products(/\\d+)?$");
        }
    }
}