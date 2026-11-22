#!/usr/bin/env bash
# Infinite Studio C03: read-only physical hardware QA recorder.
set -euo pipefail
APP_ID='com.aistudio.infinitestudio.kqvxzm'
SERIAL=''
OUTPUT=''
RUN='no'
while (($#)); do
  case "$1" in
    --serial) SERIAL="${2:-}"; shift 2;;
    --output) OUTPUT="${2:-}"; shift 2;;
    --run) RUN='yes'; shift;;
    *) echo "Unknown option: $1" >&2; exit 2;;
  esac
done
if [[ -z "$SERIAL" || ! "$SERIAL" =~ ^[A-Za-z0-9:._-]+$ ]]; then
  echo 'Usage: android_device_qa.sh --serial EXACT_ADB_SERIAL --output EXISTING_DIR [--run]' >&2
  exit 2
fi
if [[ "$RUN" != 'yes' ]]; then
  echo "DRY RUN: will record read-only device, frame, memory, thermal and battery diagnostics for $SERIAL."
  echo 'No APK installation, file deletion, camera, microphone or paid network access.'
  exit 0
fi
command -v adb >/dev/null || { echo 'adb not installed' >&2; exit 3; }
[[ -n "$OUTPUT" && -d "$OUTPUT" ]] || { echo 'Supply an existing output directory' >&2; exit 3; }
if ! adb -s "$SERIAL" get-state | grep -qx device; then
  echo 'Selected device is not available or not authorized' >&2
  exit 4
fi
if [[ "$(adb devices -l | grep -Fc "$SERIAL")" -lt 1 ]]; then
  echo 'Selected serial not found' >&2
  exit 4
fi
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DEST="$OUTPUT/infinite-studio-device-qa-$STAMP"
mkdir -m 700 "$DEST"
printf '%s\n' 'D01-D09 REQUIRE MANUAL PEN AND UI OBSERVATION.' > "$DEST/READ_ME_FIRST.txt"
printf 'serial=%s\napp=%s\ncollected_utc=%s\n' "$SERIAL" "$APP_ID" "$STAMP" > "$DEST/session.txt"
adb -s "$SERIAL" shell getprop ro.product.model > "$DEST/model.txt"
adb -s "$SERIAL" shell getprop ro.build.version.release > "$DEST/android_version.txt"
adb -s "$SERIAL" shell dumpsys gfxinfo "$APP_ID" framestats > "$DEST/gfxinfo_framestats.txt"
adb -s "$SERIAL" shell dumpsys meminfo "$APP_ID" > "$DEST/meminfo.txt"
adb -s "$SERIAL" shell dumpsys battery > "$DEST/battery.txt"
adb -s "$SERIAL" shell dumpsys thermalservice > "$DEST/thermal.txt"
{
  printf 'case\tstatus\tobserved\tissue\n'
  for number in 1 2 3 4 5 6 7 8 9; do
    printf 'D%02d\tUNTESTED\t\t\n' "$number"
  done
} > "$DEST/manual_test_results.tsv"
printf 'Evidence recorded at %s\n' "$DEST"
