#pragma once
#include <cstdint>

// UT's first 16 joystick buttons remain assignable in User.ini. SDL's Guide
// slot is unavailable on Android, so slots 6 and 16 carry the two triggers.
struct StandardControllerState {
    bool triggerHeld[2]{};

    template <typename Axis, typename Button, typename RawButton>
    uint8_t button(int index, Axis axis, Button button, RawButton rawButton) {
        if (index == 5 || index == 15) {
            const int side = index == 5 ? 0 : 1;
            const int value = axis(4 + side);
            // SDL game controller trigger axes are 0..32767. The raw button
            // also supports devices that expose a digital-only Android trigger.
            triggerHeld[side] = rawButton(index) || value >= (triggerHeld[side] ? 9830 : 14745);
            return triggerHeld[side] ? 1 : 0;
        }
        return (index >= 0 && index < 15) ? (button(index) ? 1 : 0) : 0;
    }
};
