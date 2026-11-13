# Engine-specific source and transformation review

Reviewed 2026-11-12 against the source and the isolated 469e ARM64 build. This
is a technical and provenance review, not a legal opinion or permission to
publish. A source-only project contains the following logic and identifiers;
it does not contain the original engine binaries or game assets in the source
review snapshot.

| Location | What it does or contains | Provenance and open question |
| --- | --- | --- |
| `scripts/bootstrap_windows.py`, `extract_core_probe.py` | Verify a separately supplied archive and extract engine inputs to a private build workspace. | Project-session creation is recorded. Archive names and hashes identify OldUnreal inputs. The source-only build still depends on the official archive. |
| `scripts/prepare_launcher_probe.py` | Verify the launcher and clear the `DF_1_PIE` ELF flag in a copy. | Project-session creation is recorded; the resulting launcher is modified. |
| `scripts/prepare_launcher_tls_probe.py` and `experiments/libc-probe/load_tls_trampoline.S` | Detect 138 launcher TLS instruction sequences, replace them with branch/trampoline sequences and disable one TLS relocation. | The Python tool contains version-specific offsets and instruction patterns observed in the launcher, including short machine-code values; the trampoline is compatibility code. The legal treatment of publishing those specifics and applying them is unresolved. |
| `scripts/prepare_render_probe.py` | Change four GNU-unique symbol bindings in Render.so and one in IpDrv.so to global in copied binaries. | Project-session creation is recorded; the resulting modules are modified. |
| `scripts/prepare_shared_runtime.py` | Change one GNU-unique symbol binding in ALAudio.so, bundle engine modules and the two supplied configuration templates, and generate the runtime catalog. | The locally built APK contains both unmodified and modified OldUnreal files. It is not a source-only artifact. |
| `native/host.cpp` | Redirect a Core compatibility call and SDLDrv joystick/input function-pointer slots in the loaded process; invoke a launcher entry point using pinned offsets. | Host code was added in the project session, but contains engine-specific names and offsets derived from the runtime. The modification happens in memory and its legal status is unresolved. |
| `app/src/debug/java/org/ut99android/host/RuntimeCatalog.java` | Store filenames and hashes of local runtime inputs. | Generated from build inputs, not a copy of an engine executable. Hashes and names identify third-party files. |
| `experiments/libc-probe`, `experiments/cxx-bridge`, other compatibility bridges | Implement compatibility functions, versioned symbols and data-shape adaptations for Linux and Android. | Project-session creation was recorded for the candidate code. The glibc reference remains outside the source snapshot; see `CTYPE_ABI.md`. Similarity screening cannot prove independent expression or ownership. |
| `patches/sdl2` | Alter SDL and include upstream context. | SDL zlib notice is retained; altered versions are marked. These are not solely original UTdroid99 code. Audio library patches and Gradle wrapper have their own terms. The obsolete SDL helper was archived under ignored `research/`; the bootstrap uses the patch. |

## Artifact checks

The fresh isolated build's runtime ZIP was compared with the same separately
supplied 469e archive. Core.so and Engine.so were byte-identical to the archive
members. Render.so differed at four byte positions, IpDrv.so and ALAudio.so at
one each, and the launcher copy at 1,659 positions; all compared lengths were
unchanged. These counts describe this build, not every version or runtime
behavior, and do not establish rights or the effects of in-memory hooks.

## Remaining authorship and rights limits

The session history and exact-window similarity screen described in
`FILE_PROVENANCE_REVIEW.md` give useful evidence but do not establish independent
authorship for every line, inspect every possible external source, or settle
rights in engine-derived values. The root BSD 3-Clause licence applies only to original
work its contributors can license, and does not claim the engine or other
third-party code. Confirm provenance and the applicable permissions before
publication. The sendable inquiry in `RIGHTS_REQUEST_DRAFT.md` asks specifically
about source-only publication, local transformation and later APK distribution.
