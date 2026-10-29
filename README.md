# PetShop 宠物商城

个人项目：宠物用品商城 + 养宠社区 + 宠物档案。Android 客户端（Java）+ Spring Boot 后端 + MySQL。

```
┌──────────────┐   HTTP/JSON    ┌──────────────────┐   JDBC   ┌─────────┐
│ Android App  │ ─────────────▶ │ Spring Boot :8080 │ ───────▶ │ MySQL 8 │
│  (Retrofit)  │ ◀───────────── │   context = /api  │ ◀─────── │ :3306   │
└──────────────┘   图片/JSON     └──────────────────┘          └─────────┘
```

| 目录 | 说明 |
| --- | --- |
| `app/` | Android 客户端，Gradle flavors 分 `dev` / `prod` 两套环境 |
| `backend/` | Spring Boot 后端，MyBatis-Plus + JWT 鉴权 |
| `database.sql` | 数据库结构参考快照 |
| `SOURCE_CODE.md` / `USER_MANUAL.md` | 源码说明 / 使用手册 |

---

## 1. 环境要求

| 组件 | 版本 | 备注 |
| --- | --- | --- |
| JDK | 17+ | 后端 `maven.compiler.target=17`，Android `compileOptions` 也是 17 |
| Android Studio | 任意近期版本 | 项目用 AGP 8.4.0 + Gradle 9.0.0（wrapper 自带，无需另装） |
| Android SDK | compileSdk 34 / minSdk 24 | 即 Android 7.0 及以上设备可装 |
| MySQL | 8.0+ | 需要 `utf8mb4` |
| Maven | 3.6+ | 构建后端 |

---

## 2. 启动后端

**唯一前置条件是本机 MySQL 服务在跑**。不需要手工建库建表 —— `DbInitializer` 在每次启动时会自动完成：

- `CREATE DATABASE IF NOT EXISTS petshop`
- 库为空时自动执行 `backend/src/main/resources/db/init.sql` 灌入商品、分类等初始数据
- 之后幂等补齐收藏、优惠券、社区、宠物档案等后增表与列

三种启动方式，任选其一：

```powershell
# 方式一：脚本（推荐，会先检查 JDK / Maven）
backend\start-backend.bat

# 方式二：Maven 直接跑
cd backend
mvn spring-boot:run

# 方式三：打成 jar 再运行
cd backend
mvn -DskipTests package
java -jar target\petshop-backend-1.0.0.jar
```

看到日志 `Started PetShopApplication` 即成功。验证一下：

```powershell
curl http://localhost:8080/api/health
# {"database":"CONNECTED","message":"PetShop API is running successfully","status":"UP"}
```

### 配置项

全部通过环境变量注入，默认值见 `backend/src/main/resources/application.yml`。可复制 `backend/.env.example` 按需修改：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | 后端监听端口 |
| `DB_URL` | `jdbc:mysql://localhost:3306/petshop?...` | 数据库连接串 |
| `DB_USERNAME` | `root` | 数据库账号 |
| `DB_PASSWORD` | `123456` | 数据库密码，**换机器时这个最常需要改** |
| `JWT_SECRET` | 空 | 留空则每次启动生成随机密钥，重启后旧 token 全部失效；对外部署必须设为 32 位以上随机串 |
| `XUNFEI_APPID` 等 | 空 | 讯飞星火 AI 能力，不配则相关功能不可用 |

PowerShell 里临时设置：

```powershell
$env:DB_PASSWORD = "你的密码"
$env:JWT_SECRET  = "至少32位的随机字符串"
mvn spring-boot:run
```

> 提示：`backend/init-db.ps1` / `init-db.bat` 里写的是作者本机的 MySQL 安装路径和密码，换环境不要直接跑，改完路径再用；正常情况用不到它们。

---

## 3. 让 App 连上后端

App 的服务器地址按下面的优先级决定：

**设置页手填的地址 > 打包时烧进去的地址 > 按设备类型自动选择**

| 使用场景 | App 实际访问的地址 | 你需要做的 |
| --- | --- | --- |
| 安卓模拟器 | `http://10.0.2.2:8080/api/` | 什么都不用做，开箱即用 |
| USB 线连真机 | `http://127.0.0.1:8080/api/` | 执行一次端口转发：`adb reverse tcp:8080 tcp:8080` |
| 真机无线 / 别人的手机 | `https://xxx/api/`（公网地址） | 见下一节做内网穿透 |

`adb reverse` 是日常开发最省事的一条路：手机上的 `127.0.0.1:8080` 会被直接映射到你电脑的 8080，改代码、重装 App 都不用重新配地址。注意拔线、重启手机或执行过 `adb kill-server` 之后要重新执行一次。

如果地址填错了想改回来：**App → 我的 → 设置 → 服务器地址 → 恢复自动检测**。填新地址时只写 `192.168.1.5:8080` 或 `https://xxx.cpolar.cn` 这样的简写也行，App 会自动补协议、补 `/api/` 路径和结尾斜杠，并在保存后立即调 `/health` 自检。

---

## 4. 内网穿透：让别人也能连上你的数据库

后端和数据库都跑在你自己电脑上，`localhost` / `10.0.2.2` / `192.168.x.x` 对别人的手机来说都不可达。内网穿透的作用是给本机 8080 分配一个公网域名。

以 [cpolar](https://www.cpolar.com/) 为例（ngrok 同理）：

```powershell
# 1) 注册后在官网控制台拿到 authtoken，绑定一次即可
cpolar authtoken 你的authtoken

# 2) 把本地 8080 映射出去，窗口里会给出公网地址
cpolar http 8080
#   例如 https://39bd909b.r3.cpolar.cn  →  http://localhost:8080
```

拿到域名后，地址要写成 **`https://<你的域名>/api/`**（`/api` 这个后端的 context-path 不能漏）。

- 自己临时测试：直接填进 App 的「设置 → 服务器地址」
- 要发给别人：填进 `app/build.gradle` 的 `SHARE_API_BASE_URL`，重新打包

必须知道的限制：

- 电脑不能关机、不能休眠，后端和 cpolar 窗口都要常开
- 免费隧道的域名**每次重启都会变**，变一次就得重新填或重新打包
- 所有使用者共用你这一份数据库（有 JWT 做用户隔离，但库是同一个）
- 这等于把服务开放到了公网，请先设好 `JWT_SECRET`、改掉 MySQL 默认密码

想让服务长期在线，正路是把 `backend/` 和 MySQL 部署到一台云服务器，然后把公网 IP 填进 `SHARE_API_BASE_URL`，那样就不依赖你的电脑了。

---

## 5. 打包与分享

### 5.1 自己调试用（dev 包）

```powershell
.\gradlew.bat installDevDebug
```

装到当前连接的设备上。模拟器直连本机后端；真机记得先 `adb reverse`。

### 5.2 发给别人（prod 包）

1. 打开 `app/build.gradle`，改文件开头这一行：

   ```groovy
   def SHARE_API_BASE_URL = "https://<你的隧道域名>/api/"
   ```

2. 构建：

   ```powershell
   .\gradlew.bat assembleProdRelease
   ```

3. 产物在：

   ```
   app\build\outputs\apk\prod\release\app-prod-release.apk
   ```

   把这个 apk 发给对方，安装时允许「来自未知来源的应用」即可。

关于签名的说明：`release` 构建目前借用 debug 签名，目的是让产物可以直接安装分享。真要上架或正式分发，需要生成自己的 keystore 并替换 `signingConfig`。

### 5.3 已经替你踩平的几个坑

| 问题 | 现状 |
| --- | --- |
| IDE 打出的包带 `testOnly`，别人安装被拒 | `gradle.properties` 里已设 `android.injected.testOnly=false` |
| release 未配签名导致 APK 根本装不上 | `build.gradle` 的 release 已加 `signingConfig` |
| `lintVitalAnalyzeProdRelease` 崩溃导致打不出包 | 已设 `lint.checkReleaseBuilds false` |
| 生产包地址写死模拟器 IP，等于废包 | `prod` flavor 改为读 `SHARE_API_BASE_URL` |

---

## 6. 常见问题

**App 打开后首页空白、图片加载不出来**
大概率是服务器地址不对，或设置里残留了一个连不上的旧地址（它的优先级最高，每个请求会白等 30 秒）。先到「设置 → 服务器地址 → 恢复自动检测」，然后彻底关掉 App 重开。

**模拟器连不上后端**
模拟器里的"本机"不是你的电脑，必须用 `10.0.2.2`。确认后端在跑：电脑浏览器打开 `http://localhost:8080/api/health`。

**真机连电脑局域网 IP 失败**
先确认两者在**同一网段**：电脑 `ipconfig`，手机看 Wi-Fi 详情。如果电脑是有线 `192.168.100.x`、手机是 Wi-Fi `192.168.110.x`，那是两个网络，直连必然失败，请改用 USB 线 + `adb reverse`，或走内网穿透。

**`baseUrl must end in /`**
Retrofit 要求地址以斜杠结尾。App 内填地址会自动补，手工改代码时注意。

**启动报端口被占用**
`8080` 被上一个没关干净的后端进程占着，结束旧的 java 进程或改 `SERVER_PORT`。

**换了电脑，App 一直连不上**
检查三处：MySQL 密码是否改了（`DB_PASSWORD`）、`SHARE_API_BASE_URL` 是否还是旧隧道域名、手机上设置页是否残留旧地址。

---

## 7. 安全注意

- `.env`、`local.properties`、密钥文件都已在 `.gitignore` 里，不要提交
- 对外暴露后 `/api/health` 只返回连接状态，不再输出 JDBC 连接串与数据库版本
- 公网部署前务必设置 `JWT_SECRET` 并修改 MySQL 默认密码
