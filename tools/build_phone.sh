#!/bin/bash
# AppLauncher 手机版构建（竖屏、targetSdk 33、带 QUERY_ALL_PACKAGES）
set -euo pipefail

PROJ="$(cygpath -m "$(cd "$(dirname "$0")/.." && pwd)")"
SDK="$(cygpath -m "$LOCALAPPDATA")/Android/Sdk"
BT="$SDK/build-tools/35.0.1"
PLATFORM="$SDK/platforms/android-33/android.jar"
JBR="C:/Program Files/Android/Android Studio/jbr"
JAVA="$JBR/bin/java.exe"
JAVAC="$JBR/bin/javac.exe"
JARTOOL="$JBR/bin/jar.exe"
KS="C:/Users/momo/work/ai_work/TvApkInstaller/keystore/tv.jks"

B="$PROJ/build-phone"
rm -rf "$B"
mkdir -p "$B/gen" "$B/obj" "$PROJ/dist"

# 生成手机版 manifest：去横屏锁定 + 权限
mkdir -p "$PROJ/phone"
sed -e 's|android:screenOrientation="landscape"||' \
    -e 's|    <application|    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>\n\n    <application|' \
    "$PROJ/app/AndroidManifest.xml" > "$PROJ/phone/AndroidManifest.xml"

"$BT/aapt2.exe" compile --dir "$PROJ/app/res" -o "$B/res.zip"
"$BT/aapt2.exe" link -o "$B/base.apk" -I "$PLATFORM" \
  --manifest "$PROJ/phone/AndroidManifest.xml" \
  --java "$B/gen" \
  --min-sdk-version 17 --target-sdk-version 33 \
  --version-code 2 --version-name 1.0-phone \
  "$B/res.zip"

SRCS=$(find "$B/gen" "$PROJ/app/src" -name '*.java')
"$JAVAC" -source 8 -target 8 -encoding UTF-8 -Xlint:-options \
  -bootclasspath "$PLATFORM" -d "$B/obj" $SRCS
"$JARTOOL" cf "$B/app.jar" -C "$B/obj" .
"$JAVA" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --release \
  --lib "$PLATFORM" --min-api 17 --output "$B" "$B/app.jar"
python "$PROJ/../TvApkInstaller/tools/add_dex.py" "$B/base.apk" "$B/classes.dex"
"$BT/zipalign.exe" -f 4 "$B/base.apk" "$B/aligned.apk"
"$JAVA" -cp "$BT/lib/apksigner.jar" com.android.apksigner.ApkSignerTool sign \
  --ks "$KS" --ks-pass pass:tv123456 --key-pass pass:tv123456 --ks-key-alias apkinst \
  --min-sdk-version 17 --v1-signing-enabled true --v2-signing-enabled true \
  --out "$PROJ/dist/AppLauncher_v1.0_phone.apk" "$B/aligned.apk"
"$BT/aapt2.exe" dump badging "$PROJ/dist/AppLauncher_v1.0_phone.apk" | grep -E "package:|sdkVersion|application-label"
echo "完成: dist/AppLauncher_v1.0_phone.apk"
