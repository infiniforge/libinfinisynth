# libinfinisynth development

Read README.md, docs/API.md, docs/THIRD_PARTY.md and docs/ECOSYSTEM.md before changes.
Keep synth-core independent of Android, apps and storage providers. Voices/patches
and register programs are immutable; hosts own PCM output, editors, scheduling,
permissions and projects. Validate numeric parameters, ranges and resource budgets.
Never silently replace a missing chip backend or invalid SoundFont with a tone.
Retain upstream licenses and pinned source provenance for native chip cores.
Run ./build.sh for tests, native host regression checks, Android AAR and lint.
Use -PsynthCoreOnly for JVM-only Gradle tasks; native tests require a built JNI core.
