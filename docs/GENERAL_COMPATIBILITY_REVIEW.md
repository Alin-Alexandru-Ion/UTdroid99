# Reducing engine-specific binary handling

This is an engineering investigation, not a legal opinion. The aim is to
reduce hard-coded engine internals while preserving a working build. A generic
method does not by itself grant rights to modify or distribute a game, and no
technical approach establishes that every legal concern has disappeared.

## Relocation-based call lookup: read-only prototype

The host currently hard-codes offsets for six SDL joystick imports, one SDLDrv
input call and one Core C++ compatibility call. The ELF dynamic relocation
tables identify these eight slots by symbol. The prototype
`experiments/elf-relocations/inspect_imports.py` reads section headers,
dynamic symbols and AArch64 JUMP_SLOT relocations from a supplied ELF; it does
not modify or execute the file. On the user's pinned 469e binaries its results
matched all eight offsets in the host, independently cross-checked with NDK
`llvm-readelf`. Synthetic tests cover lookup, truncated sections and an invalid
symbol index. It is not integrated into the app.

This suggests replacing the hard-coded slot numbers with general ELF
relocation parsing. The app still needs to identify the functions and redirect
calls; resolving a slot dynamically does not remove the in-memory hook or
establish a legal conclusion. A production implementation needs a safe loaded
image parser (the prototype reads file section tables that may be absent),
bounded memory validation, ambiguous-symbol handling, correct original-target
verification, page-protection rollback and a tested fallback. Test the actual
game, controller, failures and lifecycle on physical Android before switching.

## Other changes

| Current technique | Possible direction | Limitation |
| --- | --- | --- |
| Launcher PIE flag cleared in a local copy | A compatible Linux execution environment or a new launcher that invokes supported entry points. | The current Android host loads the launcher as a library. An execution environment must also solve rendering, input, lifecycle and Linux ABI compatibility. It is not implemented here. |
| Launcher TLS instruction patterns and one relocation rewritten | A loader supporting the required Linux AArch64 TLS behavior, or an upstream Android-compatible launcher. | A different loader or runtime has substantial work and its own licensing; no equivalent was demonstrated. Moving the rewrite to the phone does not remove it. |
| Entry point reached via a pinned launcher offset | A documented/exported entry point, or a supported upstream launcher. | The pinned launcher does not export `main` in its dynamic symbols. A new route has not been established. |
| GNU-unique symbol bindings rewritten in copied modules | A compatible loader with correct GNU-unique semantics. | No such Android loader has been validated here. Removing the on-disk edit requires testing actual module loading and symbol behavior. |
| In-memory hooks for controller, input and C++ compatibility | Supported upstream interfaces, symbol interposition, or general relocation lookup. | The relocation prototype removes fixed slot numbers, not the hook itself. Symbol interposition may not work for all calls or loader configurations. |
| Engine and game binaries included in locally built APK | Future engine-free APK imports user-supplied patch and game files. | The engine-free variant is not implemented. It changes what is distributed but still needs compatible loading and third-party license compliance. |

The existing tested path is intentionally unchanged. Replacing the actual game
engine with an independently developed compatible engine would be a much larger
project, require separately supplied game assets, and would still need its own
copyright, trademark and third-party license assessment. See
`USER_SUPPLIED_PATCH_OPTIONS.md` for the engine-free APK plan.
