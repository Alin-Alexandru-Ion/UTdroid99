# Experimental phone storage setup

## Current engine-free experimental build

Select the official 469e Linux ARM64 patch archive, then select your UT99 game
folder. The app imports and processes copies in private app storage; neither
selected source is edited. It needs free storage for both imports. Imported
data and settings are removed when the app is uninstalled. Reports can be
exported with Android's system Save picker. The engine-free build uses the
separate application ID `org.ut99android.host.enginefree`, so it installs beside
the previous local-build app and does not migrate its private data.

An Android 15 emulator with ARM translation imported both files, rendered the
intro, opened the menu and started a Deathmatch. Invalid and cancelled patch
imports retained the previous copy. Physical-device, other document-provider,
low-storage and interruption testing are still needed.

## Earlier debug build: version 0.21

The app uses Android's folder picker to read a user-selected prepared UT99
folder, copies it into internal app storage, and runs the copy. It no longer
declares `MANAGE_EXTERNAL_STORAGE`. The original folder stays untouched and can
be deleted by its owner after verifying the copy; imported files and app-private
settings are removed on uninstall. Existing installations must import their
external folder once; their private configuration remains in place. Reports are
kept in app-private storage and exported as a ZIP using the system Save picker.
An isolated emulator imported and booted the game after its temporary source was
removed; see `FOLDER_IMPORT_DESIGN.md` for test scope and remaining limitations.

## Historical behavior through version 0.20

The debug app defaults to `Internal storage/UT99` (`/storage/emulated/0/UT99`) and creates that directory after file access is granted. The setup screen can select another folder on primary internal storage. The native engine uses direct file paths, so cloud providers and removable volumes are not supported by this picker. Put the prepared `Maps`, `Music`, `Sounds`, `System`, and `Textures` folders inside the selected directory. `System` must contain the matching 469e script and localization packages, and `Textures/UWindowFonts.utx` must match 469e. An unpatched GOTY folder alone is not equivalent to the prepared set.

The local debug APK includes the experimental runtime bundle, including SDL, the launcher, engine modules, compatibility libraries and configuration templates. On Start, the app verifies the included files against its pinned catalog and copies them read-only into its private runtime directory. It creates writable `UnrealTournament.ini` and `User.ini` only when they do not exist. Game assets are not included. The local APK contains original and modified runtime binaries and is for private testing; distribution permissions remain an open gate.

The engine uses filesystem paths, so this prototype asks Android for **all files access**. That grant gives the app broad read/write access to shared files; the app's asset configuration points to the UT99 folder, but the operating-system permission itself is broader. The permission can be revoked in Android settings. This approach is debug-only and has not been assessed for store distribution. Android's folder picker provides content URIs, which the unmodified engine cannot open as ordinary paths.

On this development PC, with the verified patch, locally owned game files, Android tools, and research builds already prepared:

1. Run `python scripts/prepare_private_content.py` and `python scripts/prepare_shared_runtime.py`, then build the APK. The runtime script generates the pinned catalog used by the APK.
2. Connect the phone with USB debugging and determine its serial with `adb devices -l`.
3. Copy the prepared game-content folders into `Internal storage/UT99`. The optional legacy `scripts/stage_shared_content.ps1 -Serial <serial> -Adb <path-to-adb>` refuses to overwrite an existing `/sdcard/UT99`; it also copies the runtime bundle to shared storage, but the current app no longer needs those extra shared runtime files.
4. Install the debug APK, open UT99 Android Host, choose **Set up phone storage and start UT99**, allow Android's file-access setting, and start the experiment once its basic file checks pass.

Saves and configuration remain in app-private locations; reports are written into `Reports` under the selected game folder. This is not a synchronization system. The separate legacy ADB runtime staging script refreshes private INI files and should not be treated as a settings-preserving updater.

Version 0.14 adds Android-native OpenAL Soft 1.23.1 (`d3875f333fb6abe2f39d82caca329414871ae53b`), libxmp 4.6.2 (`828ef357943e1fbb13910e7a6fca21987c5c5827`), libsndfile 1.2.2 (`72f6af15e8f85157bd622ed45b979025828b7001`), and mpg123 (`b6881556a0ee8f417ccc14ded553893b533ffe0c`), built under ignored research directories. OpenAL and libsndfile are LGPL-2.0-or-later; retain their source and license terms when distributing. The OpenAL, xmp and sndfile build files were locally configured to emit the sonames required by the engine; the libsndfile build disables external FLAC, Ogg, Opus and MPEG support. The existing independent mpg123 library handles its own engine entry points. Codec coverage and licensing must be reviewed before any distribution.

In an ARM-translated Android 15 emulator, the engine initialized ALAudio with the OpenAL Soft OpenSL backend, opened `Music/Uttitle.umx`, and Android showed an active 44.1 kHz output track while the game ran. This does not establish that music or sound effects were audible or correct; speaker output, other music and sound formats, and a physical Android device remain untested. If an audio library fails to load, the host falls back to `-nosound` instead of stopping the game.

On the Android 15 ARM-translated emulator, the permission screen, missing-file message, populated-folder check, and introduction rendering were checked. For the direct-read test, the private `Maps/CityIntro.unr` was temporarily renamed, the intro rendered with the shared folder configured, and the private file was restored. In that test the previous private runtime and shared launcher were moved aside; the app verified and extracted the runtime from the APK, then rendered the intro. The setup checks common startup paths, not the contents or version of every asset. See the 0.14 audio result above for the later audio-enabled run.

Version 0.15 presents the startup steps, explains that the runtime is included but game files must be supplied, lists all missing startup files, and separately warns about missing sound/music folders and common game-type metadata. An emulator test used six non-game placeholder files to validate the warnings; this did not validate game content. A separate pre-launch configuration fixture checked that an existing GUI scale and menu style are retained, and a fresh configuration receives default values.

Debug version 0.2-reporting writes reports to `Internal storage/UT99/Reports` after a recorded game-process crash or a nonzero launcher return. If the app closes, reopen it so it can collect Android's recorded exit details. The setup screen also has **Save diagnostic report** for failures that did not result in a recorded crash. Reports can include device information, paths, player-related game log text, Android logcat, and a separate `.native.pb` tombstone when Android makes one available. File access must be granted; OS exit records and traces are best effort and may be unavailable. Emulator tests used controlled Java and native crashes and verified report creation, including after the reporting process was killed and restarted. Actual phone behavior remains to be checked.
