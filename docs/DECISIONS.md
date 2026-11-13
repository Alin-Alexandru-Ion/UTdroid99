# Decisions

- 2026-11-01: Maintain 469e as the first engine baseline; no older engine, emulation or game recreation substitution.
- Keep official binaries and reference repositories under ignored `research/`. Do not copy UTP code.
- Java Activity plus JNI and an Android native window provide the smallest independent lifecycle and framebuffer experiment. Native pixels are a diagnostic, not a graphics backend decision.
- Treat source build as preferred if authorized source is available; binary rehosting remains a gated hypothesis.
- Use installed NDK 23 for this first local build and explicitly record its API 31 native platform warning. Upgrade before release and redo alignment and runtime tests.
