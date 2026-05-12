#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ENV_FILE="${DNA_NEXUS_RELEASE_ENV:-$HOME/.android/dna-nexus-release/release-signing.env}"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

: "${DNA_NEXUS_KEYSTORE:?missing DNA_NEXUS_KEYSTORE}"
: "${DNA_NEXUS_KEY_ALIAS:?missing DNA_NEXUS_KEY_ALIAS}"
: "${DNA_NEXUS_KEYSTORE_PASSWORD:?missing DNA_NEXUS_KEYSTORE_PASSWORD}"
: "${DNA_NEXUS_KEY_PASSWORD:?missing DNA_NEXUS_KEY_PASSWORD}"

VERSION_NAME="$(
  grep -E 'versionName\s*=' app/build.gradle.kts \
    | head -1 \
    | sed -E 's/.*"([^"]+)".*/\1/'
)"

VERSION_CODE="$(
  grep -E 'versionCode\s*=' app/build.gradle.kts \
    | head -1 \
    | sed -E 's/.*=\s*([0-9]+).*/\1/'
)"

OUT_DIR="app/build/outputs/apk/release"
APK_IN="$OUT_DIR/app-release-unsigned.apk"
APK_ALIGNED="$OUT_DIR/dna-nexus-mobile-v${VERSION_NAME}-${VERSION_CODE}-aligned.apk"
APK_SIGNED="$OUT_DIR/dna-nexus-mobile-v${VERSION_NAME}-${VERSION_CODE}-official.apk"
SHA_FILE="$APK_SIGNED.sha256"

BUILD_TOOLS="$(ls -d "$HOME/Android/Sdk/build-tools/"* | sort -V | tail -n1)"

if [[ ! -x "$BUILD_TOOLS/zipalign" ]]; then
  echo "zipalign not found in: $BUILD_TOOLS"
  exit 1
fi

if [[ ! -x "$BUILD_TOOLS/apksigner" ]]; then
  echo "apksigner not found in: $BUILD_TOOLS"
  exit 1
fi

echo "==> Building release APK v${VERSION_NAME} (${VERSION_CODE})"
./gradlew :app:clean :app:assembleRelease

echo "==> Aligning APK"
"$BUILD_TOOLS/zipalign" -p -f 4 "$APK_IN" "$APK_ALIGNED"

echo "==> Signing official APK"
"$BUILD_TOOLS/apksigner" sign \
  --ks "$DNA_NEXUS_KEYSTORE" \
  --ks-key-alias "$DNA_NEXUS_KEY_ALIAS" \
  --ks-pass env:DNA_NEXUS_KEYSTORE_PASSWORD \
  --key-pass env:DNA_NEXUS_KEY_PASSWORD \
  --out "$APK_SIGNED" \
  "$APK_ALIGNED"

echo "==> Verifying signature"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$APK_SIGNED"

echo "==> SHA256"
sha256sum "$APK_SIGNED" | tee "$SHA_FILE"

echo
echo "Official release APK:"
echo "  $APK_SIGNED"
echo
echo "Checksum:"
echo "  $SHA_FILE"
