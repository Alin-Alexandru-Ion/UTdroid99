# GitHub release preparation

Status: research for a possible public source release; no release or rights
clearance has been obtained. This is not legal advice.
See [the component license matrix](COMPONENT_LICENSE_MATRIX.md) for the
artifact-specific review and outstanding questions. The [LGPL review](LGPL_REVIEW.md)
separates source-only publication from a future binary distribution.

## What the local prototype contains

The current direct-rehost debug APK bundles SDL2, Android host code,
compatibility bridges, and audio libraries. It does not bundle the OldUnreal
engine or launcher or the user's game content; players import their own patch
and game folder. The app makes temporary engine adaptations in memory.
`research/`, build directories and APKs are ignored, but a file-by-file and
history audit remains necessary before publishing any repository or branch.

The top-level `LICENSE` selects BSD 3-Clause terms for original UTdroid99 code only;
it excludes game and third-party components. Verify file-level authorship and
permissions before publishing; do not represent third-party work as our own.

## Primary-source findings (checked 2026-11-12)

- [OldUnreal's README](https://github.com/OldUnreal/UnrealTournamentPatches)
  says its maintenance followed a 2019 agreement with Epic and that its own
  project was approved by Epic. That statement is about OldUnreal's project;
  it does not establish that the agreement extends to this Android build.
- The `LICENSE.md` in the hash-verified 469e Linux ARM64 archive contains
  third-party notices; its SHA-256 is
  `f40d2b9a0465dda8b0063ca8b43226b376ca82c79923dc30a7324a20a1e1697f`.
  [OldUnreal's current license notice](https://github.com/OldUnreal/UnrealTournamentPatches/blob/master/LICENSE.md)
  additionally directs UT use and distribution to Epic's terms. Neither is
  an established grant to redistribute this modified Android runtime.
- [Epic's Terms of Service](https://legal.epicgames.com/epicgames/tos?lang=en-US)
  reserve rights and contain restrictions relevant to modification; the
  applicable terms, exceptions and any additional permission need review by
  the rights holders or qualified counsel. Do not treat this summary as an
  interpretation of the user's specific license.
- SDL2 is under the zlib license. The local source modifications and the
  upstream license are recorded in `patches/sdl2/`. Check and supply notices,
  modified source and other applicable obligations for every shipped library,
  including the locally modified LGPL components; this inventory is incomplete.
- The default runtime now excludes the optional FMOD library and Cluster
  driver. A Gradle check rejects bundles that include them. Older debug APKs
  included both and are not covered by this change. See the
  [OpenAL evaluation](OPENAL_ONLY_EVALUATION.md) for scope and test limits.

## Questions to send to the appropriate rights holders

No message has been sent. Prepare a concrete description and ask OldUnreal
which requests they can authorize and whether Epic's permission is needed:
[`RIGHTS_REQUEST_DRAFT.md`](RIGHTS_REQUEST_DRAFT.md) contains a reviewable draft.

1. May we redistribute the 469e Linux ARM64 engine, launcher, scripts and
   localization packages in an Android APK or a repository release?
2. May we distribute the locally modified binaries and compatibility bridges,
   and under what conditions, notices and support/disclaimer wording?
3. If not, is a source-only host plus user-supplied official patch archive and
   game files acceptable, and are there restrictions on automated extraction
   and transformation on the user's own device or computer?
4. How may we name the project and credit Epic, Unreal Tournament, OldUnreal,
   contributors and trademarks without implying endorsement?
5. Are there separate terms for multiplayer compatibility or integrity?

## Remaining public-beta work

- Run `python scripts/audit_source_tree.py --game-dir <local-game-folder>` as a
  preliminary screen. It checks candidate files for selected binary/game-file
  signatures, common credential patterns and exact matches to the supplied game
  folder. It is heuristic, uses project-specific exclusions, and is not a full
  Git history, secrets, provenance, or legal audit.
- `scripts/prepare_audio_source_supplement.py` prepares a candidate archive of
  the pinned and locally modified SDL/audio sources. It omits upstream libxmp
  test fixtures recorded in `OMITTED_FILES.txt`. Verify source completeness, exact
  binary correspondence, notices and relinking with modified LGPL libraries
  before relying on it for distribution; it is not a complete compliance claim.
- Identifiable OpenAL, libsndfile and mpg123 modifications were built from the
  source supplement, packaged and installed in an emulator after a same-key APK
  update. The imported game remained available and the engine reported the
  modified OpenAL device and music. The test did not separately exercise
  sndfile or MP3 decoding. This does not establish LGPL compliance or an update
  path with a different signing key. See [audio source and rebuild](AUDIO_SOURCE_AND_REBUILD.md).
- `scripts/prepare_source_review.py` creates a local, allowlisted source
  snapshot with per-file hashes. It is a review artifact only: its screening
  cannot settle legal rights, source provenance, secrets or future Git history.
- Review all proposed files, provenance, generated artifacts, private paths,
  reports, credentials, project license, and actual third-party notices.
- Verify the Windows bootstrap on another clean machine, verify remote source
  availability and compare a rebuilt APK's gameplay with the known local build.
  The bootstrap obtains pinned sources and uses a separately supplied archive;
  it does not establish permission to publish a transformed engine.
- Complete physical-device, low-storage, cancellation, provider and upgrade
  testing for the scoped folder import in `FOLDER_IMPORT_DESIGN.md`.
- Validate actual shoulder dodge and translocator gameplay, installation,
  performance, suspend/resume, networking and multiple phones/controllers.
- Provide release notes, support expectations, diagnostics/privacy information,
  user-owned game data instructions, and an explicit experimental status.

Google Play publication is out of scope.

## Direct-rehost artifact update (2026-11-13)

The current `-Put99DirectRehost` debug APK bundles Android compatibility and
third-party support libraries, but not OldUnreal engine files or game assets.
It imports the patch and game folder from the user and adapts engine code in
memory. Earlier statements below about an engine-containing debug APK describe
the historical engine-included variant, not this direct-rehost artifact. The
corresponding-source/replacement checks and release-specific review remain open.
