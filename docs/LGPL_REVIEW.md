# LGPL review for source and APK distribution

This is an engineering review, not legal advice or a release approval. It
separates the proposed public source repository from a possible later APK.

## A source-only GitHub snapshot

The source review snapshot excludes the OldUnreal archive, engine binaries,
APKs and game files. It includes project code, SDL changes, and patches that
modify upstream audio-library build files. The project BSD 3-Clause license covers only
original UTdroid99 work; it does not replace any upstream license. Preserve the
upstream copyright and license texts, mark altered files and identify their
changes. The audio CMake patches now add dated UTdroid99 modification notices.
The SDL changes have UT99Android alteration markers. The source supplement is a
separate review artifact containing the pinned modified source trees; its
availability and completeness should be reviewed before publishing the patches.
The Gradle wrapper JAR is an Apache-licensed build tool, not game content.

Three deliberately omitted files are libxmp test fixtures, not OpenAL,
libsndfile or mpg123 source files. The audited libxmp build sets
`WITH_UNIT_TESTS=OFF`, so those particular tests are not run in that build.
This observation narrows the issue but does not decide whether the supplement
meets any legal definition of complete source or whether those tests should be
included in a different authorized source distribution.

## A possible APK distribution

The tested APK combines a proprietary, locally transformed 469e runtime with
separately built shared libraries. OpenAL Soft's inspected source permits GNU
Library GPL v2 or later; libsndfile's inspected source permits LGPL v2.1 or
later; mpg123's included materials identify LGPL v2.1, and individual files and
bundled code require their own check. Do not assume that a single version or
license covers every file or a different build configuration.

For a proposed release, review the applicable LGPL texts and actual artifact:

1. Provide the applicable license texts, copyright/attribution notices and
   prominent notices of changes. Review compiled nested components; the audited
   libsndfile build contains ALAC and GSM 06.10, and the SDL build includes
   HIDAPI and yuv2rgb. Their notices are now included in the app.
2. Provide the exact corresponding library source and build changes by an
   option permitted by the relevant license, and make that source available to
   recipients as required. A pinned commit and patch are useful evidence; the
   package should also be checked against the exact shipped binaries and build
   configuration. `BUILD_EVIDENCE.json` is an evidence aid, not proof of origin.
3. Determine an applicable relinking/replacement route and ensure the terms
   permit modifying the work for the recipient's own use and reverse engineering
   to debug those modifications, where the applicable LGPL section requires it.
   A same-signing-key rebuild and update was demonstrated for the three LGPL
   audio libraries in the emulator. It is not proof that an arbitrary recipient
   can update a separately signed release, or that the tested path meets every
   LGPL condition. A user-signed rebuild may need a separate installation and
   preservation of app-private data.
4. Check all applicable third-party and engine terms together. The current
   OldUnreal notice refers UT distribution to Epic's terms; Epic's EU terms
   contain restrictions concerning modification and reverse engineering. Their
   applicability, exceptions and any separate agreements require rights-holder
   guidance or qualified review. Do not assume a direct conflict or an automatic
   exception for this particular distribution.
5. Only after resolving those points, decide how to provide notices and matching
   source for each published binary. Do not treat a source link, an attribution
   screen, or this checklist as permission to redistribute the engine.

The [GNU LGPL v2.1](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html),
[GNU Library GPL v2](https://www.gnu.org/licenses/old-licenses/lgpl-2.0.html),
[GNU FAQ on static and dynamic linking](https://www.gnu.org/licenses/gpl-faq.html#LGPLStaticVsDynamic),
[OldUnreal notice](https://github.com/OldUnreal/UnrealTournamentPatches/blob/master/LICENSE.md)
and [Epic EU terms](https://legal.epicgames.com/epicgames/tos?eulaKey=epicgames_tos_EU)
are primary references. Consult the exact historical inputs and any separate
agreements as well as current pages.
