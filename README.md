# CarCam Suite · 车机行车记录仪工具套件

面向 **雪佛兰科鲁泽（23–25 款，Android 10 车机）** 等可安装第三方 APK 的安卓车机，
用于在**局域网直连**（手机热点）环境下浏览、播放、下载行车记录仪录像，并附赠一个
基于局域网 WebView 的 **蓝奏云播放器**。

全程局域网直连，不上传云端、不消耗手机流量。

---

## 目录结构

| 目录 | 内容 | 说明 |
|---|---|---|
| `server/` | `CarCamServer` 车机端录像服务器源码（Java） | 车机上运行，把录像目录通过 HTTP 共享出来 |
| `viewer/` | `车机录像查看器` 手机端源码（Java） | 自动扫描局域网服务器、列表、在线播放、下载 |
| `lanzou-player/` | `蓝奏云播放器` 源码（Web 前端 + Java 反编译整理） | 基于本地 `MiniHttpServer` + WebView 的播放器 |
| `apk/` | 三个已签名安装包 | 见下方「安装包」 |
| `res/` | 两个车机 App 共用的启动图标资源 | `aapt2` 编译时使用 |
| `tools/` | `build_apk.sh`、`make_icons.py`、`debug.keystore` | 命令行编译与图标生成 |
| `scripts/` | `build_all.bat`、`install_all.bat`、`logcat_carcam.bat` | Windows 一键编译 / 安装 / 抓日志 |
| `docs/` | `原方案说明.md`、`架构说明.html` | 完整使用说明与架构图 |

---

## 安装包（`apk/`）

| 文件 | 包名 | 版本 | 适用端 |
|---|---|---|---|
| `CarCamServer-3.5.0-system.apk` | `com.carcam.server` | 3.5.0（`android.uid.system`） | 车机端 · 系统签名版 |
| `车机录像查看器_1.0.0.apk` | `com.carcam.viewer` | 1.0.0 | 手机端 |
| `蓝奏云播放器_1.0.apk` | `com.doubao.lanzouplayer` | 1.0 | 手机 / 车机 |

> 说明：`server/` 里的源码为 **3.5.1**（在 3.5.0 基础上增加了启动完整性检查 `Guard`），
> 因此版本号比仓库内的 3.5.0 安装包高一位；用源码重新编译即可得到 3.5.1。

安装（Windows，adb 已连接车机）：

```bat
adb install -r apk\CarCamServer-3.5.0-system.apk
adb install -r apk\车机录像查看器_1.0.0.apk
```

`CarCamServer-*-system.apk` 使用了 `android:sharedUserId="android.uid.system"`，
必须以**平台签名**安装（普通 debug 签名会报 `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE`）；
无平台签名权限时可在车机设置里用「未知来源安装」直接装 `-phone` 变体，或参考 `docs/原方案说明.md`。

---

## 快速上手

1. 手机开热点 → 车机连接该热点（同一局域网）。
2. 车机装 `CarCamServer`，授予存储权限，点【启动录像服务器】，记下首页显示的 `http://192.168.x.x:8080`。
3. 手机装查看器，点【自动扫描】→ 选中服务器 → 浏览 / 播放 / 下载；也可以直接用手机浏览器访问上面的地址。

完整步骤、常见问题排查见 [`docs/原方案说明.md`](docs/原方案说明.md)。

---

## 从源码编译

前置：JDK 8+、Android SDK build-tools 30.0.3、platforms android-30。

```bash
# Linux / macOS
bash tools/build_apk.sh
```

```bat
:: Windows（需设置 ANDROID_HOME）
scripts\build_all.bat
```

产物输出到 `dist/`。改端口：`server/src/com/carcam/server/CamServerService.java` 中 `DEFAULT_PORT = 8080`。

`lanzou-player/` 为独立工程，其 `AndroidManifest.xml` 为按反编译结果与源码复原的编译用清单，
详见该目录下 `README.md`。

---

## 安全与隐私

- HTTP 服务仅监听局域网（手机热点），**无鉴权**：连上同一热点的设备都能访问录像，请勿在公共网络使用。
- 记录仪视频包含车辆周边影像与位置信息，属个人隐私数据，请妥善保管、勿公开传播。
- 本项目仅供个人车辆自用与学习交流；车机破解、系统签名包安装等操作请自行评估风险。
