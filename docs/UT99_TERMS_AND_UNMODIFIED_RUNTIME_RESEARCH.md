# UT99 terms and an unmodified-runtime investigation

Research date: 2026-11-12. This records accessible evidence, not legal advice or a complete inventory of every UT edition, contract or jurisdiction. No contact was made with Epic or OldUnreal. The agreement applying to a particular player's copy must be checked against that copy and its acquisition channel.

## Sources and what they say

1. [UT Game of the Year manual, Infogrames North America edition](https://www.mogelpower.de/manuals/Unreal_Tournament_Game_of_the_Year_Edition_Manual_Englisch.pdf), p. 1, is a third-party-hosted reproduction of a contemporary manual, not an Epic-hosted authenticated copy. Its EULA restricts modification, decompilation, reverse engineering and redistribution. This is evidence of an actual restriction for that edition, not proof it governs all copies or is enforceable in every jurisdiction. A different [Totally Unreal compilation transcription](https://wiki.beyondunreal.com/Unreal_Tournament_end-user_license_agreement) describes a different Infogrames agreement. Verify against the actual user's edition; the local game folder inspected did not contain a UT EULA or manual.
2. [OldUnreal UT99 patch page](https://www.oldunreal.com/downloads/unrealtournament/oldunreal-patches-for-unrealtournament-version-469/) says maintenance follows a 2019 agreement with Epic; it discusses UnrealScript compatibility and an SDK for native mods. It does not publish that agreement or grant all developers its rights.
3. [OldUnreal patch license file](https://github.com/OldUnreal/UnrealTournamentPatches/blob/master/LICENSE.md) identifies third-party components and their licenses. Those grants apply to the identified components, not automatically to the UT engine. Keep the actual license and source obligations of each distributed component.
4. [Epic Games Store EULA](https://legal.epicgames.com/store/eula) restricts modification and reverse engineering for software it covers. Whether it governs a particular UT99 copy is not established. Do not substitute Epic's current general store terms for a user's historical game license.
5. [EU Software Directive 2009/24/EC](https://eur-lex.europa.eu/eli/dir/2009/24/oj/eng) Articles 5 and 6 address certain lawful use, observation and interoperability-related decompilation, subject to conditions. Article 9 addresses conflicting contractual provisions in specified areas. Application depends on national implementation, actual conduct and facts. Interoperability is not a blanket permission to distribute or modify anything.
6. Epic's [Unreal Tournament EULA](https://cdn1.epicgames.com/eulatracking-download/ut/en/v1/r1/b12fd5dc1b63b138cf326db7f3cfb163.pdf) found in searches concerns a later Unreal Tournament project with an editor and marketplace. It is not evidence of the terms for UT99. Similarly UT2004 and modern Unreal Engine agreements cannot simply be applied to UT99.

Correction to earlier discussion: it was too broad to suggest that no available legal material mentions the relevant conduct. The contemporary GOTY manual identified above expressly contains restrictions; what applies to this user's copy and the effects of law are unresolved.

## What a no-file-edit route needs to replace

Current engine-free import still transforms a local launcher and three modules; the existing host also redirects calls in process. The selected original files remain intact, but private transformed copies are generated. Merely renaming those transformations as a wrapper does not remove them.

| Present adaptation | Candidate alternative | Status and critical risk |
| --- | --- | --- |
| Launcher PIE flag change | Run the original executable through a Linux-compatible execution environment; or use a separate launcher and exported, supported engine APIs. | Not demonstrated. Android app process, graphics integration and platform restrictions make the first option substantial. The pinned launcher does not export main as a dynamic symbol. |
| Launcher TLS instruction and relocation rewrite | A Linux-compatible loader and AArch64 TLS environment matching the binary's ABI. | Not demonstrated. This is the main technical blocker; a regular SDL wrapper does not solve TLS by itself. |
| GNU-unique binding rewrites in Render, IpDrv and ALAudio | Loader with appropriate ELF GNU-unique symbol support and Linux ABI compatibility. | Plausible but untested with the original modules on Android. |
| Runtime Core and SDLDrv slot hooks | Supported plugin/API where available or symbol interposition at the loader boundary; SDL game controller adapter. | May still affect runtime behavior; loader resolution and symbol visibility need testing. Relocation lookup already prototyped removes hardcoded slots, not hooks. |
| UT99 assets and patch payload | User supplies official files and private working storage. | Already implemented; confirm unchanged hashes for every engine input and output in a new mode. |

An alternate independently developed compatible engine would remove reliance on the existing engine binary but is a major project. Native UnrealScript mods cannot by themselves provide the missing platform loader before the engine starts. No approach here is automatically legally cleared merely because files remain byte-identical; copying, reverse engineering, runtime changes, contracts and third-party code still require separate review.

## Acceptance gate for a future experiment

Run as a separate experimental mode, preserving the working engine-free path. First produce a read-only inventory of original ELF program headers, TLS, symbol bindings and dependencies. Then demonstrate a loader experiment on isolated test fixtures before handling game files. Any engine test must verify SHA-256 for every input before and after, report whether memory instructions or relocation slots were modified, capture loader failures, and progress from launch to menu and actual gameplay. Run on physical ARM64 Android before claiming the game works. Review new loader dependencies and licenses before redistribution. Do not state that no-edit mode works until these tests pass.

## Still unresolved

Applicable agreement for each acquisition source; rights in any version-specific executable-derived material; effects of runtime interception; exact permissions for SDK headers; complete and matching LGPL source, notice and replacement compliance; full provenance of the source candidate; and physical device validation. Any release decision should distinguish source-only publication from an APK containing LGPL libraries. Date-stamped source links are evidence, not permission or a guarantee against a claim.
