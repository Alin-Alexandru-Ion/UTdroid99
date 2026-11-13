# Linux runtime integration progress

Research date: 2026-11-12. The goal is one self-contained Android app: players must not need to install, launch, configure or interact with Termux. This is an isolated prototype, not a playable release.

Subsequent direct Android rehosting work, including an emulator practice-match test and a private phone-test APK, is documented in [DIRECT_REHOST_TEST.md](DIRECT_REHOST_TEST.md). That test does not use the Linux userspace or virtual-display design described below.

## Unchanged patch import

The experimental `-Put99LinuxRuntimeProbe` build uses a separate application ID (`org.ut99android.host.linuxprobe`), and imports the user-selected pinned 469e archive without calling the ELF transformer. It stores the original launcher under its original name and copies original modules, three audio support libraries, and the three bundled font files. The usual game patch data is imported separately. Existing archive hash, bounds, duplicate and path checks still apply. The play button and the old Android runtime installer are disabled in this variant: the Linux launcher is not integrated yet.

Desktop verification compared all 22 selected ARM64 files byte for byte with their original entries in the supplied archive and checked the source archive hash remained unchanged. A separate Android 15 x86_64 emulator test imported the archive through the document picker and independently compared the SHA-256 of all 22 imported files with the archive; all matched and the selected archive copy remained unchanged. This validates extraction, not execution, graphics or physical-device behavior. The existing transformed-runtime import also passed its regression test. This mode is separate from the tested existing transformed-runtime path.

## Android process experiment

The API-36-targeted standalone diagnostic in `experiments/android-exec-probe` ran on an Android 15 x86_64 emulator. The official Termux app used in the earlier proof of concept targets API 28, so its successful PRoot launch does not establish behavior for UTdroid99's API-36 target.

An APK-packaged native executable ran, while an identical executable copied to writable private app storage failed with EACCES. The packaged Debian 12 x86_64 glibc loader started but died with SIGSYS when used to load a read-only Linux guest from app data. A tracing diagnostic identified blocked x86_64 calls: `access`, `set_robust_list`, and `rseq`.

The diagnostic translated `access` to `faccessat`, returned a fixture-only success for `set_robust_list`, and returned ENOSYS for `rseq`. Under that bounded test, the unchanged Debian glibc executable printed its version and exited successfully. This proves only one simple single-process x86_64 fixture under emulation, not that UT99, ARM64, child processes, threading, graphics or audio will work. In particular, faking robust-futex registration is not safe to adopt as a general game solution. The tracer must not be shipped as a production runtime. `ptrace` is used on the diagnostic's own child process; no other app is traced.

The downloaded Debian libc package and generated APKs are in ignored private `research/`; they are not release artifacts. Redistribution would require a separate component and source/license review. The x86 probe uses a local throwaway signing key, not the app's release identity.

## Self-contained runtime and display experiments

The later API-36-targeted execution probe bundles its own PRoot, loader and QEMU, along with their Android-side dependencies. It ran an unchanged ARM64 UT launcher and displayed a DeathMatch practice map through Xvfb and x11vnc, using Linux software rendering on the x86_64 emulator. The game, display server and runtime ran in the test app's sandbox; a separate debug activity displayed the framebuffer. Touch in a separate earlier test opened the practice-session dialog, and a direct practice-map launch reached the match. These are separate observations, not proof that starting and playing a match end to end works by touch. Audio was disabled for the test. The original engine hashes matched after the earlier run.

The Linux root filesystem and game were staged manually for this laboratory test. The runtime is not yet integrated into the real import and Play flow. Its unauthenticated local TCP display and X server settings are test-only and must be replaced with a private authenticated connection before integration. The emulator used llvmpipe; it did not demonstrate Android GPU acceleration. Its slowness also does not establish performance on a physical ARM64 phone.

An additional ARM64-only diagnostic packages Android ARM64 PRoot and dependencies inside its own APK. The downloaded binary's hardcoded library paths were adjusted in the experimental PRoot copy, not in any UT engine file. On the x86_64 emulator, Android's ARM translation loaded the dependencies but the guest `uname` test timed out; terminating the probe produced an emulator translation-layer error. This is not a physical ARM64 result. The APK is only a runtime diagnostic and cannot run a game.

The PRoot binaries used in these tests originate from packages maintained by the Termux project, but the intended design embeds the required components and has no dependency on the Termux app. Their provenance does not grant redistribution rights: review each component's license, corresponding source and relinking requirements before publishing.

## Remaining work

1. Integrate a private Linux runtime into UTdroid99, validate lifecycle and imports, and test unchanged launcher and required libraries on an actual ARM64 Android device. The x86 fixture's syscall shortcuts are not a production ARM64 implementation.
2. Replace the test display transport with a secured app-private bridge and investigate actual Android GPU acceleration; software llvmpipe has only demonstrated functionality.
3. Bridge Linux audio to Android and connect Android physical gamepads and touch controls to Linux SDL2; test starting and playing a practice session end to end. Existing SDLActivity JNI controls cannot simply control a separate Linux process.
4. Check runtime component licenses, source and replacement requirements, build reproducibility, imported file integrity and Android lifecycle/storage behavior. Preserve the existing working path until this new path is usable.
