# Evidence and gates

Current update 2026-11-03: the original launcher initialized the engine and rendered changing CityIntro scenes on an ARM-translated, host-GPU emulator. See [CITYINTRO_PROGRESS.md](CITYINTRO_PROGRESS.md). Physical ARM64 execution and full ABI compatibility remain unverified. Entries below the table are historical test results.

| Gate | Status | Evidence / remaining work |
| --- | --- | --- |
| G0 rights and provenance | BLOCKED | Official asset SHA-256 verified; runtime transformation and distribution rights not established. |
| G1 ELF feasibility | PARTIAL | Original modules loaded with provisional shims on translated emulator; full ABI and native hardware unverified. |
| G2 Android native shell | PARTIAL | Diagnostic shell and SDL surface ran in translated emulator; physical device untested. |
| G3 original engine entry | PARTIAL | Original engine initialized and loaded CityIntro under translated emulator; compatible runtime not established. |
| G4 original game frame | PARTIAL | CityIntro video and frames observed on host-GPU emulator; graphics correctness and physical device untested. |
| G5 offline play | PARTIAL | Practice play and controller buttons/axes were observed by the user on a phone; joystick dodge direction and live sensitivity controls remain unfinished. |
| G6 multiplayer | PARTIAL | Emulator listen server bound game/query UDP ports and answered a local status query. Client joining, discovery, public browsing and phone hosting are unverified. |
| G7 release | BLOCKED | Device QA, import, runtime, alignment, rights and release validation outstanding. |

2026-11-02: A translated ARM64 test on an Android 15 x86-64 emulator loaded the original test fixture and passed versioned-symbol and thread-local checks. The real 469e Core.so load failed because `libSDL2-2.0.so.0` was unavailable. Host diagnostic frame and pause/resume were observed in that emulator; physical ARM64 G2 and engine initialization remain untested. Details and reproduction are in [LOADER_PROBE.md](LOADER_PROBE.md).

Follow-up: exact-SONAME Android SDL and a research-only libdl bridge preloaded in the translated emulator. The bridge's basic positive and negative controls passed. With both staged, Core failed at `libstdc++.so.6`; Core did not load and G1/G3 remain unpassed. Broader bridge semantics and physical ARM64 testing remain outstanding. The latest debug APK built successfully and `zipalign -c -P 16 -v 4` passed; SHA-256 `b8134e8de5733a9a94af6d6f5abcbfd9ec7dc2fb9b7e3f72d28a65ae92e706d6`.

After restricting loader controls to debuggable apps, the final rebuilt APK SHA-256 is `35fde7f560e590e0eb2ff1db182ad9f63032c9849983d9458fb57903009fd5cb`; build and `zipalign -c -P 16 -v 4` passed, and the translated emulator repeated the bridge controls and the same Core dependency failure. Release-mode UI behavior is not runtime-tested.

Subsequent boot investigation: translated-emulator controls for anchored-RTTI cross-library exceptions/unwind, limited math, binary128, and thread-local errno passed; duplicate inline RTTI failed both with the experimental C++ candidate and the unmodified NDK reference. The original Core loader advances to missing `libc.so.6` and does not load. Full details and limits: [BOOT_PROGRESS.md](BOOT_PROGRESS.md). G1 and G3-G7 remain unpassed; Android intro has not been rendered.

Latest diagnostic build: `gradlew.bat --offline --no-daemon assembleDebug` succeeded; APK SHA-256 `cdf4a81140a9f277f5e8f66df6fc0425f35c07aea9adcc246a29ced2d503531e`. `zipalign -c -P 16 -v 4` succeeded, and inspection shows only the three ARM64 host/fixture libraries are packaged. The isolated ELF experiments and commercial game binaries are not in the APK. Static APK alignment does not replace a 16 KiB device test.

Threading follow-up: translated ARM64 emulator tests passed limited side-table mutex, condition and synthetic GLIBC_2.17 event controls; see [THREADING_ABI.md](THREADING_ABI.md) for exact coverage and excluded behavior. The newer debug APK SHA-256 is `e97b4c03a07fc0ee10cbeb5a0c81b0c8e142a7086670edabc6bc5df51aa95319`; build and zip alignment passed. Core still fails to load at libc, and no intro has rendered.

C-locale follow-up: the C-locale classification and conversion tables match all 384 entries of each pinned glibc reference table in the translated emulator; locale changes and actual engine integration remain untested. See [CTYPE_ABI.md](CTYPE_ABI.md). Latest debug APK SHA-256 `39d2cc5618f84572817480f9bc93b49ab9d471cf5eaad5a5ca4976f268a0bdb1`; build and zip alignment passed.

Libc follow-up: version-0 stat, limited fortified-format and checked-memory controls passed, as did an isolated combined synthetic fixture for an incomplete libc-shaped candidate. Static Core import inventory reports 65 exact name/version/type candidates and 64 missing; this is not engine compatibility. The real Core loader still stops at `libc.so.6`. See [LIBC_PROGRESS.md](LIBC_PROGRESS.md). Latest diagnostic APK SHA-256 `72195028c5173a2b85a9c61ce9ece629bc9542c04213bd18516f19fbf921d8fd`; build and zip alignment passed. G1 and all original-engine/gameplay gates remain unpassed.

The standard ARM64-only debug build passed on 2026-11-02; SHA-256 `f69628bd1b7106907c5a6d6153911274a65a68599c85ac654ef89bebe82e5af1`. The APK contains only the three ARM64 host/fixture libraries. `zipalign -c -P 16 -v 4` passed; each native library's PT_LOAD alignment and GNU_RELRO end also met the 16 KiB static checks. This APK is a diagnostic build, and these checks do not establish runtime behavior on a 16 KiB device.

Host build: Gradle 8.14.3, Android Gradle Plugin 8.5.2, platform 36, NDK 23.2.8568313 and CMake 3.22.1, Windows, `gradle --no-daemon assembleDebug`: BUILD SUCCESSFUL. First sandbox build failed to initialize Gradle native services; offline retry lacked cached Kotlin dependencies; networked build succeeded. Installed NDK reports it uses native API 31 for the API 34 minimum. `zipalign -c -P 16 -v 4` passed for the initial APK; actual 16 KiB device behavior remains untested. Check both PT_LOAD and GNU_RELRO after every rebuild; see Android's [page-size guidance](https://developer.android.com/guide/practices/page-sizes).

After adding both `max-page-size` and `common-page-size` linker options, `gradlew.bat --offline --no-daemon assembleDebug` passed. The rebuilt host has three PT_LOAD alignments of `0x4000`, and `GNU_RELRO` ends at `0xc000`, a 16 KiB boundary. Build-tools 36 `zipalign -c -P 16 -v 4 app/build/outputs/apk/debug/app-debug.apk` passed. APK SHA-256: `fd1dc778f3cf8853b168a1d4722d2898850a0b7294081490850b1fd1f5bd4519`. These checks concern the host only, not any upstream engine binary, and are not device execution evidence.
