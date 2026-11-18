# CarCamServer · 车机端录像服务器

车机（Android 10，雪佛兰科鲁泽 23–24 款）上运行的 HTTP 录像服务：把车机内的行车记录仪视频目录
通过局域网共享出来，手机浏览器或「车机录像查看器」即可浏览、在线播放（支持 Range 拖动）与下载。

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
