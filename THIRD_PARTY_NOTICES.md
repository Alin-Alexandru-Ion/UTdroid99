# UTdroid99 third-party notices and release status

Unreal Tournament was created by Epic Games and Digital Extremes. The 469e
patch is maintained by OldUnreal. UTdroid99 is an independent Android
adaptation and is not endorsed by Epic Games or OldUnreal. The user's game
content is not included.

The current direct-rehost APK does not include OldUnreal engine binaries or
game content. It imports the user's patch and game files and performs temporary
engine-specific adaptations in memory. OldUnreal's notice refers Unreal
Tournament use and distribution to Epic's terms. The app's credits and project
licence do not grant rights to the engine or game content.

The following notices are included in `app/src/debug/assets/licenses/` and
accessible from the app's Credits and licences screen:

| Component | Source / version used | Notice |
| --- | --- | --- |
| OldUnreal UT 469e | [OldUnreal patches](https://github.com/OldUnreal/UnrealTournamentPatches) | OldUnreal.txt, taken from the pinned Linux ARM64 archive |
| SDL2, locally altered | [SDL](https://github.com/libsdl-org/SDL), commit `5d249570393f7a37e037abf22cd6012a4cc56a71` | SDL2.txt; changes in `patches/sdl2/` |
| SDL2 bundled yuv2rgb and HIDAPI | Included in the pinned SDL tree | SDL2-yuv2rgb.txt and SDL2-HIDAPI-*.txt |
| OpenAL Soft, locally altered | [OpenAL Soft](https://github.com/kcat/openal-soft), 1.23.1, commit `d3875f333fb6abe2f39d82caca329414871ae53b` | OpenAL-Soft.txt; patch in `patches/audio/` |
| libxmp, locally altered | [libxmp](https://github.com/libxmp/libxmp), 4.6.2, commit `828ef357943e1fbb13910e7a6fca21987c5c5827` | libxmp.txt, libxmp-ProWizard.txt and libxmp-lhasa.txt; patch in `patches/audio/` |
| libsndfile, locally altered | [libsndfile](https://github.com/libsndfile/libsndfile), 1.2.2, commit `72f6af15e8f85157bd622ed45b979025828b7001` | libsndfile.txt; patch in `patches/audio/` |
| libsndfile ALAC and GSM 06.10 | Compiled from the pinned libsndfile tree | libsndfile-ALAC-attribution.txt, libsndfile-ALAC-Apache2.txt and libsndfile-GSM610.txt |
| mpg123 | [SDL's mpg123 mirror](https://github.com/libsdl-org/mpg123), commit `b6881556a0ee8f417ccc14ded553893b533ffe0c` | mpg123.txt |
| Android NDK LLVM runtime components | [Android NDK](https://developer.android.com/ndk), 23.2.8568313 | Android-NDK-LLVM.txt and Android-NDK-libcxxabi.txt, copied unchanged from that NDK |
| Apache Commons Compress, IO, Codec and Lang | The experimental engine-free build uses Commons Compress 1.27.1, Commons IO 2.16.1, Commons Codec 1.17.1 and Commons Lang 3.16.0. | Apache-2.0.txt and Apache-Commons-*-NOTICE.txt in `app/src/enginefree/assets/licenses/` |

This is a working inventory, not a completed distribution audit. Before
public distribution, verify exact build inputs and all transitive licences,
preserve relevant notices and complete corresponding-source, modification and
relinking requirements where applicable. The locally modified library sources
and build changes can be assembled with `scripts/prepare_audio_source_supplement.py`.
That candidate omits listed test fixtures and is not yet verified to satisfy
all corresponding-source or relinking requirements for a shipped binary.
The default runtime excludes FMOD and its Cluster driver. Older debug APKs
included them, so this inventory does not describe those artifacts. The NDK
notices are a starting point; verify all linked runtime components and their
applicable notices for any proposed binary distribution.
HIDAPI offers several license alternatives; the SDL build may use its BSD-style
option. Its selection notice and all three upstream alternatives are included for
review. Inclusion of the GPL text does not by itself select that alternative.
The source tree also contains the Gradle wrapper JAR, a Gradle component under
the [Apache License 2.0](https://github.com/gradle/gradle/blob/master/LICENSE).
The generated `gradlew` and `gradlew.bat` scripts are also Gradle components,
retain their original Apache-2.0 headers, and are not original UTdroid99 code.
The full Apache license is included as `gradle/LICENSE-APACHE-2.0.txt`.
It is a build tool, not original UTdroid99 code. Its SHA-256 is
`7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172`,
matching Gradle's published [8.14.3 wrapper checksum](https://gradle.org/release-checksums/).
The configured 8.14.3 distribution ZIP checksum also matches that reference.
Review any remaining associated notices for the source distribution.
Do not treat the original-code licence as applying to any third-party work.
The bootstrap applies `patches/sdl2/ut99-android.patch`, which includes upstream
SDL context and modified code. SDL's copyright and license apply to those portions.
