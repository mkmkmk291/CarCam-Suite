# CarCamServer · 车机端录像服务器

车机（Android 10，雪佛兰科鲁泽 23/24 款车机）上运行的两用 App：

1. **局域网录像服务**——把车机内的行车记录仪视频目录通过 HTTP 共享出来，手机浏览器或「车机录像查看器」即可浏览、在线播放（支持 Range 拖动）与下载。
2. **蓝奏云自动备份**——登录蓝奏云账号后，把记录仪新录像自动上传到网盘（默认「记录仪」文件夹），手机用「蓝奏云播放器」即可看云端视频，不受车机是否在线影响。

## 蓝奏云自动备份怎么用

首页【☁ 蓝奏云自动备份设置】里：

| 操作 | 说明 |
|---|---|
| 填账号 + 密码 →【登录】 | 走蓝奏云 PC 端登录接口，成功后自动保存 Cookie |
| 勾选【开启自动上传到蓝奏云】 | 后台线程持续扫描新录像并上传 |
| 【🧪 测试上传】 | 上传 1MB 测试文件，打印耗时与目标文件夹 `fol_id`，先验证链路再开自动备份 |
| WebView 备用登录 | 接口登录失败（验证码/风控）时改走网页登录 |

实现要点（见 `AutoUploader.java` / `LanZouAuth.java` / `LanZouCreds.java`）：

- 上传目标：根目录下名为「记录仪」的文件夹；找不到则传根目录，并在日志中提示；
- Cookie（`phpdisk_info` / `lanzou_ifo`）约 3 天有效且不滚动续期 → 到期或读盘失败时**用加密保存的凭据静默重登**，失败后 5 分钟内不重试，避免刷接口；
- 密码用 Android Keystore 的 AES-256-GCM 加密存 `filesDir/lz_cred.bin`，个别车机 Keystore 不可用时回退明文（仅为保证功能可用）；
- 蓝奏云免费版单文件上限 100MB。

## 源码结构

```
src/com/carcam/server/
├── MainActivity.java        # 首页 UI：启动/停止服务、显示 IP、扫描录像目录、上传设置
├── CamServerService.java    # 前台服务，持有 HttpServer
├── HttpServer.java          # HTTP 服务：目录列表、Range 播放、下载、上传接口
├── HtmlPages.java           # 内嵌 HTML 页面（浏览器端 UI）
├── VideoScanner.java        # 录像目录扫描/识别
├── AutoUploader.java        # 录像自动上传（蓝奏云 / 123 云盘 / WebDAV）
├── LanZouAuth.java          # 蓝奏云登录链路（Cookie、sign 参数）
├── LanZouClient.java        # 蓝奏云上传接口
├── LanZouCreds.java         # 账号凭据加密存储（Keystore AES-GCM，失败回退明文）
├── LanZouLoginActivity.java # 蓝奏云登录页
├── Pan123Client.java        # 123 云盘上传
├── WebDavClient.java        # WebDAV 上传
├── Guard.java               # 启动完整性检查（3.5.1 新增）
└── Util.java                # 工具类（IP、格式化等）
```

## 版本

- 源码：`3.5.1`（`AndroidManifest.xml` versionName），在 3.5.0 基础上加入 `Guard` 完整性校验。
- 仓库 `apk/CarCamServer-3.5.0-system.apk`：3.5.0 系统签名版，对应 3.5.1 之前的构建。

## 编译

`AndroidManifest.xml` 声明了 `android:sharedUserId="android.uid.system"`，
要装进车机需要**平台签名**；仅自用调试可先去掉该属性用 debug 签名编译。

```bash
# 仓库根目录
bash tools/build_apk.sh          # Linux / macOS
scripts\build_all.bat            # Windows（ANDROID_HOME 已配置）
```

默认端口 `8080`，改 `CamServerService.DEFAULT_PORT`。

## 默认扫描的录像目录

- `/dvr/Video/`：行车循环录像（厂商路径，文件形如 `yyyy-MM-dd-HH-mm-ss.mp4`）
- `/storage/emulated/0/DCIM/DVR/`：手动/锁定录像与抓拍（`Record/`、`Picture/`）

以上路径由反编译车机原厂记录仪 `com.jsbd.cameraservice` 确认。

## 安全

- 服务无鉴权，仅监听局域网；请勿在公共 Wi-Fi 下开启。
- 录像含车辆位置与周边影像，属隐私数据。
