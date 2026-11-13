# Draft request for rights clarification

This is a ready-to-review inquiry for UTdroid99 to send. No request has been sent.
Do not attach private game files, the local APK, or proprietary binaries unless
the recipient asks for them and the applicable sharing rights are clear.

One possible first contact is Smirftsch@oldunreal.com, published on the
[OldUnreal Unreal Tournament forum](https://www.oldunreal.com/phpBB3/viewforum.php?f=47).
That listing is not evidence that this person can grant the requested rights;
ask to be directed to the appropriate maintainer or rights holder if necessary.

**Subject:** Guidance on an independent Unreal Tournament Android adaptation

Hello OldUnreal team,

I am developing UTdroid99, an independent experimental Android host for
Unreal Tournament. It is not an official or endorsed Epic or OldUnreal project.
I would like to understand which parts could be shared and whose permission is
needed. I am not asking you to speak on Epic's or other licensors' behalf.

Our preferred initial release is a source-only GitHub repository with build
instructions. We do not plan to provide an APK initially. Each user would obtain
the official patch and their own game files separately and build locally.

The build takes a separately obtained OldUnreal 469e Linux ARM64 patch archive,
verifies its hash and creates local runtime copies. To load them on Android, it
clears a launcher PIE flag, rewrites selected launcher thread-local storage
accesses, and changes GNU-unique symbol bindings in Render.so, IpDrv.so and
ALAudio.so. At runtime the host also redirects selected Core.so and SDLDrv.so
function-pointer slots in memory for compatibility and controller input. The
scripts contain version-specific hashes, offsets, symbols and instruction
patterns derived from the patch. Some original patch modules and configuration
files are bundled into each locally built APK alongside those modified copies.

The app uses SDL2, OpenAL Soft and other audio libraries with Android
compatibility code; the default build excludes FMOD and Cluster. Users supply
their game content separately, and their maps, music, sounds and textures are
not included in the APK.

I would appreciate guidance on these distinct questions:

1. **Source repository:** May the independent host code, compatibility code,
   build scripts and instructions—including the described binary transformation
   logic—be published on GitHub without including the OldUnreal archive, engine
   binaries or game assets? In particular, is publishing the version-specific
   offsets, instruction patterns and in-memory hooking code acceptable? Are
   there attribution or other conditions, and is Epic authorization needed?
2. **Local builds:** May a player who has lawfully obtained the patch and game
   files use these scripts to transform the patch locally and build the app for
   personal use? Are there relevant restrictions or a supported alternative?
3. **Binary distribution:** If we later wish to share an APK containing the
   transformed 469e engine and launcher, but no player game assets, who can
   grant permission and what conditions, notices and attribution would apply?
4. **Open-source library obligations:** The APK includes LGPL libraries.
   Distribution may require users to be able to modify those libraries and
   debug the modifications. Can the applicable engine permissions or terms allow
   the replacement/relinking and debugging needed for this, and who should make
   that determination? We would welcome a compatible distribution approach.
5. **Project identity:** May we use the name UTdroid99 and refer to Unreal
   Tournament and OldUnreal in the app and documentation with clear attribution
   and a statement that the project is independent and not endorsed?

If any question should be directed to Epic or another rights holder, please let
me know the appropriate contact or process. If another implementation or
collaboration route would be preferable, I would appreciate your suggestion.
I can provide further technical details and a source-only review snapshot.
Thank you for your time and for maintaining the game.

Best regards,
UTdroid99
