# Publication route review

Reviewed 2026-11-12. This is an engineering assessment, not a legal opinion or
permission from any rights holder.

## Recommended route: independent source project

UTdroid99's host, compatibility bridges, build instructions and original work
can be presented as UTdroid99's Android adaptation, clearly crediting Epic,
Digital Extremes, OldUnreal and third-party authors. The project licence applies
only to work its contributors can license. Third-party patches, notices and
the Gradle wrapper have their own terms. The candidate must be reviewed before
publication; an exclusion scan cannot establish authorship or permission.

The candidate source excludes game assets, the OldUnreal archive, engine
binaries and APKs. Nevertheless, it includes scripts that transform a local
copy of the 469e engine: launcher PIE/TLS changes and symbol-binding changes.
The legal status of publishing those scripts and performing the transformations
is not settled by omitting the binaries. Seek qualified advice or rights-holder
clarification where needed; do not present this uncertainty as proof that the
source repository is prohibited.

The app as currently built packages modified engine binaries. Do not publish the
APK until applicable engine rights and release-specific third-party obligations,
including LGPL source, replacement and debugging requirements, are resolved.
The source-only candidate and a binary distribution are separate decisions.

## Upstream fork alternative

[OldUnreal's public patch repository](https://github.com/OldUnreal/UnrealTournamentPatches)
publishes releases, documentation and an issue tracker. Its visible default
tree does not provide a complete buildable UT99 ARM64 engine source tree. The
[469d release notes](https://github.com/OldUnreal/UnrealTournamentPatches/blob/master/ReleaseNotes.md)
describe ARM64 support; that does not imply engine source access. OldUnreal
also publishes individual components such as
[XOpenGLDrv](https://github.com/OldUnreal/XOpenGLDrv), which are not a full
engine source tree and have their own applicable terms.

Forking the public patch repository would copy its public Git history, not
grant access to the private engine source or permission to redistribute a
modified runtime. A fork could be useful for coordination if maintainers want
that, but it does not resolve the missing rights or make UTdroid99 their work.
No public fork, branch, repository or message was created in this review.

## Evidence and remaining work

- `scripts/audit_source_tree.py --game-dir research/pc-reference` screened 170
  candidate files, found no flagged files, and compared matching sizes with the
  local game copy. It is heuristic: it cannot find every secret, identify
  non-identical game derivatives, or establish rights or authorship. A new
  public repository's full staged files and future history still need review.
- The checked-in SDL2, OpenAL Soft, libsndfile and libxmp patches reverse-check
  against the corresponding locally prepared source trees. This checks patch
  applicability, not provenance or legal compliance.
- `scripts/prepare_source_review.py` creates a fresh allowlisted snapshot and
  SHA-256 manifest. The resulting snapshot is a review candidate, not a release.
- `RIGHTS_REQUEST_DRAFT.md`, `COMPONENT_LICENSE_MATRIX.md`, `LGPL_REVIEW.md`
  and `RELEASE_PREPARATION.md` record the unresolved rights, file provenance,
  source, notice and relinking questions. Verify bootstrap on a second clean
  machine and physical-device behavior before representing it as ready to use.
