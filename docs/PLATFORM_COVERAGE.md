# Platform coverage review

Reviewed the locally available UTP platform status and the official 469e Linux defaults as reference material, without copying UTP project code. UTP reports real iOS/iPadOS device play, audio and controller-at-launch coverage, and separate Simulator evidence for server-browser population and joining. It also reports unresolved physical text entry, controller hot-connect, menu cursor and network-transition issues. These are claims in that project's status, not Android test results.

| Area | Android evidence | Remaining work |
| --- | --- | --- |
| Setup and owned data | Folder picker, all-files access, private runtime and startup-file checks. | Complete package/version validation and physical setup checks; do not bundle game data. |
| Renderer and offline game | User reported phone gameplay; emulator booted the original game. | Long-session performance, lifecycle, compatibility and wider content coverage. |
| Controls | User confirmed Bluetooth buttons and movement with adjusted axis bindings. | Correct dodge direction, live camera sensitivity, hot-plug and device-specific validation. |
| Audio | Emulator initialized OpenAL/OpenSL, opened title music and created an active output track. | Audible music/effects, other formats, route changes and physical phone tests. |
| Multiplayer | Emulator listen server answered a local UDP status query. | Client join, LAN discovery, public browser and phone hosting. |
| Data preservation | Installer creates private configurations only when absent. GUI scale and menu style are now initialized only when missing. | Validate updates, interruption recovery and save/config preservation more broadly. |
| Diagnostics | Manual and crash reports in the selected game's Reports folder. | Physical crash collection and broader privacy/redaction review. |

These bounded checks are not full platform coverage. The experimental runtime still contains compatibility shims, and runtime redistribution permissions remain unresolved.
