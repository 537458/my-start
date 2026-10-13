# 附录B 主要源代码

---

## B.1 后端核心代码

### B.1.1 项目启动类

**文件**：`backend/src/main/java/com/petshop/PetShopApplication.java`

```java
@SpringBootApplication
@EnableScheduling
public class PetShopApplication {
    public static void main(String[] args) {
        SpringApplication.run(PetShopApplication.class, args);
    }
}
```

**说明**：Spring Boot启动入口，`@EnableScheduling`启用定时任务。

---

### B.1.2 控制层实现

**文件**：`backend/src/main/java/com/petshop/controller/AuthController.java`

```java
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    // 用户注册
    @PostMapping("/register")
    public Response<User> register(@RequestBody User user) {
        User registeredUser = userService.registerUser(user);
        return Response.success("注册成功", registeredUser);
    }

    // 用户登录
    @PostMapping("/login")
    public Response<Map<String, Object>> login(@RequestBody Map<String, String> loginData) {
        String username = loginData.get("username");
        String password = loginData.get("password");
        User user = userService.login(username, password);

        // 生成JWT Token
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", String.valueOf(user.getId()));
        claims.put("username", user.getUsername());
        String token = jwtUtil.generateToken(claims);

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("user", user);
        return Response.success("登录成功", response);
    }
}
```

**说明**：处理用户注册和登录请求，登录成功后生成JWT Token返回。

---

### B.1.3 业务层实现

**文件**：`backend/src/main/java/com/petshop/service/UserService.java`

```java
public interface UserService {
    User registerUser(User user);           // 用户注册
    User login(String username, String password);  // 用户登录验证
    User findById(Integer id);              // 根据ID查询用户
    User update(User user);                 // 更新用户信息
    void delete(Integer id);                // 删除用户
    boolean updatePassword(Integer userId, String oldPassword, String newPassword);  // 修改密码
}
```

**说明**：定义用户业务层接口规范，包含注册、登录、信息管理等核心方法。

---

### B.1.4 安全认证实现

**文件**：`backend/src/main/java/com/petshop/util/JwtUtil.java`

```java
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private Long expiration;

    // 生成Token
    public String generateToken(Map<String, Object> claims) {
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
                .compact();
    }

    // 解析Token
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secret.getBytes()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 验证Token有效性
    public boolean validateToken(String token) {
        Claims claims = parseToken(token);
        return claims != null && !claims.getExpiration().before(new Date());
    }
}
```

**文件**：`backend/src/main/java/com/petshop/config/JwtAuthenticationFilter.java`

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        String token = extractTokenFromRequest(request);

        if (token != null && jwtUtil.validateToken(token)) {
            Integer userId = jwtUtil.getUserIdFromToken(token);
            String username = jwtUtil.getUsernameFromToken(token);

            // 设置认证信息
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(userId, null, 
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        return (bearerToken != null && bearerToken.startsWith("Bearer ")) 
            ? bearerToken.substring(7) : null;
    }
}
```

**说明**：JWT工具类负责Token生成与验证，过滤器拦截请求自动认证。

---

## B.2 前端核心代码

### B.2.1 前端路由配置

**文件**：`app/src/main/AndroidManifest.xml`

```xml
<application android:name=".PetShopApplication">
    <!-- 启动页（入口） -->
    <activity android:name=".activity.SplashActivity" android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
    
    <!-- 主要页面 -->
    <activity android:name=".activity.LoginActivity" />
    <activity android:name=".activity.MainActivity" />
    <activity android:name=".activity.ProductDetailActivity" />
    <activity android:name=".activity.OrderActivity" />
</application>
```

**文件**：`app/src/main/java/com/petshop/activity/MainActivity.java`

```java
public class MainActivity extends AppCompatActivity {

    private HomeFragment homeFragment;
    private CategoryFragment categoryFragment;
    private CartFragment cartFragment;
    private ProfileFragment profileFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 初始化Fragment
        homeFragment = new HomeFragment();
        categoryFragment = new CategoryFragment();
        cartFragment = new CartFragment();
        profileFragment = new ProfileFragment();

        // 默认显示首页
        switchFragment(homeFragment);

        // 底部导航切换
        binding.bnvMain.setOnNavigationItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_home) switchFragment(homeFragment);
            else if (item.getItemId() == R.id.nav_category) switchFragment(categoryFragment);
            else if (item.getItemId() == R.id.nav_cart) switchFragment(cartFragment);
            else if (item.getItemId() == R.id.nav_profile) switchFragment(profileFragment);
            return true;
        });
    }

    private void switchFragment(Fragment target) {
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fl_content, target)
            .commit();
    }
}
```

**说明**：AndroidManifest声明Activity组件，MainActivity管理底部导航Fragment切换。

---

### B.2.2 HTTP请求封装

**文件**：`app/src/main/java/com/petshop/api/ApiClient.java`

```java
public class ApiClient {
    private static String BASE_URL_EMULATOR = "http://10.0.2.2:8080/api/";
    private static String BASE_URL_REAL_DEVICE = "http://10.21.229.222:8080/api/";
    
    private static ApiClient instance;
    private ApiService apiService;

    // 单例获取
    public static ApiClient getInstance(Context context) {
        if (instance == null) instance = new ApiClient(context);
        return instance;
    }

    private ApiClient(Context context) {
        // OkHttp配置
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(chain -> {
                Request request = chain.request().newBuilder()
                    .header("Authorization", "Bearer " + getToken())
                    .build();
                return chain.proceed(request);
            })
            .build();

        // Retrofit配置
        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(determineBaseUrl(context))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build();

        apiService = retrofit.create(ApiService.class);
    }

    // Token管理
    public void saveToken(String token) { prefs.edit().putString("token", token).apply(); }
    public String getToken() { return prefs.getString("token", null); }
    public void clearToken() { prefs.edit().remove("token").apply(); }
}
```

**说明**：Retrofit + OkHttp封装HTTP请求，自动添加Token认证头，支持模拟器/真机自动切换。

---

### B.2.3 登录页面组件

**文件**：`app/src/main/java/com/petshop/activity/LoginActivity.java`

```java
public class LoginActivity extends AppCompatActivity {

    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        apiService = ApiClient.getInstance(this).getApiService();

        // 已登录则跳转主页
        if (ApiClient.getInstance(this).isLoggedIn()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }

        binding.btnLogin.setOnClickListener(v -> login());
    }

    private void login() {
        String username = binding.tilUsername.getEditText().getText().toString();
        String password = binding.tilPassword.getEditText().getText().toString();

        Map<String, String> params = new HashMap<>();
        params.put("username", username);
        params.put("password", password);

        apiService.login(params).enqueue(new NetworkCallback<LoginData>(this) {
            @Override
            protected void onSuccess(LoginData data) {
                // 保存Token和用户信息
                SecurePreferences.saveToken(LoginActivity.this, data.getToken());
                SecurePreferences.saveUserId(LoginActivity.this, data.getUser().getId());
                
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            }

            @Override
            protected void onBusinessError(int code, String message) {
                ToastUtil.showError(LoginActivity.this, "登录失败: " + message);
            }
        });
    }
}
```

**说明**：登录页面，调用API验证用户，成功后保存Token并跳转主页。

---

### B.2.4 首页仪表盘组件

**文件**：`app/src/main/java/com/petshop/fragment/HomeFragment.java`

```java
public class HomeFragment extends Fragment {

    private ViewPager2 vpBanner;
    private RecyclerView rvHotProducts;
    private ProductAdapter productAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        initBanner(view);       // 轮播图
        initHotProducts(view);  // 热门商品
        loadProducts();         // 加载数据
        
        return view;
    }

    // 轮播图初始化
    private void initBanner(View view) {
        vpBanner.setAdapter(new BannerAdapter(bannerImages));
        
        // 自动轮播
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            int next = (vpBanner.getCurrentItem() + 1) % bannerImages.length;
            vpBanner.setCurrentItem(next);
        }, 3000);
    }

    // 热门商品列表
    private void initHotProducts(View view) {
        rvHotProducts.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        productAdapter = new ProductAdapter(new ArrayList<>(), getContext());
        rvHotProducts.setAdapter(productAdapter);
    }

    // 加载商品数据
    private void loadProducts() {
        ApiClient.getInstance(getContext()).getApiService().getProducts()
            .enqueue(new NetworkCallback<List<Product>>(getContext()) {
                @Override
                protected void onSuccess(List<Product> data) {
                    productAdapter.updateData(data);
                }
            });
    }
}
```

**说明**：首页Fragment，包含轮播图自动切换、热门商品横向列表展示。

---

**文档版本**：V1.0  
**适用项目**：宠物商城（PetShop）