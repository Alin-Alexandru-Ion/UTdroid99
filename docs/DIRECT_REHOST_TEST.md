# Direct Android rehosting test

Private diagnostic build, 2026-11-12: `research/UTdroid99-direct-rehost-debug.apk`.
SHA-256: `4dd0277923a7d457d9d94cfaa035fe8d95b0143840fdbc1e8dc657911eda7c0f`.
Application ID: `org.ut99android.host.directprobe`; it installs separately from earlier test variants.

The user imports their 469e Linux ARM64 patch archive and game folder through Android's document picker. The app copies the inputs into its own storage. Neither the patch archive nor the source game folder is changed. The APK support archive contains Android-side compatibility libraries and no UT engine modules or game assets.

This test uses the existing SDL Android host and compatibility shims, not a Linux distribution, Termux app, X server, virtual framebuffer or VNC. The ARM64 game code is loaded into the Android process. The exact supported 469e launcher and three modules are adapted as sealed, temporary in-memory images; their imported files are not edited. The current adaptation is version-specific, includes launcher TLS instruction transformations and symbol-binding changes, and the host uses existing engine-specific hooks. This is a prototype, not a general ARM64 binary translator or a determination of redistribution rights.

## Validation

- Android 15 x86_64 emulator with Android's ARM64 translation and `-gpu host`: the patch and game folder were imported through the Android picker, the intro rendered, touch opened the game menu, Start Practice Session opened DM-Agony, and touch Fire entered the DeathMatch. The on-screen movement stick was exercised. The final APK was reinstalled and reached the practice map again after moving the control toggle away from the menu.
- The renderer reported `Android Emulator OpenGL ES Translator (NVIDIA GeForce RTX 5060 Ti/PCIe/SSE2)`. This is emulator host-GPU acceleration, not a physical Android GPU benchmark.
- The engine log reported OpenAL Soft/OpenSL audio initialization and music playback, but the emulator was started with audio disabled, so audible sound was not verified.
- After the match, the staged launcher and Render, IpDrv and ALAudio SHA-256 hashes matched the original patch entries. In-memory transformation fixture tests passed and rejected an invalid launcher. Original patch archive and source game folder were not modified.
- The other emulator configuration using software graphics failed to create the ES 3.1 context required by this renderer. This does not establish what will happen on a physical phone.
- Android APK signature verification and 16 KiB ZIP alignment passed. Native ARM64 hardware, physical controllers, touch aiming quality, audio playback, Android 17 and 16 KiB runtime behavior have not been verified.

Build with `gradlew.bat --offline --no-daemon -Put99DirectRehost -Put99RuntimeBundle=<support-only-zip> assembleDebug` after preparing the local Android support bundle. The test app has a different application ID, so it has a separate imported game copy and settings. Its imports are removed if it is uninstalled.

## Controller and network test update

The updated APK `research/UTdroid99-controller-network-debug.apk` (SHA-256
`70f047d96679f4981f39dedea324448a5864995c1e68a19c57eebbb2b2cf7f30`) retains
the same application ID and can update the preceding direct-rehost test when
signed with the same debug key. Android's normal INTERNET permission is
declared in the main manifest. The game browser is retained, and setup now
offers joining a hostname or IPv4 address with an optional UDP game port.
The address is validated and passed as one launcher argument, not to a shell.

On an Android 15 emulator using host graphics and ARM translation, a temporary
virtual Xbox controller connected before game launch opened through SDL's
GameController API; a right-stick event reached the camera axis hook, a held
left-shoulder press reached the directional dodge adapter with the selected
stick direction, both analog triggers produced corresponding Android/SDL
events, and Start displayed the in-game menu. SDL remapping, controller
selection, deadzone, trigger hysteresis and dodge sequence fixture tests
passed. Simulated events do not establish real Bluetooth hardware behavior or
prove every button's in-game effect.

The direct-join setup dialog connected the Android client to a privately hosted
Windows UT server on both UDP 7777 and custom port 7787 over emulator-to-host
loopback. The server recorded HELLO, LOGIN and JOIN; the client displayed the
server's DeathMatch map. No public internet server, server browser listing,
NAT, custom server packages, or physical device was tested. Server-address
validation and APK signature/alignment checks also passed. Emulator test data,
the virtual controller, local servers and temporary copies were removed.
