# Audio source and modified-library rebuild

The LGPL components require a distribution-specific compliance review. This
document records a practical local rebuild path; it is not a conclusion that any
published APK satisfies all source, replacement, installation or other terms.

Run the [Windows bootstrap](BUILDING.md) first. Its work directory contains
the pinned upstream source checkouts, the project patches and configured build
trees. For example, edit `research/bootstrap/openal-soft` to modify OpenAL Soft,
then run:

```powershell
python scripts/rebuild_audio_windows.py --work-dir research/bootstrap --component openal
```

The script rebuilds OpenAL, regenerates the local runtime and its integrity
catalog, then assembles a debug APK. Other `--component` values are `sndfile`,
`mpg123` and `xmp`; repeat the option for several. The runtime catalog's hashes
protect the installed bundle, so simply dropping a different library into the
old APK will not work. A newly built APK carries the updated hashes.

For the engine-free import APK, use a workspace created with
`bootstrap_windows.py --support-only` and select the engine-free rebuild path:

```powershell
python scripts/rebuild_audio_windows.py --work-dir research/support-build --component openal --engine-free
```

This makes a new `ut99-support-modified.zip` and rebuilds the engine-free APK;
choose a new `--support-output` path when repeating the command. To bind a
candidate source supplement to an engine-free APK, pass both `--apk` and
`--runtime <path-to-the-support-zip>` to the supplement script. Source trees
with additional local modifications are intentionally rejected by that script;
the distributor must provide the matching modified sources by an appropriate
method. A compatible signing key is still needed for an in-place APK update.
In an isolated emulator test, a locally modified OpenAL library was rebuilt
into the engine-free APK and installed as a same-key update. Both game and
patch imports survived; the engine loaded the modified library, rendered the
intro and reported its identifying device string. Restoring the normal APK
also retained the imports and restored the original OpenAL hash. This does not
test a different signing key or establish full LGPL compliance.

The APK must be signed by a key compatible with an existing installation to
install as an update. Do not uninstall an installation containing game copies
or settings without first preserving the user's data. Building this way still
uses the user's separately obtained OldUnreal archive; engine distribution
permission remains unresolved. On 2026-11-12, OpenAL, libsndfile and mpg123
sources extracted from the candidate supplement were changed with identifying
strings, rebuilt and packaged into a debug APK. Their packaged libraries and
catalog hashes matched. With the same debug signing key, that APK installed as
an update on an isolated Android 15 emulator without removing its imported
game. The intro rendered, the engine reported the modified OpenAL device and
music playback, and the emulator audio capture contained nonzero samples.
The test verified all three modified files were installed and no audio preload
error was logged; it did not separately exercise sndfile and MP3 decoding. It
does not test a different signing key, a physical device, subjective sound
quality, or every interface-compatible modification. It does not establish
legal compliance.

For a candidate source supplement, using the unmodified bootstrap checkouts:

```powershell
python scripts/prepare_audio_source_supplement.py --source-root research/bootstrap --output research/audio-sources.tar.gz
```

To tie a candidate supplement to an APK just built in that workspace, add
`--apk app/build/outputs/apk/debug/app-debug.apk`. The generator checks that
the APK's runtime ZIP and five SDL/audio library hashes match that workspace,
then records the hashes, source commits and patches in `BUILD_EVIDENCE.json`.
This verifies matching files at collection time; it does not independently
prove the binaries were built from those sources or establish legal compliance.

The generator checks the exact upstream revisions and expected tracked patches,
then packages those source trees. It records omitted game-format and binary test
fixtures. It refuses modified tracked trees because it only prepares the
original pinned build's candidate sources. Before any release, match the
sources to the actual libraries and determine whether omissions, build scripts,
third-party notices and the method for replacing or relinking libraries satisfy
the applicable terms. The generated archive does not contain the engine, game
files or APK and is not by itself a complete compliance determination.

The current exclusions are upstream libxmp test inputs: the `ZEN-dif-prty.exe`
module fixture and two malformed UMX inputs (`load_umx_invalid_objname.umx` and
`load_umx_invalid_names.umx.xz`). The libxmp build config disables unit tests;
these omissions prevent running those specific tests from the supplement. They
are not OpenAL, libsndfile or mpg123 source files. Their omission is recorded to
avoid distributing executable or game-format files without separate review;
their effect on the completeness of any proposed distribution remains a
release-review decision.
