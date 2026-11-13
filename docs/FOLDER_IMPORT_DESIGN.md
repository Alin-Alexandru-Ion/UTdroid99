# Game-folder import without broad file access

Status: implemented in debug version 0.21. The system picker provides a
read-only grant; the app copies the tree into internal app storage, validates
required startup files and selects it only after success. It does not request
all-files access. Previous imported copies and the user's source stay intact.

On an isolated Android 15 x86_64 emulator with ARM translation, a prepared
folder imported successfully, three representative SHA-256 hashes matched,
and an Assault match rendered after the temporary source folder was deleted.
An empty second import was rejected and the prior copy remained selected.
Diagnostic ZIP export through Android's save picker succeeded. Physical-phone,
low-storage, cancellation, cloud providers and multiple-import gameplay remain
unverified.

## Proposed experience

1. Explain that players select their own prepared UT99 folder. The system
   folder picker grants access to that folder tree, not the whole device.
2. Show what will be copied and that the original is left alone. Copy progress
   and individual-file free-space checks are implemented; total-size preflight
   is not yet available.
3. Stream the chosen tree into a new app-owned game-data directory. Use ordinary
   filesystem paths for the engine. Show progress and allow cancellation.
4. Validate the completed copy, then switch the active game folder. Preserve
   the previous active copy and all existing settings until success is confirmed.
5. Offer re-import/update for new mods and files, without deleting the original
   or overwriting configuration. Explain that copies require extra storage and
   app-owned files may be removed on uninstall.
6. Continue writing crash reports, and offer an Android system Save or Share
   action so reports can be exported back to a folder the player can access.

Use `ACTION_OPEN_DOCUMENT_TREE` and access data through `ContentResolver` and
`DocumentsContract`. A URI must not be converted into a guessed filesystem
path. The OS restricts selection of storage roots and certain folders; providers
can also refuse operations or go offline. See [Android's folder access guide](https://developer.android.com/training/data-storage/shared/documents-files).

## Implementation checks

- Handle provider errors, unexpected names, duplicate/case-colliding paths,
  cancellation, insufficient space and partial imports without switching to
  a broken copy or deleting a previous copy. Avoid following provider-supplied
  paths outside the app-owned staging directory.
- Preserve custom maps, mods, `.int` metadata, assets and required 469e files;
  do not silently substitute original GOTY System binaries for patched files.
- Keep user settings, save data and existing direct-storage installations; make
  migration an explicit choice. Clarify whether later server downloads and
  saves live inside app-owned paths and how they can be exported.
- Test real game boot and gameplay from the copy, changed/additional content,
  interrupted import, replacement, low storage, reports export, and both fresh
  installs and upgrades. Test more than one document provider and device.
- The app does not migrate existing shared folders automatically: upgrading
  users choose and import their old folder once. Their existing private game
  settings are preserved, and the old external source is never removed.

Version 0.22 adds a copy manager for selecting a previously imported copy and
deleting inactive copies after confirmation. The active copy cannot be deleted.
Selected maps and their dependencies can be added to the active copy without
re-importing the full game: `.unr` to Maps, `.utx` to Textures, `.uax` to
Sounds, `.umx` to Music, and `.u` / `.int` to System. The file picker grants
access only to the selected files. Existing filenames are skipped rather than
overwritten, including names that differ only by letter case. Native libraries
and compressed `.uz` files are not imported by these controls.

The app still requires a prepared 469e folder and does not fully automate its
preparation. Imports are copies, not a synchronized mirror. Adding a map alone
does not automatically install any textures, sounds, scripts or other packages
it may need. Old game copies and current game settings are distinct.
# Touch controls in the experimental debug build

Start the game without a connected controller to show the touch layout. The
left stick moves; drag an uncovered area of the game view to aim, and tap the
view to click game menus. The Menu button sends Escape (also skipping the intro),
and Hide controls temporarily removes the gameplay controls. The keyboard
button opens Android's keyboard; M is ordinary text again. A controller
connected before launch selects the controller layout and Start sends Escape.
Restart the game after connecting or disconnecting a controller to switch modes.
The buttons use the game's joystick bindings, so changing those bindings also
changes their actions. This is experimental and requires physical-device play
testing; the emulator verified virtual-joystick initialization but its OpenGL
backend could not initialize the game renderer.

