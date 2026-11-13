# Build the experimental debug APK on Windows

## Engine-free import build

To build the experimental APK that asks the player for the patch and game
folder on the phone, install the tools listed below and run:

```powershell
python scripts/bootstrap_windows.py --support-only --work-dir research/support-build
```

This builds only support libraries and the Android host. It does not need or
package the OldUnreal patch or game files. The output is the debug APK at
`app/build/outputs/apk/debug/app-debug.apk`, with application ID
`org.ut99android.host.directprobe`; it installs alongside the existing local
build. On the phone, select the official 469e Linux ARM64 `.tar.bz2` patch,
then select your UT99 folder. The app copies and prepares those files privately.
This is experimental: see `USER_SUPPLIED_PATCH_OPTIONS.md` for test limits.
On 2026-11-12 this command completed from a fresh source and support workspace
using local Git mirrors and the installed SDK/JDK; no patch or game files were
provided to the build. The resulting APK passed a 16 KiB zip-alignment check.
Remote dependency downloads had been exercised in a previous clean build, but
this support-only build used local mirrors and a Gradle cache.

## Local engine-included build

The Windows bootstrap builds the Android host and its locally needed libraries
from source, using a user-supplied official OldUnreal archive. The result is a
private debug build, not a redistributable release. The archive and transformed
runtime remain in ignored local storage; no game content is included in the APK.
The proposed initial publication is source-only: users would obtain their own
inputs and build locally. Whether the transformation scripts and this local
build approach may be publicly offered is still awaiting rights clarification.

## What you need

- Windows, Git, Python 3.11 or newer, JDK 17 or newer and Android SDK platform
  36, with SDK CMake 3.22.1 and NDK 23.2.8568313 installed. Also install CMake
  3.27 or newer on `PATH` for mpg123. Set `ANDROID_HOME` and
  `JAVA_HOME`. The compatibility bridge currently assumes the Windows NDK layout.
- For the local engine-included build, obtain the official [469e Linux ARM64 patch archive](https://github.com/OldUnreal/UnrealTournamentPatches/releases/tag/v469e) yourself,
  `OldUnreal-UTPatch469e-Linux-arm64.tar.bz2`, obtained separately. Its required
  SHA-256 is `4c3978073b12b049c3ffdeb4d275cfc7a2313650f3eb5b94db06fbfee77c3e3b`.
- A prepared matching UT99 game folder for the phone; you import it into the app
  separately. A stock GOTY folder needs the matching 469e packages and fonts.
- Internet for initial Git source clones and Gradle dependencies, and several
  gigabytes of free space. Download access does not itself confer rights to
  redistribute a modified runtime.

From the project folder in PowerShell:

```powershell
python scripts/bootstrap_windows.py --patch-archive 'C:\path\to\OldUnreal-UTPatch469e-Linux-arm64.tar.bz2'
```

The script refuses to use a nonempty `research/bootstrap` directory, checks the
archive hash, clones pinned SDL/audio commits, applies the checked-in patches,
builds the Android dependencies and assembles the APK. It copies the supplied
archive into its build workspace; it does not alter or delete the original.
The patch archive and game content are not downloaded by the bootstrap.
To try again without
removing anything, pass `--work-dir research/bootstrap-another-attempt`.
For offline development, `--local-sources <directory>` uses local Git mirrors
named as specified in the script. `--no-apk` prepares the bundle only.
FMOD and the Cluster audio driver are excluded by default. See [the evaluation](OPENAL_ONLY_EVALUATION.md) for test limits.
For a local rebuild after modifying an audio library and for the candidate
source supplement, see [audio source and rebuild](AUDIO_SOURCE_AND_REBUILD.md).

The output is `app/build/outputs/apk/debug/app-debug.apk`. Install it on an
ARM64 Android 14+ device, then import the game folder in the app. Uninstalling
removes its private game copies and settings. The original folder is not deleted
by import. See [folder import](FOLDER_IMPORT_DESIGN.md).

To prepare a separate copy of a stock game folder, apply the official 469e patch
using its instructions. The matching `System/*.u`, `SystemLocalized/int/*.int`
(placed in the game's `System` folder), and `Textures/UWindowFonts.utx` must be
present alongside the game Maps, Textures, Sounds and Music. Keep your original
installation and back it up before applying any patch. The runtime bundle is
handled by the APK build and is not a substitute for the player's game data.

The SDL source and local patch are pinned. A newly compiled SDL can differ in
bytes from our older local binary, so the bootstrap records the built binary's
hash in the generated runtime catalog. Original engine archive and transformation
inputs remain checked against fixed hashes. A successful build is not proof of
bit-for-bit reproducibility or of gameplay on a particular phone.

On 2026-11-12 the bootstrap completed in a fresh dependency workspace on the
development Windows machine using local Git mirrors, the existing Android SDK
and Gradle cache. The APK's bundled runtime and 469e notice were compared to the
fresh outputs. That earlier test did not verify remote cloning, a different
machine, or gameplay with the newly rebuilt libraries.

Later the same day, a separate source-review snapshot was extracted into a new
workspace and built using fresh network clones and a new Gradle user home. All
38 Gradle tasks executed and the debug APK assembled successfully. APK and
nested runtime ZIP integrity checks passed; the runtime contained engine files,
but no FMOD, Cluster, maps, textures, sounds or music. This test reused the
installed SDK, JDK and Windows machine; it was not an independent machine or a
gameplay test. The subsequent source changes were attribution comments, notices
and review documentation, not build logic. Treat the debug APK as private.

Rights for public distribution of the transformed engine remain unresolved;
LGPL source/relinking obligations and other notices also need release-specific
review. See [release preparation](RELEASE_PREPARATION.md) and
[third-party notices](../THIRD_PARTY_NOTICES.md). Do not publish the ignored
workspace, game content or this debug APK as a substitute for that review.

## Current direct-rehost build (2026-11-13)

The `--support-only` bootstrap now selects `-Put99DirectRehost`, the current
user-importing compatibility host, with application ID
`org.ut99android.host.directprobe`. It does not package UT engine files or game
assets. The earlier engine-free application ID and build instructions below
are historical. This change still needs a clean bootstrap and device validation.

The direct-rehost bootstrap completed on 2026-11-13 in a fresh dependency
workspace using local Git mirrors, an existing Gradle cache and the installed
SDK/JDK. The resulting APK was not gameplay-tested. This does not verify a
fresh network bootstrap or a different computer.
