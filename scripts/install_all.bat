@echo off
chcp 65001 >nul
REM ============================================
REM 一键安装：车机端录像服务器 + 手机端录像查看器
REM 前置条件：
REM   1) adb 已配置到 PATH，或本脚本与 adb.exe 同目录
REM   2) 车机已开启 WIFI ADB（拨号 *#111#* -> WIFI ADB）
REM      并已通过 adb connect <车机IP>:<端口> 连接成功
REM ============================================
setlocal
cd /d "%~dp0"

echo [1/2] 检查 adb 连接...
adb devices

echo.
echo [2/2] 安装 APK...
echo   - 车机端: dist\CarCamServer.apk
adb install -r "dist\CarCamServer.apk"
echo   - 手机端: dist\CarCamViewer.apk
adb install -r "dist\CarCamViewer.apk"

echo.
echo 完成！若 adb 当前连的是手机，请先连车机再重新执行本脚本。
echo 安装后：车机上打开"车机录像服务器" -> 启动服务 -> 开启热点；
echo          手机上打开"车机录像查看器" -> 连接热点 -> 自动扫描。
pause
