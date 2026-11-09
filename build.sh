#!/bin/bash
# AppLauncher 电视版构建脚本（无 Gradle：aapt2 -> javac -> d8 -> zipalign -> apksigner）
#
# 环境要求：
#   - Android SDK（需 build-tools 35.0.1 与 platforms/android-33），通过
#     ANDROID_SDK_ROOT 或 ANDROID_HOME 指定；Windows 下若未设置则回退到
#     %LOCALAPPDATA%/Android/Sdk
#   - JDK 17+（Android Studio 自带的 JBR 即可），通过 JAVA_HOME 指定
#   - Python 3（仅构建图标时需要 Pillow）
#
# 首次构建会在 keystore/ 下自动生成本地调试签名密钥（密码见下方 KS_PASS）。
# 若要发布自己的 Release，请替换为你自己的密钥。

set -euo pipefail

PROJ="$(cygpath -m "$(cd "$(dirname "$0")" && pwd)" 2>/dev/null || echo "$(cd "$(dirname "$0")" && pwd)")"

# ---- 定位 Android SDK ----
if [ -n "${ANDROID_SDK_ROOT:-}" ]; then
  SDK="$(cygpath -m "$ANDROID_SDK_ROOT" 2>/dev/null || echo "$ANDROID_SDK_ROOT")"
elif [ -n "${ANDROID_HOME:-}" ]; then
  SDK="$(cygpath -m "$ANDROID_HOME" 2>/dev/null || echo "$ANDROID_HOME")"
elif [ -n "${LOCALAPPDATA:-}" ]; then
  SDK="$(cygpath -m "$LOCALAPPDATA")/Android/Sdk"
else
  echo "错误：未找到 Android SDK，请设置 ANDROID_SDK_ROOT" >&2
  exit 1
fi
BT="$SDK/build-tools/35.0.1"
PLATFORM="$SDK/platforms/android-33/android.jar"
[ -f "$PLATFORM" ] || PLATFORM="$SDK/platforms/android-34/android.jar"
[ -f "$PLATFORM" ] || PLATFORM="$SDK/platforms/android-35/android.jar"

# ---- 定位 JDK ----
if [ -n "${JAVA_HOME:-}" ]; then
  JBR="$(cygpath -m "$JAVA_HOME" 2>/dev/null || echo "$JAVA_HOME")"
elif [ -f "C:/Program Files/Android/Android Studio/jbr/bin/java.exe" ]; then
  JBR="C:/Program Files/Android/Android Studio/jbr"
elif command -v java >/dev/null 2>&1; then
  JBR="$(dirname "$(command -v java)")/.."
else
  echo "错误：未找到 JDK，请设置 JAVA_HOME" >&2
  exit 1
fi

JAVA="$JBR/bin/java.exe"; [ -f "$JAVA" ] || JAVA="$JBR/bin/java"
JAVAC="$JBR/bin/javac.exe"; [ -f "$JAVAC" ] || JAVAC="$JBR/bin/javac"
JARTOOL="$JBR/bin/jar.exe"; [ -f "$JARTOOL" ] || JARTOOL="$JBR/bin/jar"

# ---- 签名密钥 ----
# 默认使用项目本地的 applauncher.jks（首次构建自动生成，密码 applauncher）。
# 如需使用自己的密钥，设置环境变量：
#   export SIGN_KS=/path/to/your.jks SIGN_ALIAS=your_alias SIGN_STORE_PASS=pass:xxx SIGN_KEY_PASS=pass:xxx
mkdir -p "$PROJ/keystore"
KS="${SIGN_KS:-$PROJ/keystore/applauncher.jks}"
KS_ALIAS="${SIGN_ALIAS:-applauncher}"
KS_PASS="${SIGN_STORE_PASS:-pass:applauncher}"
KEY_PASS="${SIGN_KEY_PASS:-$KS_PASS}"
if [ ! -f "$KS" ]; then
  echo "== 生成本地签名密钥 keystore/applauncher.jks =="
  "$JBR/bin/keytool.exe" -genkeypair -keystore "$KS" -alias "$KS_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass applauncher -keypass applauncher \
    -dname "CN=AppLauncher Debug" >/dev/null 2>&1 \
  || "$JBR/bin/keytool" -genkeypair -keystore "$KS" -alias "$KS_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass applauncher -keypass applauncher \
    -dname "CN=AppLauncher Debug" >/dev/null
fi

B="$PROJ/build"
rm -rf "$B"
mkdir -p "$B/gen" "$B/obj" "$PROJ/dist"

echo "== aapt2 编译资源 =="
"$BT/aapt2.exe" compile --dir "$PROJ/app/res" -o "$B/res.zip" \
  || "$BT/aapt2" compile --dir "$PROJ/app/res" -o "$B/res.zip"
"$BT/aapt2.exe" link -o "$B/base.apk" -I "$PLATFORM" \
  --manifest "$PROJ/app/AndroidManifest.xml" \
  --java "$B/gen" \
  --min-sdk-version 17 --target-sdk-version 19 \
  --version-code 1 --version-name 1.0 \
  "$B/res.zip" \
  || "$BT/aapt2" link -o "$B/base.apk" -I "$PLATFORM" \
  --manifest "$PROJ/app/AndroidManifest.xml" \
  --java "$B/gen" \
  --min-sdk-version 17 --target-sdk-version 19 \
  --version-code 1 --version-name 1.0 \
  "$B/res.zip"

echo "== javac =="
SRCS=$(find "$B/gen" "$PROJ/app/src" -name '*.java')
"$JAVAC" -source 8 -target 8 -encoding UTF-8 -Xlint:-options \
  -bootclasspath "$PLATFORM" \
  -d "$B/obj" \
  $SRCS
"$JARTOOL" cf "$B/app.jar" -C "$B/obj" .

echo "== d8 dex =="
"$JAVA" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --release \
  --lib "$PLATFORM" --min-api 17 \
  --output "$B" \
  "$B/app.jar"

python "$PROJ/tools/add_dex.py" "$B/base.apk" "$B/classes.dex"
"$BT/zipalign.exe" -f 4 "$B/base.apk" "$B/aligned.apk" \
  || "$BT/zipalign" -f 4 "$B/base.apk" "$B/aligned.apk"

echo "== 签名（v1+v2）=="
"$JAVA" -cp "$BT/lib/apksigner.jar" com.android.apksigner.ApkSignerTool sign \
  --ks "$KS" --ks-pass "$KS_PASS" --key-pass "$KEY_PASS" \
  --ks-key-alias "$KS_ALIAS" --min-sdk-version 17 \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled false \
  --out "$PROJ/dist/AppLauncher_v1.0.apk" "$B/aligned.apk" \
  || "$JAVA" -cp "$BT/lib/apksigner.jar" com.android.apksigner.ApkSignerTool sign \
  --ks "$KS" --ks-pass "$KS_PASS" --key-pass "$KEY_PASS" \
  --ks-key-alias "$KS_ALIAS" --min-sdk-version 17 \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled false \
  --out "$PROJ/dist/AppLauncher_v1.0.apk" "$B/aligned.apk"

echo "== 完成: dist/AppLauncher_v1.0.apk =="
"$BT/aapt2.exe" dump badging "$PROJ/dist/AppLauncher_v1.0.apk" 2>/dev/null \
  | grep -E "package:|sdkVersion|application-label" || \
  "$BT/aapt2" dump badging "$PROJ/dist/AppLauncher_v1.0.apk" \
  | grep -E "package:|sdkVersion|application-label"
