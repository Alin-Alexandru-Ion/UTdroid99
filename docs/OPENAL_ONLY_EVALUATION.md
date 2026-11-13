# OpenAL-only experimental build

The default build excludes `Cluster.so` and `libfmod.so.13` from the bundled
runtime. ALAudio and the OpenAL Soft, libxmp, libsndfile and mpg123 libraries
remain. The evaluated APK is a private debug artifact, not a cleared release.

If an existing `UnrealTournament.ini` selects `Cluster.ClusterAudioSubsystem`,
the OpenAL-only host copies that configuration to
`UnrealTournament.ini.before-openal-only` if no backup exists, then selects
`ALAudio.ALAudioSubsystem`. Other settings and other audio selections are not
rewritten. A prior install may retain old runtime files in its private directory;
this experiment does not delete those files or any imported game copies. A fresh
installation is required to verify that no old Cluster/FMOD files are present.

## Emulator result, 2026-11-12

On a fresh isolated Android 15 x86-64 emulator with ARM translation and host
NVIDIA graphics, the app imported a prepared game folder, rendered CityIntro,
displayed the menu, started an offline DM-Agony match and fired the weapon.
The engine logged successful ALAudio initialization with OpenAL Soft/OpenSL,
and logged playing the `Uttitle` and `utmenu23` tracker music. Android reported
an active 44.1 kHz game audio track. A separate run using the emulator's WAV
backend captured stereo PCM with nonzero samples while the intro, menu and a
match with weapon fire were exercised. This is signal-path evidence; the
recording was not perceptually assessed and does not establish sound quality,
that each individual effect was audible, or phone-speaker behavior.

The packaged runtime and the fresh app-private runtime omitted both excluded
files. An ELF dependency scan found no remaining original SystemARM64 libraries
with a direct `DT_NEEDED` entry for either excluded library. A simulated saved
Cluster configuration was migrated, and its backup retained the prior setting.
The test does not cover dynamic loads by every optional module, all game modes,
multiplayer mods, audio formats, devices or upgrades. The emulator test data was
removed afterward.

The default build avoids bundling FMOD. It does not by
itself resolve the separate rights to the transformed OldUnreal engine, or the
source, notice and relinking obligations of other libraries. Consult
`RELEASE_PREPARATION.md` and `THIRD_PARTY_NOTICES.md` before any release.
