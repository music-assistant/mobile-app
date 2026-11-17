#!/bin/sh
# Xcode Cloud post-clone hook. The official release builds on GitHub Actions, so this
# script runs only for an Xcode Cloud workflow (for example a personal TestFlight).
#
# Workflow environment variables (see iosApp/Configuration/Config.xcconfig):
#   MA_TEAM_ID, MA_BUNDLE_ID, MA_CARPLAY (YES/NO). Signing is always Automatic here,
#   because Xcode Cloud manages the certificates and profiles.
set -eu

REPO="${CI_PRIMARY_REPOSITORY_PATH:-$(cd "$(dirname "$0")/../.." && pwd)}"
LOCAL_XCCONFIG="$REPO/iosApp/Configuration/Local.xcconfig"

# WebRTC.xcframework is git-ignored. Keep the release in step with
# .github/workflows/ios-release.yml and IOS-BUILD-INSTRUCTIONS.md.
WEBRTC_RELEASE=m125-cow-bloat-fix.1
FRAMEWORKS="$REPO/iosApp/Frameworks"
if [ ! -d "$FRAMEWORKS/WebRTC.xcframework/ios-arm64/WebRTC.framework" ]; then
  mkdir -p "$FRAMEWORKS"
  curl -fsSL "https://github.com/teancom/webrtc/releases/download/$WEBRTC_RELEASE/WebRTC.xcframework.$WEBRTC_RELEASE.zip" \
    -o "${TMPDIR:-/tmp}/WebRTC.xcframework.zip"
  unzip -q "${TMPDIR:-/tmp}/WebRTC.xcframework.zip" -d "$FRAMEWORKS"
  rm "${TMPDIR:-/tmp}/WebRTC.xcframework.zip"
  test -d "$FRAMEWORKS/WebRTC.xcframework/ios-arm64/WebRTC.framework"
fi

# The Kotlin build phase runs Gradle, which needs JDK 21. Xcode Cloud has no JDK.
JDK_DIR="$HOME/ma-jdk-21"
if [ ! -x "$JDK_DIR/Contents/Home/bin/java" ]; then
  case "$(uname -m)" in
    arm64) JDK_ARCH=aarch64 ;;
    *) JDK_ARCH=x64 ;;
  esac
  mkdir -p "$JDK_DIR"
  curl -fsSL "https://api.adoptium.net/v3/binary/latest/21/ga/mac/$JDK_ARCH/jdk/hotspot/normal/eclipse" \
    | tar -xz -C "$JDK_DIR" --strip-components 1
fi
"$JDK_DIR/Contents/Home/bin/java" -version

# Build settings reach the Run Script phases as environment variables, so JAVA_HOME
# set here is what the Gradle phase sees.
{
  echo "// Written by ci_scripts/ci_post_clone.sh"
  echo "JAVA_HOME = $JDK_DIR/Contents/Home"
  echo "MA_SIGNING_STYLE = Automatic"
  [ -n "${MA_TEAM_ID:-}" ] && echo "MA_TEAM_ID = $MA_TEAM_ID"
  [ -n "${MA_BUNDLE_ID:-}" ] && echo "MA_BUNDLE_ID = $MA_BUNDLE_ID"
  [ -n "${MA_CARPLAY:-}" ] && echo "MA_CARPLAY = $MA_CARPLAY"
  true
} > "$LOCAL_XCCONFIG"
cat "$LOCAL_XCCONFIG"

# TestFlight needs a new build number for each upload.
if [ -n "${CI_BUILD_NUMBER:-}" ]; then
  /usr/libexec/PlistBuddy -c "Set :CFBundleVersion $CI_BUILD_NUMBER" "$REPO/iosApp/iosApp/Info.plist"
fi
