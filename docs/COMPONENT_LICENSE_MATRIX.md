# Component and source-review matrix

This matrix records the current debug build. It is not permission from the
rights holders or a legal opinion. Inspect the actual release artifact and
applicable terms before distributing anything.

| Component | Evidence and current position | Remaining review |
| --- | --- | --- |
| UTdroid99 original host, bridge, scripts and docs | Project `LICENSE` selects BSD 3-Clause terms for original contributor work only. | Review file-level authorship and any code derived from other projects. |
| OldUnreal 469e runtime, launcher, ALAudio and other engine modules | The current direct-rehost APK imports the user-provided archive; it contains no engine binaries. The app adapts engine code in memory. | Review applicable terms for the compatibility approach and preserve relevant notices. An older engine-included build is a separate artifact. |
| SDL2 | Pinned source and local changes; upstream zlib, HIDAPI and yuv2rgb notices are included. | Preserve attribution and mark altered versions; compare the source supplement to each actual release. |
| OpenAL Soft | Pinned modified source; its `alc/alc.cpp` specifies GNU Library GPL v2 or later, and its license is included. | Verify the exact corresponding source and the applicable distribution/replacement route for the actual APK. |
| libsndfile and mpg123 | Pinned source and LGPL notices are included; libsndfile has a dated build patch. ALAC and GSM 06.10 notices are included for the audited libsndfile build. Individual files may have additional notices. | Verify exact source, enabled codecs, nested licenses and the distribution/replacement route. |
| libxmp | The pinned project's main notice is MIT-style; additional loader and decompressor notices are included. | Check enabled code and provenance of test inputs and any additional nested notices. Three test fixtures are deliberately omitted from the supplement. |
| Android NDK LLVM/C++ runtimes | NDK 23.2.8568313 compiler/runtime components are linked into local libraries. LLVM and libc++abi notices from that NDK are included. | Verify the notices and source/relinking requirements for the exact linked runtime components. |
| Gradle wrapper | 8.14.3 wrapper JAR and distribution checksums match Gradle's published values; Gradle is Apache-2.0. | Preserve applicable source-distribution notices and recheck when changing wrapper versions. |

The default build excludes FMOD and Cluster. The archive's OpenAL, SDL and
audio source trees do not include the source for OldUnreal's ALAudio module;
the presence of a library source supplement must not be described as complete
source for that proprietary engine module. Source and relinking options are
described in [audio source and rebuild](AUDIO_SOURCE_AND_REBUILD.md). The
[FSF LGPL FAQ](https://www.gnu.org/licenses/gpl-faq.html#LGPLStaticVsDynamic)
describes different considerations for static and dynamic linking; the exact
obligations for this APK and its installation/signing mechanism need review.
The relevant LGPL terms also address the user's ability to modify the library
and debug those modifications; reconcile those requirements with all applicable
engine and distribution terms before claiming a compliant combined release.
See [the LGPL review](LGPL_REVIEW.md) for the separate source-only and APK
distribution questions.
