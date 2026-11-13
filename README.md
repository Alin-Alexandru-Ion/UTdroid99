# UTdroid99

UTdroid99 is an independent experimental Android compatibility app for the OldUnreal 469e Linux ARM64 version of Unreal Tournament. It offers touchscreen and SDL controller input and online play. It is not affiliated with or endorsed by Epic Games or OldUnreal.

The app does not include Unreal Tournament, its engine, game assets or the OldUnreal patch. You must supply your own extracted game folder and the official 469e Linux ARM64 patch archive. The app imports private copies and leaves the originals untouched. Removing the app deletes its imported copies and settings.

## Build on Windows

Install the prerequisites in [BUILDING.md](docs/BUILDING.md). From PowerShell, run:

```powershell
py -3 scripts/bootstrap_windows.py --support-only --work-dir research/support-build
```

The first build fetches pinned source dependencies and Gradle components. The result is `app/build/outputs/apk/debug/app-debug.apk`. This is an experimental debug build for ARM64 devices; a working SDK, JDK, NDK and network connection are required. See [BUILDING.md](docs/BUILDING.md) for details and limitations.

The app imports the user-supplied patch and runs its engine through native compatibility bridges. Imported engine files remain unchanged on disk; temporary in-memory adaptations and engine-specific hooks are used. It is not a general CPU emulator. The compatibility and release rights questions are described in [DIRECT_REHOST_TEST.md](docs/DIRECT_REHOST_TEST.md) and [RELEASE_PREPARATION.md](docs/RELEASE_PREPARATION.md).

## Credits and licences

Unreal Tournament was created by Epic Games and Digital Extremes. The 469e patch is maintained by OldUnreal. Original UTdroid99 work is by me and licensed under [BSD 3-Clause](LICENSE). Third-party components retain their own licences; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The launcher icon was generated with OpenAI image generation. Development assistance: OpenAI Codex.
