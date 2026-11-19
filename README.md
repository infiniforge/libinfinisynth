# libinfinisynth

Version **0.1.0**. Reusable offline sound generation for InfiniForge applications.

- `synth-core`: immutable float PCM, DAHDSR envelopes, 1–8 operator FM with an
  arbitrary modulation matrix, sample pitch/loop rendering and bounded SF2 sample
  extraction (mono, linked stereo, 16/24-bit pools).
- `synth-android`: dynamically linked Nuked-OPL3, chips m6581 SID 6581, and dual SAA1099 CMS emulation.
  Public register programs expose complete chip control; editable patch builders
  provide operator/voice envelopes, waveforms, feedback, routing and filters.

Apps own audio devices, mixing/editing workflows, files, permissions and projects.
No sound bank is bundled. SF2 extraction reads original sample headers/pools;
it is not a full SoundFont preset/modulator or MIDI sequencer implementation.
SID is the upstream 6581 model, without a claim of exact analog silicon behavior.

Standalone build: JDK 17/21, Android SDK 36/build-tools 35, AGP 8.9.2, pinned Gradle
9.7.1. Run `./build.sh --max-workers=1 --console=plain`. Termux uses installed
clang/aapt2 and builds arm64-v8a; normal Android builds use NDK 27.2.12479018 and
CMake 3.22.1. No sibling checkout is needed. Native binaries use 16 KiB ELF alignment.

`./gradlew :synth-core:test -PsynthCoreOnly` runs JVM and host JNI tests on Linux/Termux and macOS (clang required).
The host JNI build is automatic; Android builds package their own target binary.
Coordinates: `io.github.gretchycat.libinfinisynth:synth-core:0.1.0` and
`synth-android:0.1.0`. Maven publishing is not set up; use pinned source composites.

[Public API](docs/API.md) · [Third-party sources/licenses](docs/THIRD_PARTY.md) ·
[MIT license for original code](LICENSE.md)
