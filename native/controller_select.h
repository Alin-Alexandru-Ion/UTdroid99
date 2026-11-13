#pragma once

// UT opens one joystick at index zero. If that is an unmapped auxiliary
// joystick, prefer a later SDL-mapped controller. Preserve all other opens.
template <typename IsController>
int chooseControllerIndex(int requested, int count, IsController isController) {
    if (requested != 0 || count <= 0 || isController(0)) return requested;
    for (int index = 1; index < count; ++index)
        if (isController(index)) return index;
    return requested;
}
