# Unmodified ARM64 Linux runtime experiment

Test date: 2026-11-12. Experimental investigation, not a new app build.

## Setup

- Disposable read-only Android 15 x86_64 emulator, separate from the working UTdroid99 app. The experiment used the official Termux GitHub 0.118.3 x86_64 APK, verified against its release SHA-256, plus Termux PRoot 5.1.107.102, proot-distro 5.10.3 and QEMU user-mode 11.1.3.
- PRoot Debian bookworm ARM64 userland with glibc, SDL2 2.26.5, Mesa, Xvfb and OpenAL packages. QEMU was required because this emulator is x86_64; an ARM64 phone may not need CPU emulation, but this has not been demonstrated.
- Game assets came from a private copy of the user's existing folder, plus matching v469e patch game packages. Original launcher and ARM64 modules came directly from the separately supplied official archive (archive SHA-256 `4c3978073b12b049c3ffdeb4d275cfc7a2313650f3eb5b94db06fbfee77c3e3b`).
- The launcher was run as a Linux executable in Debian, not dlopened as an Android library. None of UTdroid99's launcher/module file transformations or runtime hooks were used. The Linux loader and QEMU perform their normal runtime loading, relocation and execution; this is not a claim about unchanged memory.

## Observed results

1. The ARM64 Debian `uname` ran inside the emulator. The original launcher initially reported a missing SDL2 library.
2. After installing SDL2 and other Linux dependencies, the game initialized the engine, loaded Entry and CityIntro, initialized SDL input and created a viewport. A deliberately headless SDL dummy driver could not create the required OpenGL window.
3. With Xvfb, the game created an X11 window and initialized an OpenGL 4.5 core context through Mesa llvmpipe software rendering. Its log reported `Game engine initialized`, `Unreal Tournament (Running)` and entry to the main loop. A 35-second framebuffer capture was black; a later capture at 70 seconds showed a very dark rendered scene from the intro map. One frame does not prove a smoothly playing cinematic or playable gameplay. Cluster audio failed to initialize; sound was not verified.
4. The launcher SHA-256 was `726b4fbeea173ce62e02a81f76638e939389e1be8b40fea88f0ac83b4d21e390` before and after the headless attempt. After the graphical runs, 19 launcher/module library files were checked against the original archive and all matched. INI files were not included in this binary comparison: game startup writes configuration.
5. The copied game settings referenced a missing player package and the engine fell back to a default pawn. Do not interpret that warning as an engine or Linux compatibility failure. The timeout was used to end the graphical test; exit 124 was induced, not a game crash.

The local scripts, hashes, logs and framebuffer capture are private research artifacts under `research/linux-runtime-test`; they are not a distributable source or asset bundle.

## Integration work, not yet implemented

- Prove this route on a physical ARM64 Android device, ideally without QEMU, and determine which Android versions and app execution restrictions are relevant. A Termux proof of concept is not proof that a modern standalone app can launch identical downloaded executables from its private storage. Android 10+ restricts direct `execve` from writable app data for apps targeting API 29+: https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission
- Replace Xvfb with an Android display bridge and investigate actual hardware graphics, frame output, fullscreen and lifecycle. Xvfb/llvmpipe in the emulator is a diagnostic, not an acceptable performance result.
- Provide Linux-to-Android audio and controller/touch transport without UT engine file patches. Test actual sound, game controls and online play. Existing Android SDL hooks in the working build are not automatically available to a separate Linux process.
- Select and pin a suitable runtime and dependencies; PRoot/QEMU and glibc have their own license and distribution requirements. This test does not establish those obligations have been met for an APK.
- Verify all engine files remain identical before and after launch, capture all loading failures, and test importing game data and app upgrades. No claim of legal clearance follows merely from avoiding edits to engine files.

Keep the current working application separate until a replacement passes visible intro, menu, gameplay, audio and physical controller tests on an ARM64 phone.
