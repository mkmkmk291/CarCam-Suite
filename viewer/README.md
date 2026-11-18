# 车机录像查看器 · 手机端

与车机端 `CarCamServer` 配套使用的手机 App：自动扫描局域网内的录像服务器，
浏览录像目录、在线播放（支持拖动进度）、下载到手机 `Download` 目录。

## 源码结构

```
src/com/carcam/viewer/
├── MainActivity.java      # 首页：自动扫描热点网段/常见网段、手动填 IP、服务器条目列表
├── FileListActivity.java  # 目录浏览、视频列表、播放/下载
├── PlayerActivity.java    # 在线播放页
└── Util.java              # 工具类（网段枚举、大小格式化、下载）
```

## 扫描网段

默认扫描 `192.168.43.x`、`192.168.31.x`、`192.168.137.x`、`192.168.0.x`、`192.168.1.x`、`172.20.10.x`
等手机热点常用网段，端口 `8080`（与车机端默认一致）。

## 编译

```bash
bash tools/build_apk.sh          # 仓库根目录，Linux / macOS
scripts\build_all.bat            # Windows
```

产物：`dist/CarCamViewer.apk`，与仓库 `apk/车机录像查看器_1.0.0.apk` 同源。

## 安装

```bat
adb install -r apk\车机录像查看器_1.0.0.apk
```
