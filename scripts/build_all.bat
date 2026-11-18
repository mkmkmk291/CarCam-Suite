@echo off
chcp 65001 >nul
REM ============================================
REM Windows 一键重编译两个 APK（改源码后使用）
REM 前置：已安装 Android SDK（ANDROID_HOME 或本脚本同目录 sdk 文件夹）
REM       已安装 JDK 8+（java/javac 在 PATH）
REM 输出：dist\CarCamServer.apk / dist\CarCamViewer.apk
REM ============================================
setlocal enabledelayedexpansion
cd /d "%~dp0"

if defined ANDROID_HOME (
  set "SDK=%ANDROID_HOME%"
) else (
  set "SDK=%CD%\sdk"
)
if not exist "%SDK%\build-tools\30.0.3" (
  echo 未找到 %SDK%\build-tools\30.0.3，请安装 build-tools;30.0.3 并设置 ANDROID_HOME
  pause & exit /b 1
)
set "BT=%SDK%\build-tools\30.0.3"
set "AJ=%SDK%\platforms\android-30\android.jar"

if not exist tools\debug.keystore (
  keytool -genkeypair -keystore tools\debug.keystore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US"
)

if not exist dist mkdir dist
if not exist build mkdir build

REM 生成图标
python tools\make_icons.py || py tools\make_icons.py

call :build server CarCamServer
call :build viewer CarCamViewer
echo.
echo 全部构建完成，APK 在 dist 目录。
pause
exit /b 0

:build
set "name=%~1"
set "outname=%~2"
echo ==^> 构建 %name%
if exist build\%name% rmdir /s /q build\%name%
mkdir build\%name%\classes build\%name%\dexout
"%BT%\aapt2" compile --dir res -o build\%name%\res.zip
"%BT%\aapt2" link -o build\%name%\base.apk -I "%AJ%" --manifest %name%\AndroidManifest.xml build\%name%\res.zip --min-sdk-version 26 --target-sdk-version 29 --version-code 1 --version-name 1.0.0
dir /s /b %name%\src\*.java > build\%name%\sources.txt
javac -source 8 -target 8 -classpath "%AJ%" -d build\%name%\classes @build\%name%\sources.txt
dir /s /b build\%name%\classes\*.class > build\%name%\classlist.txt
"%BT%\d8" --lib "%AJ%" --release --output build\%name%\dexout @build\%name%\classlist.txt
copy /y build\%name%\base.apk build\%name%\unsigned.apk >nul
python -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1],'a').write(sys.argv[2],'classes.dex')" build\%name%\unsigned.apk build\%name%\dexout\classes.dex
"%BT%\zipalign" -f 4 build\%name%\unsigned.apk build\%name%\aligned.apk
"%BT%\apksigner" sign --ks tools\debug.keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out dist\%outname%.apk build\%name%\aligned.apk
echo    完成: dist\%outname%.apk
exit /b 0
