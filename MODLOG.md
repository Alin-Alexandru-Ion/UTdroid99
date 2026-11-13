# Controller investigation

## 2026-11-06: live joystick camera sensitivity

The catalog-pinned 469e `SDLDrv.so` SHA-256 is `901f610b633b91547bd68db6e5ef4a940ffdb6868a30b215b271df439e5dd767`. Its `USDLViewport::UpdateInput(int)` queries each joystick axis and calls `USDLViewport::CauseInputEvent(int, EInputAction, float)` through a PLT relocation. The six-axis table maps SDL axes 0–5 to input keys 224, 225, 226, 227, 232, 233. Action 4 is axis input. The `CauseInputEvent` relocation for this pinned build is at offset `0xed468`; the host only installs the hook if the slot points to the resolved expected function and preserves its original pointer and page protections.

The settings slider scales only axis events for keys 226 and 227 (JoyZ and JoyR) and saves a separate multiplier. It does not edit the engine's `User.ini`, change button events, or scale JoyX/JoyY. Emulator testing with a four-axis `uinput` joystick confirmed that the engine opened it: with JoyZ input 0.305, 233% yielded 0.711; moving the slider while held to 100% yielded 0.305. The slider showed 233% after a game-process relaunch. This tests the engine input boundary, not physical-device gameplay or every controller axis layout.

## Dodge investigation

The user observed that forward/back double flicks did not dodge and sideways flicks produced the wrong direction. The publicly available PlayerPawn script indicates that dodge edge detection uses the movement axes, but it has not yet been confirmed against the exact shipped 469e game package or reproduced in a controlled four-direction gameplay test. No dodge patch is justified by that reference alone. Joystick dodge direction remains open.

## 2026-11-06: requested controller layout and dead zone

SDL's Android joystick mapping exposes A, Back, Start, left stick, right stick, left shoulder and right shoulder as UT Joy1, Joy5, Joy7, Joy8, Joy9, Joy10 and Joy11 respectively. The existing trigger translation uses Joy6 for LT and Joy16 for RT. The one-time `xbox-layout-v5` migration saves the prior `User.ini`, changes the requested joystick slots and clears JoyU/JoyV to avoid trigger axes controlling the camera. Later user remaps are preserved. Joy8 is left empty pending a real directional-dodge implementation.

The initial `Joy7=ShowMenu` experiment paused a practice match without presenting the original menu. The host now sends Start's normal joystick event plus the same Escape UI event used by the keyboard, and leaves Joy7 unbound by default to avoid a double menu toggle. Start opened the full UT menu in the emulator; Select displayed the scoreboard. Start is a deliberate menu-only keyboard-event exception, while the other controller buttons remain joystick inputs.

The first four joystick axes pass through a 10% per-axis center dead zone in the pinned SDL driver hook, with full travel rescaled to the original range. A standalone math test covers center, threshold, symmetry, monotonicity and endpoints. In an Android 15 emulator with a virtual four-axis controller, a right-stick sample previously measured at 0.305 reached the hook as 0.284 after deadzone scaling. Trigger axis slots are outside the filtered range. Physical controller feel and the remaining buttons' actual gameplay behavior require phone testing.
# 2026-11-10 - Shoulder controls

The 469e PlayerPawn dodge path records movement edges and chooses a dodge on a
second same-direction edge; PlayerWalking.Dodge applies velocity and normal
network movement carries the dodge enum. Internal left/right enum labels have
opposite names to their resulting strafe-velocity signs. SDLDrv's pinned
UpdateInput polls joystick buttons before axes, and axis indices 0/1 map to
input keys 224/225; Joy10 is key 209. The host translates a Joy10 press into
four input updates (neutral/active/neutral/active), selecting a cardinal
direction from the stick with a centered-forward default. This is conditional
on stock movement bindings, enabled dodge timing and normal in-game dodge
rules; frame scheduling and actual gameplay still need physical validation.
Joy11 is migrated once to GetWeapon BotPack.Translocator with a backup.
Android emulator evidence: recognized virtual Xbox controller, logged centered,
left and forward stick values on LB, and received RB; no movement or equipped
weapon outcome was independently measured. Test cleanup is emulator-scoped.

