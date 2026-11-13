# C-locale character tables — 2026-11-02

This is a test-only step toward glibc compatibility for the original 469e engine. Core imports `__ctype_b_loc@GLIBC_2.17`; other audited 469e modules also import the case-conversion table accessors. A later provisional experiment loaded Core and Engine, but the game has not started; see [LIBC_PROGRESS.md](LIBC_PROGRESS.md).

`experiments/libc-probe/ctype_probe.c` constructs the little-endian AArch64 glibc-style C-locale classification, lowercase, and uppercase tables and exposes `__ctype_b_loc`, `__ctype_tolower_loc`, and `__ctype_toupper_loc` with `GLIBC_2.17` symbol versions under the separate test-only `libutlibcprobe.so` name. The accessors return thread-local pointer slots. This deliberately supports only the C locale: `setlocale`, per-thread locales, non-C Unicode classification, and runtime locale changes are not implemented. The functions have not been connected to a `libc.so.6` implementation or the original Core.

## Reference and results

The reference is [glibc 2.31 C-ctype.c](https://github.com/bminor/glibc/blob/9ea3686266dca3f004ba874745a4087a89682617/locale/C-ctype.c) at commit `9ea3686266dca3f004ba874745a4087a89682617`. The downloaded source stays under ignored `research/glibc-reference/C-ctype.c`, SHA-256 `7e2a69821d5bb269f80d69804513bcf2a1d146f87be6f18aae09b2db1264b796`. The [ctype definitions](https://github.com/bminor/glibc/blob/9ea3686266dca3f004ba874745a4087a89682617/ctype/ctype.h) describe the classification bits and table accessors. Source material is reference input; no glibc source was copied into this implementation.

`scripts/extract_ctype_reference.py` checks the source hash and extracts the three upstream tables into ignored `research/ctype-reference.bin`: 3840 bytes, SHA-256 `268926265a5e37bb9573e8a0a3c1270d651233ad73a0917c333a78de7d4bd122`. It checks each expected table length. On the Android 15 x86-64 emulator using ARM64 translation, the debug `ctype` probe compared all 384 entries of each table byte-for-byte to that reference, and checked that a second thread receives a different pointer slot. Result: `C locale tables match pinned glibc for 384 entries; thread slot PASS`.

The `errno`, limited mutex, condition, and versioned synthetic-event probes were rerun after adding the tables; each passed its existing controls. The real Core probe still fails at missing `libc.so.6`. These results do not establish glibc locale lifecycle, engine operation, physical ARM64 behavior, or 16 KiB device behavior.

## Reproduce

Place the hash-matching pinned `C-ctype.c` in `research/glibc-reference/`, then from the project root:

```powershell
py -3 scripts/extract_ctype_reference.py
& "$env:LOCALAPPDATA/Android/Sdk/cmake/3.22.1/bin/cmake.exe" --build research/libc-probe-build
.\gradlew.bat --offline --no-daemon assembleDebug
.\scripts\run_android_probe.ps1 -Serial <explicit-serial> -Probe ctype
adb -s <explicit-serial> logcat -d -s UT99Host:I '*:S'
```

Set the SDK, JDK and Gradle environment as documented in `README.md`; configure the libc-probe build first if needed. Current hashes: `libutlibcprobe.so` `b8ee9c0eafa18289aad967af97fa5966e5dd81ce2312c74b22e33e9ed9d6959c`; `app-debug.apk` `39d2cc5618f84572817480f9bc93b49ab9d471cf5eaad5a5ca4976f268a0bdb1`. The APK build and zip alignment passed; the original runtime and experimental external libraries are not bundled.
