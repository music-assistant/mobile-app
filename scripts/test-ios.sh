#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

RUN_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ios-tests.XXXXXX")"
SIMCTL_JSON="$RUN_DIR/simctl.json"
RESULT_BUNDLE="$RUN_DIR/iosAppTests.xcresult"
SUMMARY_JSON="$RUN_DIR/iosAppTests-summary.json"
cleanup() {
  rm -f "$SIMCTL_JSON" "$SUMMARY_JSON"
  printf 'XCTest result bundle preserved at: %s\n' "$RESULT_BUNDLE"
  printf 'XCTest run directory preserved at: %s\n' "$RUN_DIR"
}
trap cleanup EXIT

xcrun simctl list devices available -j > "$SIMCTL_JSON"
DEVICE_ID="$(python3 - "$SIMCTL_JSON" <<'PY'
import json
import platform
import re
import sys

if platform.machine() != "arm64":
    raise SystemExit("An arm64 macOS host is required for an arm64 simulator")
with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
iphone = []
for runtime, entries in payload.get("devices", {}).items():
    if "iOS" not in runtime or "Simulator" not in runtime:
        continue
    runtime_version = tuple(int(part) for part in re.findall(r"\d+", runtime))
    for device in entries:
        if device.get("isAvailable") and device.get("name", "").startswith("iPhone"):
            udid = device.get("udid")
            if udid:
                iphone.append((runtime_version, runtime, device.get("name", ""), udid))
if not iphone:
    raise SystemExit("No available iPhone simulator for arm64 host/runtime")
iphone.sort(reverse=True)
print(iphone[0][3])
PY
)"
test -n "$DEVICE_ID"

xcodebuild test \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -destination "platform=iOS Simulator,id=$DEVICE_ID" \
  -resultBundlePath "$RESULT_BUNDLE" \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=''
xcrun xcresulttool get test-results summary --path "$RESULT_BUNDLE" --compact > "$SUMMARY_JSON"
python3 - "$SUMMARY_JSON" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    summary = json.load(handle)
assert summary.get("result") == "Passed", summary
assert int(summary.get("totalTestCount", 0)) > 0, summary
assert int(summary.get("failedTests", 0)) == 0, summary
PY
