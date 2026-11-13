# User-supplied patch and game: build options

## Working path: build locally

The Windows bootstrap already takes the user's official, hash-pinned 469e Linux
ARM64 archive, copies it to a private build workspace, builds the compatibility
components, transforms selected copies and assembles an APK. The original
archive is not altered. The user separately imports a prepared, matching game
folder on the phone. The locally built APK contains modified and unmodified
engine files; the source review snapshot contains neither the patch nor game
assets. See `BUILDING.md` for the command and the clean-workspace build result.

The project includes original Android host and compatibility code, but is not
made entirely from original code: it depends on SDL2, OpenAL Soft, libxmp,
libsndfile, mpg123, Android's toolchain and other upstream components under
their own licenses. The checked-in SDL/audio patches include upstream context.
Replacing those dependencies would be a major development project and would
not remove dependence on the UT engine supplied by the user. Engine-specific
hashes, offsets and short instruction patterns in the host and build scripts
come from examining the 469e input; see `ENGINE_TRANSFORMATION_REVIEW.md`.

The old imperative SDL modification helper has been archived in the local,
ignored `research/` directory. The build already applies the checked-in SDL
patch instead; archiving the helper does not remove SDL or its licensing.

## Experimental engine-free APK

The experimental engine-free APK ships the Android host and licensed compatibility/audio
libraries, and asks the user to select two inputs on the phone: the official
469e ARM64 archive and a game folder. It processes copies into app-private
storage. It omits the OldUnreal engine, launcher, patch templates and game
files from the distributed artifact, but includes licensed third-party libraries.

On first launch the engine-free build displays credits and explains that the
player supplies the patch and game, and that the app modifies a private patch
copy for Android compatibility. The player must check an acknowledgment to
continue; Exit leaves it unaccepted. This describes the setup and does not
itself grant any third-party rights or waive library license terms.

Initial engineering work: `PatchElfTransformer` reproduces the four modified
469e launcher/engine files byte-for-byte from the previous local build and
rejects the wrong input. `prepare_support_bundle.py` packages only thirteen
explicitly listed support libraries. The `-Put99EngineFree` debug build checks
that exact list and has a separate application ID. It includes an experimental
patch picker, private archive processor and game-folder merge. On an Android 15
emulator with ARM translation, selecting the official patch
and a separate stock game folder succeeded; the app applied the patch to its
private copy, rendered CityIntro, opened the main menu and entered a DM-Agony
practice match. OpenAL initialized. The source patch and a representative
original map retained their hashes. An invalid archive and a cancelled import
left the prior patch available; a same-key APK upgrade retained both imports.
Actual sound perception, other providers and physical-phone use remain
unverified.

Before a public release, further work includes:

1. Review the separate engine-free build variant; ensure Gradle cannot silently add
   `ut99-runtime.zip` or original engine binaries, and inventory the resulting
   APK and its third-party notices and corresponding-source obligations.
2. Further test the Android system file picker for the 469e `.tar.bz2` archive and stream
   it into app-owned staging. Verify the exact known archive hash and safely
   unpack only expected files. Android does not provide the Python bootstrap;
   a suitable bzip2/tar implementation and its license must be selected.
3. Continue testing the Android ELF transformations and compare their outputs
   byte-for-byte with the tested Windows build for each pinned input. Verify
   sizes, hashes, instruction patterns and expected change counts before use.
   Build-time and in-memory compatibility hooks still exist in this approach.
4. Further test support-library and imported-engine staging, read-only loading,
   existing settings, cancellation and updates on multiple Android devices.
5. Further test merging patch packages, localizations and fonts into private
   copies of stock game folders, including unusual versions and custom mods.
6. Stage and validate the whole result before activation. Handle cancellation,
   wrong versions, malformed archives, storage exhaustion and upgrades without
   deleting an active copy; provide cleanup of abandoned staging data.
7. Test fresh install, both import orders, interruption, upgrade and rollback,
   boot, audio, controller, online play and 16 KiB devices on a physical phone.

An engine-free APK changes what we distribute; it does not by itself settle the
legal status of transformation code or its application to a user's files. LGPL
and other third-party obligations still need review for the actual APK. The
existing tested APK remains the local-build variant until the new path is built
and validated.
