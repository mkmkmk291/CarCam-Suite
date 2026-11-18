@echo off
chcp 65001 >nul
REM ============================================
REM 抓取车机端录像服务器日志（便于排查问题）
REM 用法：先 adb connect 车机，再运行本脚本
REM 停止：按 Ctrl+C
REM ============================================
cd /d "%~dp0"
echo 正在抓取日志（包名 carcam，按 Ctrl+C 停止）...
echo 日志同时保存到 logcat_carcam.log
adb logcat -v time | findstr /i "carcam HttpServer CamServerService AndroidRuntime" > logcat_carcam.log
