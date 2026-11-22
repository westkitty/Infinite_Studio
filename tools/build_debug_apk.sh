#!/usr/bin/env bash
# Build on a host with an installed Android SDK and compatible Gradle; no network API inference.
set -euo pipefail
OUT=''
if [[ $# -eq 2 && "$1" == '--output' ]]; then OUT="$2"; else
  echo 'Usage: bash tools/build_debug_apk.sh --output EXISTING_DIRECTORY' >&2; exit 2
fi
[[ -d "$OUT" ]] || { echo 'Output directory must already exist' >&2; exit 2; }
command -v gradle >/dev/null || { echo 'BLOCKED: Gradle executable missing' >&2; exit 3; }
command -v keytool >/dev/null || { echo 'BLOCKED: Java keytool missing' >&2; exit 3; }
if [[ -z "${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}" ]]; then
  echo 'BLOCKED: Android SDK root not configured' >&2; exit 3
fi
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if ! git rev-parse --show-toplevel >/dev/null; then
  echo 'BLOCKED: Run from a real Git repository after applying source changes' >&2; exit 4
fi
if [[ ! -f debug.keystore ]]; then
  echo 'Creating local throwaway DEBUG signing key. This must never sign a release.'
  umask 077
  keytool -genkeypair -keystore debug.keystore -storepass android -alias androiddebugkey \
    -keypass android -keyalg RSA -keysize 2048 -validity 3650 \
    -dname 'CN=Android Debug,O=Infinite Studio,C=US' -noprompt
fi
python3 -m unittest discover -s companion -p 'test_*.py' -v
gradle :app:testDebugUnitTest assembleDebug --no-daemon
APK='app/build/outputs/apk/debug/app-debug.apk'
[[ -s "$APK" ]] || { echo 'BLOCKED: No debug APK produced' >&2; exit 5; }
HASH="$(shasum -a 256 "$APK" | cut -d ' ' -f 1)"
DEST="$OUT/Infinite_Studio_C01_C05_debug_${HASH:0:12}.apk"
if [[ -e "$DEST" ]]; then
  cmp -s "$APK" "$DEST" || { echo 'BLOCKED: Existing output hash name differs' >&2; exit 6; }
else
  cp "$APK" "$DEST"
fi
printf 'BUILD OUTPUT: %s\nSHA-256: %s\n' "$DEST" "$HASH"
printf '%s\n' 'Hardware S-Pen tests D01-D09 remain unrun until a physical tablet is used.'
