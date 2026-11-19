#!/usr/bin/env sh
set -eu
cd -- "$(dirname -- "$0")"
SYNTH_SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}
set -- "-Dandroid.home=$SYNTH_SDK" "$@"
if [ -n "${TERMUX_VERSION:-}" ]; then set -- "-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)" "$@"; fi
./gradlew :synth-core:test :synth-android:assembleDebug :synth-android:lintDebug "$@"
