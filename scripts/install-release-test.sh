#!/usr/bin/env bash
set -euo pipefail

UNINSTALL_FIRST=0

if [[ "${1:-}" == "--uninstall" ]]; then
  UNINSTALL_FIRST=1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PKG="com.pqnas.mobile"

APK_IN="app/build/outputs/apk/release/app-release-unsigned.apk"
APK_ALIGNED="app/build/outputs/apk/release/app-release-test-aligned.apk"
APK_SIGNED="app/build/outputs/apk/release/app-release-test-signed.apk"

BUILD_TOOLS="$(ls -d "$HOME/Android/Sdk/build-tools/"* | sort -V | tail -n1)"

if [[ ! -x "$BUILD_TOOLS/zipalign" ]]; then
  echo "zipalign not found in: $BUILD_TOOLS"
  exit 1
fi

if [[ ! -x "$BUILD_TOOLS/apksigner" ]]; then
  echo "apksigner not found in: $BUILD_TOOLS"
  exit 1
fi

if [[ ! -f "$HOME/.android/debug.keystore" ]]; then
  echo "Debug keystore not found: $HOME/.android/debug.keystore"
  echo "Run ./gradlew :app:assembleDebug once first, or create a debug keystore."
  exit 1
fi

echo "==> Building minified release APK"
./gradlew :app:assembleRelease

echo "==> Aligning APK"
"$BUILD_TOOLS/zipalign" -p -f 4 "$APK_IN" "$APK_ALIGNED"

echo "==> Signing release APK with local debug key"
"$BUILD_TOOLS/apksigner" sign \
  --ks "$HOME/.android/debug.keystore" \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$APK_SIGNED" \
  "$APK_ALIGNED"

echo "==> Verifying signature"
"$BUILD_TOOLS/apksigner" verify --verbose "$APK_SIGNED"

if [[ "$UNINSTALL_FIRST" == "1" ]]; then
  echo "==> Uninstalling existing app first"
  adb uninstall "$PKG" || true
fi

echo "==> Installing release-test APK"
adb install -r "$APK_SIGNED"

echo
echo "Installed:"
echo "  $APK_SIGNED"
echo
echo "This is a release build signed with your local debug key."
echo "Use it for testing only, not publishing."
