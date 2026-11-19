# Generation API

All classes are in `io.github.gretchycat.infinisynth`. Call renderers on a worker.

`AudioBuffer(rate, channels, interleavedFloats)` copies finite float PCM; getters
return copies and `channel(index)` extracts an independent mono buffer. Buffers
preserve over-range float levels. The host chooses clipping/normalization policy.

`Envelope(delay, attack, hold, decay, sustain, release, curve)` uses seconds, sustain
0..1, curve 0.1..10. Note-off releases from the current envelope level, including
during attack. Zero-length stages are legal. FM operators each have an envelope.

`FmSynth.Patch` owns 1..8 `Operator`s, a square modulation matrix, carrier output
weights, gain, pan and deterministic noise seed. Matrix `[destination][source]`
is phase-modulation depth in radians; feedback/cycles use the previous sample,
so evaluation is independent of operator order. Operators expose ratio, fixed Hz,
detune cents, level, initial phase, waveform and pulse width. Oscillators are
simple sampled oscillators, without a claim of bandlimited waveforms or DX-series
bit compatibility. `render(patch, baseHz, noteSeconds, rate)` includes release tails.

`SoundFontBank.read(InputStream)` borrows input and parses at most 64 MiB. `samples()`
exposes names, root keys, fine tuning and loop headers; `extract(index)` copies
original sample PCM, pairing linked stereo. 16-bit `smpl` and optional low 8-bit
`sm24` data are supported. SF3/compressed/ROM samples and malformed ranges fail.
Preset bags, key/velocity zones, modulators and external sample files are not
interpreted; no substitute instruments are generated. `SampleSynth.Patch` controls
semitone shift, gain, pan, explicit loop points, looping and a DAHDSR envelope.
Linear interpolation resamples samples to the requested rate.

`ChipSynth.Program` accepts a chip, output rate/frame count, PAL/NTSC SID clock
and ordered `Write(frame, register, value)` events. Equal-frame ordering is
preserved. OPL3 registers 0..511 / SID registers 0..24 / CMS registers 0..63 are available.
CMS 0..31 address chip 1, 32..63 chip 2; 64..65 clock the externally clocked
envelopes of chip 1/2 without rewriting/resetting their parameters (value ignored). JNI executes
actual Nuked-OPL3 / m6581 / SAA1099 emulation, with key-on/off and register envelopes rather
than approximating them with a sine generator. `ChipPatches` translates editable
2/4-operator OPL, 1..3-voice SID, or 12-voice dual SAA1099 CMS patches into register programs.
CmsVoice exposes frequency/octave registers, left/right 4-bit amplitudes and
tone/noise enables. CmsPatch contains 12 voices, four noise rate selectors,
four CmsEnvelope values, chip clock (default by host: 8 MHz) and two optional
shared chip external envelope clock Hz values (0..20 kHz, default held). CmsEnvelope exposes
enable, all eight native modes, 3-bit resolution, clock source and right inversion.
Timestamped clock events count against the program event budget. CMS mixes both
stereo chips at half gain. Hosts can additionally apply a DAHDSR output envelope. Advanced hosts
can use raw events for multiple OPL channels, rhythm, chip routing and automation.
FM/SF envelopes use seconds; chip envelopes use their native 0..15 rates/levels.

Rates are 8..192 kHz; buffers/render output allow at most 16 million float samples
and 32 channels. FM/SF note length is at most 30 seconds plus envelope tail; chip
programs are at most 30 seconds, 200,000 writes. Parameter errors are explicit;
renderers observe thread interruption and throw InterruptedIOException. Native
chip code loads only on rendering; a missing backend raises a linkage error,
never an unrelated sound. Each render owns its chip state. SID entry points are
serialized because the upstream model initializes shared cutoff tables.
