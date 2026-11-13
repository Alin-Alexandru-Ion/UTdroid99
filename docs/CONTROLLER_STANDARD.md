# SDL2 controller bridge (0.18)

Direct-rehost update, 2026-11-13: the bridge prefers an SDL-mapped controller
when UT requests joystick zero but an unmapped auxiliary device is first. The
four selection cases, deadzone, trigger and dodge sequence tests pass. On an
Android 15 emulator, an injected Xbox-style device was selected as an SDL
GameController; right-stick motion and left-shoulder dodge requests reached
the adapter, trigger changes reached SDL, and Start opened the game menu.
These are simulated inputs, not physical-controller or complete gameplay
verification. A connected controller hides the touch gameplay layout at game
startup; connection changes require restarting the game to switch layouts.

The debug host opens a recognized SDL GameController alongside UT's real SDL
joystick and adapts the pinned 469e SDLDrv's joystick polls to SDL's standard
positions. UT still receives joystick inputs and owns the action bindings.
Devices SDL cannot map retain the previous raw-joystick path. SDL's Android
fallback mapping is inferred from the device's reported controls and is not a
guarantee for every controller or manufacturer. Connect the controller before
launching; reconnect and multiple-controller selection are not yet verified.

| Control | UT input | Initial action |
| --- | --- | --- |
| South / East | Joy1 / Joy2 | Jump / crouch |
| Left / right stick | JoyX, JoyY / JoyZ, JoyR | Move / look |
| Left / right trigger | Joy6 / Joy16 | Alt fire / fire |
| Select / Start | Joy5 / Escape | Scoreboard / menu |
| Left / right bumper | Joy10 / Joy11 | Directional dodge / translocator |
| Right stick click | Joy9 | Translocator |

Left bumper triggers a four-update movement-edge sequence for the game's normal
dodge logic. It chooses the dominant left-stick axis, or forward at center;
diagonal ties select forward/back. It assumes the default JoyX/JoyY movement
bindings and the game requires dodging to be enabled. Dodge remains subject to
game state, dodge timing, player movement and any server rules. The 10% deadzone is applied only to the
four stick axes. The trigger slots support analog triggers with hysteresis and
digital-only trigger buttons. A one-time migration sets Joy2 to Duck and saves
the previous User.ini as User.ini.before-standard-layout-v6; subsequent
changes made in UT are preserved. Version 0.19 reserves Joy10 for the host
dodge and sets Joy11 to translocator once, after backing up User.ini to
User.ini.before-shoulder-layout-v7. Start additionally uses the host's existing
Escape UI event because the game's joystick ShowMenu binding was unreliable.

Validation on 2026-11-09: local SDL 2.0.20 virtual controller with deliberately
shuffled physical axes/buttons passed; native trigger/deadzone tests passed;
an Android API 35 x86_64 emulator with ARM translation installed the bridge and
rendered a match. No controller was attached to that emulator for the game
run, and this version has not been tested on the physical Xiaomi controller.
The game was already reported working on the user's phone, including custom
online servers, before this change.

Version 0.19: automated sequence tests cover each cardinal direction, center,
diagonals, and returning to the physical stick value. In an offline emulator
match an Android virtual Bluetooth Xbox device was opened through SDL's
standard controller API; centered, left and forward shoulder presses reached
the adapter with the expected stick readings. Right shoulder reached Android
and the generated Joy11 binding was checked. These checks do not prove that
the player actually dodged in the requested direction, nor that the
translocator equipped in a mode where it is available. Physical and online
gameplay validation is pending.
