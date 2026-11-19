#!/usr/bin/env sh
set -eu
SYNTH_OUTPUT=$1
SYNTH_JDK=$2
SYNTH_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
mkdir -p "$SYNTH_OUTPUT"
case $(uname -s) in
  Darwin) SYNTH_JNI=darwin; SYNTH_LINK=-dynamiclib; SYNTH_ALIGN=; SYNTH_EXT=dylib ;;
  *) SYNTH_JNI=linux; SYNTH_LINK=-shared; SYNTH_ALIGN=-Wl,-z,max-page-size=16384; SYNTH_EXT=so ;;
esac
clang "$SYNTH_LINK" -fPIC -O2 -std=c11 $SYNTH_ALIGN \
  -I"$SYNTH_JDK/include" -I"$SYNTH_JDK/include/$SYNTH_JNI" \
  "$SYNTH_ROOT/synth-android/src/main/cpp/chips_jni.c" \
  "$SYNTH_ROOT/synth-android/src/main/cpp/third_party/opl3.c" \
  "$SYNTH_ROOT/synth-android/src/main/cpp/cms.c" \
  -lm -lpthread -o "$SYNTH_OUTPUT/libinfinisynth.$SYNTH_EXT"
