#!/bin/bash
# 一键构建两个 APK（Linux/macOS 环境，需已安装 Android SDK 与 JDK 8+）
# 用法: ./build_apk.sh
set -e
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-/home/user/android-sdk}"
BT="$SDK/build-tools/30.0.3"
AJ="$SDK/platforms/android-30/android.jar"
AAPT2="$BT/aapt2"
OUT="$(pwd)/dist"
KS="$(pwd)/tools/debug.keystore"
mkdir -p "$OUT" build

# 1) 生成图标
python3 tools/make_icons.py

# 2) 生成 debug 签名
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias androiddebugkey -keyalg RSA \
    -keysize 2048 -validity 10000 -storepass android -keypass android \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

build() {
  local name="$1"
  local outname="$2"
  echo "==> 构建 $name"
  local tmp="build/$name"
  rm -rf "$tmp"; mkdir -p "$tmp"
  "$AAPT2" compile --dir "res" -o "$tmp/res.zip"
  "$AAPT2" link -o "$tmp/base.apk" -I "$AJ" \
    --manifest "$name/AndroidManifest.xml" \
    "$tmp/res.zip" \
    --min-sdk-version 26 --target-sdk-version 29 \
    --version-code 1 --version-name 1.0.0
  mkdir -p "$tmp/classes" "$tmp/dexout"
  find "$name/src" -name '*.java' > "$tmp/sources.txt"
  javac -source 8 -target 8 -classpath "$AJ" -d "$tmp/classes" @"$tmp/sources.txt"
  find "$tmp/classes" -name '*.class' > "$tmp/classlist.txt"
  "$BT/d8" --lib "$AJ" --release --output "$tmp/dexout" $(cat "$tmp/classlist.txt")
  cp "$tmp/base.apk" "$tmp/unsigned.apk"
  python3 -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1],'a').write(sys.argv[2],'classes.dex')" "$tmp/unsigned.apk" "$tmp/dexout/classes.dex"
  "$BT/zipalign" -f 4 "$tmp/unsigned.apk" "$tmp/aligned.apk"
  "$BT/apksigner" sign --ks "$KS" --ks-key-alias androiddebugkey \
    --ks-pass pass:android --key-pass pass:android \
    --out "$OUT/$outname.apk" "$tmp/aligned.apk"
  "$BT/apksigner" verify --print-certs "$OUT/$outname.apk" >/dev/null
  echo "    完成: $OUT/$outname.apk"
}

build server CarCamServer
build viewer CarCamViewer
echo "全部构建完成。"
