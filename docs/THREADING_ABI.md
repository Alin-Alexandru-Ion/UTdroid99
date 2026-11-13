# Threading compatibility experiment — 2026-11-02

This is test-only work toward loading the original 469e engine. The original `Core.so` is still blocked at `libc.so.6`; no engine initialization or CityIntro rendering has occurred. The adapters below are not a complete GNU libc or pthread implementation and are not packaged in the app.

## Evidence from the actual core

Read-only `llvm-objdump -d --demangle` of pinned `Core.so`, SHA-256 `f0ef4d6bd3b2bf13c4afb8e0ad0c3ea6be6a63182b9d64a67554136e03c3837d`, shows:

- At `FEventUnix::Create(int)` (`0xf7698`), the code passes `this+56` and a null attribute to `pthread_mutex_init`, and `this+8` and a null attribute to `pthread_cond_init`.
- `FEventUnix::WaitForEventHelper` calls `pthread_cond_wait` and `pthread_cond_timedwait` with pointers at those offsets. `FEventUnix::Trigger` contains direct calls to mutex unlock and condition signal/broadcast. These are static call-site observations; reachability, ownership and runtime interleavings are not established.
- `FCriticalSectionUnix::FCriticalSectionUnix()` (`0xf7938`) passes `this+8` and a null attribute to `pthread_mutex_init`.

`scripts/inspect_core_threading.py --core research/Core.so --objdump <llvm-objdump> --output research/core-threading.json` reproduces the selected disassembly and direct pthread call contexts. Indirect calls and behavior are not covered.

## Layout and restricted adapter

The [glibc 2.31 AArch64 pthread sizes](https://github.com/bminor/glibc/blob/9ea3686266dca3f004ba874745a4087a89682617/sysdeps/aarch64/nptl/bits/pthreadtypes-arch.h) specify a 48-byte LP64 mutex and 48-byte condition variable. The [generic glibc mutex definition](https://github.com/bminor/glibc/blob/9ea3686266dca3f004ba874745a4087a89682617/sysdeps/nptl/bits/struct_mutex.h) places the mutex kind after four 32-bit fields; the architecture-specific override at the corresponding AArch64 path was not found in the pinned source. The installed Android NDK's LP64 `pthread_mutex_t` is 40 bytes and `pthread_cond_t` is 48 bytes, with a different mutex state representation. The [Bionic implementation](https://android.googlesource.com/platform/bionic/+/4b1c6e73850d2e77c6d05a2f7dad8e3e8c928103/libc/bionic/pthread_mutex.cpp) documents its own state encoding. The upstream reference files remain in ignored `research/glibc-reference/` and were not copied into the implementation.

In `experiments/libc-probe/mutex_probe.c`, a side table maps a glibc mutex or condition-object address to a separately allocated Android object. The prototype accepts null initialization attributes and private normal, recursive, and error-check mutex kinds; condition waits are limited to normal mutexes and default-clock, process-private conditions. Unsupported non-null attributes, unsupported mutex kinds, nonzero lazy condition initializers, and recursive condition waits return `ENOTSUP`. This is a bounded experiment: robust or process-shared objects, real glibc attribute formats, nondefault clocks, cancellation, fork behavior, ownership misuse, thread exit, object inspection by engine code, and full glibc destruction/error semantics are not implemented or validated. Never forward the opaque glibc bytes directly to Bionic or treat this prototype as production-ready.

On the Android 15 x86-64 emulator with ARM64 translation, a 48-byte buffer containing a glibc-style recursive kind at offset 16 was interpreted by direct Bionic calls as nonrecursive: the first lock succeeded and a second `trylock` returned `EBUSY` (16). The side-table mutex controls passed a recursive lock, busy destruction, unsupported-input rejection and 2,000 protected increments across two threads. This validates only those specific cases, not the complete glibc ABI.

The condition test used one 120-byte event-shaped buffer, with condition at +8, mutex at +56, and sentinel bytes at +104..119. It passed default initialization, signal/wait, timed wait and retained lock, two-worker broadcast, busy destruction, unsupported-input rejection, and sentinel checks. The wait/timeout/broadcast controls passed three consecutive fresh-process runs. No physical ARM64 or 16 KiB device run has occurred.

## Versioned synthetic event

`libutpthreadprobe.so` exposes research-only GLIBC_2.17 pthread names that call the restricted side-table functions. `libuteventfixture.so` imports those versioned symbols and exercises null-attribute initialization, a 30ms timed wait, unlock, destruction and sentinel checks on the same event layout. `llvm-readelf --dyn-syms -dW research/libc-probe-build/libuteventfixture.so` confirms seven `pthread_*@GLIBC_2.17` imports and `DT_NEEDED` on the test shim. The fixture passed four translated-emulator runs, including three consecutive fresh-process runs. It is a synthetic module: it neither contains nor loads the UT core, and its result does not prove the engine's synchronization behavior.

## Reproduction and artifacts

Configure the libc probe using the NDK/CMake commands in `BOOT_PROGRESS.md`, then run `cmake --build research/libc-probe-build` and `gradlew.bat --offline --no-daemon assembleDebug`. Run `scripts/run_android_probe.ps1 -Serial <serial> -Probe mutex`, `condition`, or `event`; inspect `adb -s <serial> logcat -d -s UT99Host:I '*:S'`. The saved private log is `research/threading-probe.log`.

Build output SHA-256: `libutlibcprobe.so` `37bc1c60fa895bd94f91b706d50e36b751ec24573e8cf79f18e1f7c38dc2346a`; `libutpthreadprobe.so` `aaa0b6d2ab75d954c0122e64dedcfbbda2e1f30ec7a85557807240361fc56be7`; `libuteventfixture.so` `3ebd64286f3f33f5ef4e735fc9f74c4c746fc365fbff8e0befe0156064f11b4b`. The debug APK SHA-256 is `e97b4c03a07fc0ee10cbeb5a0c81b0c8e142a7086670edabc6bc5df51aa95319`; its ARM64-only build and `zipalign -c -P 16 -v 4` passed. Static alignment does not prove 16 KiB device operation.

After the C-locale additions described in [CTYPE_ABI.md](CTYPE_ABI.md), the newer `libutlibcprobe.so` hash is `b8ee9c0eafa18289aad967af97fa5966e5dd81ce2312c74b22e33e9ed9d6959c`; the latest debug APK hash is `39d2cc5618f84572817480f9bc93b49ab9d471cf5eaad5a5ca4976f268a0bdb1`. Mutex, condition and versioned event controls were rerun successfully with this build.
