# 蓝奏云播放器 · Lanzou Player

**用来直接看蓝奏云里的视频**：登录蓝奏云账号后浏览云端文件夹，点视频即可在线播放，不用先下载到本地。
配合车机端 `CarCamServer` 的「蓝奏云自动备份」，形成
**车机自动上传录像 → 手机随时云端回看** 的闭环。

实现方式：App 在本地起一个 `MiniHttpServer`，WebView 加载内嵌网页 UI，网页侧的网盘请求由原生
`LanzouClient` 代发（绕过跨域与 Cookie 限制），视频流再由本地服务代理输出，从而在车机/手机上
得到接近原生播放器的体验。

## 源码结构

```
lanzou-player/
├── assets/
│   ├── index.html            # 前端页面
│   └── static/app.js         # 前端逻辑（列表、播放、下载）
│   └── static/style.css      # 样式
├── src/com/doubao/lanzouplayer/
│   ├── MainActivity.java     # WebView + 全屏播放 + 外部播放器跳转 + 凭据加密存储
│   ├── MiniHttpServer.java   # 本地 HTTP 服务，前端 API 与静态资源
│   └── LanzouClient.java     # 蓝奏云接口：登录、列目录、取直链、Cookie 管理
├── res/mipmap-xxhdpi/        # 启动图标
└── AndroidManifest.xml       # 编译用清单（见下方说明）
```

## 关于源码来源

- `assets/` 下的 `index.html`、`app.js`、`style.css` 与 APK 内完全一致（前端原始源码）。
- `src/` 下的 Java 为 **1.0 APK 的反编译整理版**（jadx 输出，包结构已还原为
  `src/com/doubao/lanzouplayer`），用于阅读和二次开发参考，个别位置可能需要按原构建方式微调后才能直接编译。
- `AndroidManifest.xml` 为按反编译结果与源码实际能力**复原的编译用清单**
  （APK 内的二进制清单无法直接还原为文本），包名、入口 Activity、所需权限与原 APK 一致：
  `INTERNET`、`ACCESS_NETWORK_STATE`，`usesCleartextTraffic=true`（WebView 加载 `http://127.0.0.1`）。

## 编译

需要 Android SDK build-tools + `android.jar`：

```bash
aapt2 compile --dir res -o build/res.zip
aapt2 link -o build/base.apk -I $SDK/platforms/android-34/android.jar \
  --manifest AndroidManifest.xml build/res.zip \
  --min-sdk-version 21 --target-sdk-version 34 --version-code 1 --version-name 1.0
javac -source 8 -target 8 -classpath $SDK/platforms/android-34/android.jar -d build/classes $(find src -name '*.java')
d8 --lib $SDK/platforms/android-34/android.jar --release --output build/dexout $(find build/classes -name '*.class')
# 合并 classes.dex + assets 到 base.apk 后 zipalign、apksigner 签名
```

`assets/` 必须一起打进 APK，否则首页白屏。

## 功能

- **登录蓝奏云账号**（`POST /api/login`，Cookie 由原生侧维护），进入后直接浏览**云端文件夹**
  （`GET /api/browse?folder_id=…&page=…`，支持分页）
- **云端在线播放**：`GET /api/stream/<file_id>?ext=…` 由本地服务代理转发，拖动进度不必整段先下载
- **云端下载**：`GET /api/download/<file_id>`，可交给系统浏览器/下载器续传
- 全屏播放、倍速、外部播放器接管（`ACTION_VIEW` 把直链交给 MX Player 等）
- 账号凭据用 Android Keystore（AES-GCM）加密存储；`/api/logout` 可退出

> 与车机端配合：`CarCamServer` 把新录像自动上传到蓝奏云同名账号的「记录仪」文件夹，
> 本播放器登录同一账号即可看到这些视频。

## 免责声明

本项目仅用于个人已购/自有网盘内容的便捷播放，请遵守蓝奏云服务条款，勿用于传播侵权内容。
