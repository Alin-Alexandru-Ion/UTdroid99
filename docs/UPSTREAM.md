# Upstream evidence

Observed 2026-11-01; checkouts and archive are isolated in ignored `research/`.

- [OldUnreal patch repository](https://github.com/OldUnreal/UnrealTournamentPatches), HEAD `6c6b76ee8d69d21666b7e959865757fe901531ed`, shallow checkout. HEAD is a reference snapshot, not the release source for the archive.
- [Official v469e release](https://github.com/OldUnreal/UnrealTournamentPatches/releases/tag/v469e), `OldUnreal-UTPatch469e-Linux-arm64.tar.bz2`, 74,481,975 bytes. Official GitHub release API asset digest and locally computed SHA-256 both `4c3978073b12b049c3ffdeb4d275cfc7a2313650f3eb5b94db06fbfee77c3e3b`. The asset metadata snapshot is in ignored `research/release-assets.json`.
- [UTP](https://github.com/chrissotraidis/utp), HEAD `1eee38172b785d0ea95feee1bb0135c40efe1c29`, shallow checkout, read for ideas only. See its [rights](https://github.com/chrissotraidis/utp/blob/main/RIGHTS_AND_LICENSES.md) and [status](https://github.com/chrissotraidis/utp/blob/main/docs/STATUS.md).

The archive contains `SystemARM64/`, data packages in `System/`, and 20 regular ELF files. It contains symlinks `ut-bin` and `ucc-bin` pointing to the corresponding ARM64 executables. Both executables request `/lib/ld-linux-aarch64.so.1`. The current upstream Linux README describes copying Music, Sounds, Maps and non-overwriting Textures from a game distribution, warns against copying the original System directory wholesale, and separately describes bonus-pack `.u`/`.int` files. Confirm these rules against the exact data version before implementing import.

The static report `research/audit-469e.json` contains every readelf result, dynamic symbols, versions, relocation types, PT_TLS and dependency basename candidates. It does not follow archive symlinks or analyze runtime-loaded modules. No Linux x86-64 desktop archive was downloaded, so an archive-to-archive desktop comparison remains unmeasured.

UTP uses a macOS Mach-O conversion/signing pipeline, UIKit and Metal; those mechanisms are specific to Apple and do not imply an ELF loader. Transferable ideas include immutable pinned input, explicit host/engine boundary, staged imports, lifecycle diagnostics, separated build and device gates, and proving original loop entry. No UTP code is copied here.

## Locally installed reference content

On 2026-11-02 an existing GOG UT GOTY installation was located on the development machine. Its configuration specifies `LocalMap=CityIntro.unr`, and the map exists at `Maps/CityIntro.unr` with SHA-256 `086bfc3bc21e358b24c21bf4291f1107e5770f6c7d7e6de2ed2615892a02b2df`. A private research copy of its System, Maps, Music, Sounds and Textures directories was created under ignored `research/pc-reference`; the copied map hash matches the installed file. The copy's config was changed to windowed mode; the installed game was not modified. This input is not packaged or authorized for redistribution merely because it exists locally.

Two attempts to launch the copied Windows executable with `CityIntro.unr -windowed` did not yield a captured frame; its newly created log remained empty and the processes had exited by the subsequent inspection. The second was observed running after eight seconds, but no successful map load or reason for exit was established. This desktop experiment is independent of Android and does not demonstrate engine startup there.
