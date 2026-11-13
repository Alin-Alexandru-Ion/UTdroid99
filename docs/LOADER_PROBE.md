# Android loader probe — 2026-11-02

This debug experiment does not start UT99. Loading arbitrary native libraries executes their constructors: use only the pinned, locally verified research input in an isolated emulator or test device. Never import native libraries from game data or servers. The original Core input and runtime are excluded from the APK and public code tree.

The original fixture in `native/probe_*` has a versioned `probe_versioned_add@PROBE_1.0` import and ARM64 `R_AARCH64_TLSDESC`. It tests a dependency, symbol-version resolution and per-thread TLS, not the GLIBC ABI. The host attempts `dlopen` and reports errors. The Core button explicitly probes `files/Core.so`; if it ever successfully loads, constructors can run, but the engine entry point is not called.

Build on Windows: set `ANDROID_HOME`, `JAVA_HOME`, then `gradlew.bat --offline --no-daemon -PprobeX86=true assembleDebug`. The property adds x86-64 for a development emulator; ordinary `assembleDebug` remains ARM64 only. The installed NDK 23 uses native API 31 and needs replacement before release. Select the target explicitly, e.g. `adb -s emulator-5556 install -r --abi arm64-v8a app/build/outputs/apk/debug/app-debug.apk`, and launch `org.ut99android.host/.MainActivity`. Select **Run loader fixture** and inspect `adb -s emulator-5556 logcat -d -s UT99Host:I *:S`.

To stage the exact official 469e Core for a controlled probe, run `python scripts/extract_core_probe.py`, `adb -s emulator-5556 push research/Core.so /data/local/tmp/ut99-core-probe.so`, launch the host and select **Probe local Core.so** once to create `files/`. Then run `adb -s emulator-5556 shell run-as org.ut99android.host cp /data/local/tmp/ut99-core-probe.so /data/user/0/org.ut99android.host/files/Core.so` and `adb -s emulator-5556 shell run-as org.ut99android.host chmod 400 /data/user/0/org.ut99android.host/files/Core.so`; press the Core button again. This debug app's private copy is read-only for Android's dynamic code policy. Hash-check it with `adb -s emulator-5556 shell run-as org.ut99android.host sha256sum /data/user/0/org.ut99android.host/files/Core.so`. These commands are for the app's debug package and chosen test device only.

## Observed result

Android 15 API 35 `sdk_gphone64_x86_64` emulator, read-only AVD, host x86-64, `--abi arm64-v8a` (ARM translation): host launched and posted a 2400x1080 diagnostic frame; Home and return logged pause and resume. Fixture logged `Fixture results main=18,19 thread=18 PASS`. ARM64 `llvm-readelf` confirmed PT_TLS, `R_AARCH64_TLSDESC`, a `PROBE_1.0` version requirement and a DT_NEEDED edge to the provider. An early fixture attempt used an unavailable extracted library path; loading by soname corrected it.

Pinned Core SHA-256 `f0ef4d6bd3b2bf13c4afb8e0ad0c3ea6be6a63182b9d64a67554136e03c3837d` was copied to private read-only storage. Android loader reported `library "libSDL2-2.0.so.0" not found: needed by .../Core.so`. The load did not succeed, so engine startup was not attempted. The fixture result does not establish that GNU libc, libstdc++, Linux SDL, the engine, or GPU interfaces are compatible. Emulator ARM translation is development evidence only; a physical ARM64 Android test remains outstanding.

## SDL candidate

The official [SDL repository](https://github.com/libsdl-org/SDL), tag `release-2.32.10`, commit `5d249570393f7a37e037abf22cd6012a4cc56a71`, was cloned under ignored `research/`. SDL uses the zlib license; its license must accompany any future distribution. Its Android ARM64 shared library built using the installed NDK 23 and CMake 3.22.1, `ANDROID_PLATFORM=android-31`, `SDL_SHARED=ON`, `SDL_STATIC=OFF`. Reconfigure with `CMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384` to align the output. The candidate is research-only and is not part of the APK.

`python scripts/compare_sdl_exports.py --audit research/audit-469e.json --candidate research/sdl-build/libSDL2.so --readelf <llvm-readelf>` matched 88 of 88 unique `SDL_` symbol names imported across the audited patch ELFs. The Android output's SONAME is `libSDL2.so`, not the `libSDL2-2.0.so.0` requested by Core.so. Name matching is not a guarantee of versions, binary layouts, initialization, runtime behavior, or Android lifecycle integration. The candidate has not been loaded alongside Core. Next, investigate a bounded library-name binding experiment in the isolated emulator, followed by the next actual linker error, without claiming that any later dependencies are satisfied.

The aligned candidate SHA-256 is `8350f20f02aca7d5d9cc764baec4108beb1f859ad875141debbb7619df80a715`. Its four PT_LOAD segments have `0x4000` alignment and its GNU_RELRO end is `0x1e8000`. This is static build evidence, not a 16 KiB device test.

## Follow-up: exact SONAME and libdl bridge

`experiments/sdl-soname/CMakeLists.txt` wraps the pinned unmodified SDL source and builds an Android library with SONAME `libSDL2-2.0.so.0`. Configure with the Android NDK toolchain, `ANDROID_ABI=arm64-v8a`, `ANDROID_PLATFORM=android-31`, `SDL_SOURCE_DIR=<pinned checkout>` and build target `SDL2`. Output SHA-256 `8663d64211f8e3f64d9519afc6593395bc03d7f5eb09a410ad5e56fdad674a6b`; all 88 SDL symbol names remain present. In the translated ARM64 Android 15 emulator this library preloaded successfully, after which Core stopped at missing `libdl.so.2`. SDL initialization, display/audio integration, and engine behavior were not tested.

`experiments/libdl-bridge` builds a separate ARM64 research-only `libdl.so.2` with four GLIBC_2.17 exports: `dlopen`, `dlsym`, `dlclose`, and `dlerror`. Their implementations call Bionic's `LIBC`-versioned functions. Build with the same NDK toolchain/ABI/platform and CMake/Ninja. Output SHA-256 `131e4fb51fdaf72f3527d3f372be9e13582bf2217a05d9cde1e9b62628b6172b`. Static readelf confirmed the intended SONAME and versioned imports and exports. Stage as read-only `files/libdl.so.2` alongside Core and the SDL candidate, using the same `adb push`, `run-as cp`, and `chmod 400` procedure described above. The updated host has distinct **Test dl bridge** and **Probe SDL, dl and Core** controls.

On the same translated emulator, the bridge smoke test resolved its GLIBC-versioned exports, opened Android `libm.so`, resolved `cos`, closed it, and returned an error for a missing library. It logged `Bridge positive and missing-library controls PASS`. With SDL and bridge preloaded, Core failed on the next dependency: `libstdc++.so.6` not found. This only shows Android's loader advanced past two dependency names; Core never loaded and no engine entry point ran. The bridge does not prove Linux-compatible `RTLD_NEXT`, caller-sensitive namespace handling, flags, thread/error semantics, or broad behavior, and is not a production compatibility layer.

Static comparison with NDK 23 API 31 AArch64 `libc.so`, `libm.so` and `libdl.so` stubs: of Core's 129 unique GLIBC-versioned import names, ten are absent by name: `__ctype_b_loc`, `__environ`, `__errno_location`, `__fprintf_chk`, `__printf_chk`, `__wprintf_chk`, `__xstat`, `backtrace`, `backtrace_symbols`, and `malloc_trim`. The supplied NDK's `libc++_shared.so` contains all 21 of Core's GLIBCXX/CXXABI import names. Run `scripts/compare_core_bionic.py` with the audit report, `--readelf`, `--stub-dir <NDK>/sysroot/usr/lib/aarch64-linux-android/31` and `--cxx <NDK>/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so`. Matching names do not establish symbol versions, C++ ABI, exception/unwind behavior or interchangeable GNU and LLVM C++ runtimes; missing names might have another provider. These are static comparisons, not a working `libstdc++` replacement.

PowerShell configure/build commands from the project directory (adjust SDK path as needed):

```powershell
$sdk = $env:ANDROID_HOME
$cmake = "$sdk/cmake/3.22.1/bin/cmake.exe"
$ninja = "$sdk/cmake/3.22.1/bin/ninja.exe"
$toolchain = "$sdk/ndk/23.2.8568313/build/cmake/android.toolchain.cmake"
$sdlSource = (Resolve-Path research/SDL2).Path.Replace('\', '/')
& $cmake -S experiments/sdl-soname -B research/sdl-soname-build -G Ninja "-DCMAKE_MAKE_PROGRAM=$ninja" "-DCMAKE_TOOLCHAIN_FILE=$toolchain" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-31 "-DSDL_SOURCE_DIR=$sdlSource" -DCMAKE_BUILD_TYPE=Release
& $cmake --build research/sdl-soname-build --target SDL2 --parallel 6
& $cmake -S experiments/libdl-bridge -B research/libdl-bridge-build -G Ninja "-DCMAKE_MAKE_PROGRAM=$ninja" "-DCMAKE_TOOLCHAIN_FILE=$toolchain" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-31 -DCMAKE_BUILD_TYPE=Release
& $cmake --build research/libdl-bridge-build
```

On 2026-11-02 the final host debug build repeated the passing bridge controls and the `libstdc++.so.6` failure; the captured local log is `research/loader-followup.log`. Loader buttons are shown only when the host is debuggable. This is an experimental control, not a safe general-purpose module importer.
