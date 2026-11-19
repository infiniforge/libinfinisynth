# Third-party chip cores

- Nuked-OPL3, Nuke.YKT, LGPL-2.1-or-later. Unmodified `opl3.c` / `opl3.h` from
  https://github.com/nukeykt/Nuked-OPL3 at 765ec962e473aeb767e4cba74ffdc8f588ffbfe8.
  Full license: `synth-android/src/main/cpp/third_party/Nuked-OPL3-LICENSE`.
- chips m6581, Andre Weissflog (based on tedplay by Attila Grosz), zlib/libpng
  license, retained in the unmodified header. Source:
  https://github.com/floooh/chips at 7ef8776f637a6424ac615da64de6b968b06de791.

- SAA1099 core by Juergen Buchmueller and Manuel Abadia, BSD-3-Clause,
  from libvgm https://github.com/ValleyBell/libvgm at
  c5c03a2c2514d38d2ab7b7e8dbd021af0b8292cb,
  emu/cores/saa1099_mame.c. Full license: third_party/SAA1099-LICENSE
  under the native source directory. This standalone adaptation retains the
  sound-generation/register algorithm and removes libvgm device registration,
  logging and mute APIs; it uses standard integer types and a small CMS adapter.
  No libvgm framework/header/logging implementation is included. Two independent
  instances provide the Creative Music System/Game Blaster configuration.

The original library code is MIT. The LGPL core is dynamically linked as part of
`libinfinisynth.so`; consumers must retain these notices and permit replacement /
relinking of the library. Complete source, JNI wrapper and build instructions
are provided in this repository. No sound bank or copyrighted instrument samples
are bundled. SID is the upstream 6581 model; no claim of exact analog behavior,
8580 support or bit-perfect chip recordings is made. OPL3 includes the upstream
register, envelope, routing and rhythm implementation. App presets are examples;
the public API accepts all chip registers and timestamped writes.
