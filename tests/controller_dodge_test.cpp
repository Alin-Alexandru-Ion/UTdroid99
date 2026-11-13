#include "../native/controller_dodge.h"
#include <cassert>
#include <cstdio>

// Model the engine's sign-edge and double-press decisions using the default
// JoyX=+aStrafe and JoyY=-aBaseY bindings.
static int exercise(float stickX, float stickY) {
    ControllerDodge dodge;
    int was = stickY < 0 ? 1 : stickY > 0 ? 2 : stickX < 0 ? 4 : stickX > 0 ? 3 : 0;
    int old = 0, result = 0;
    dodge.press(stickX, stickY);
    for (int frame = 0; frame < 4; ++frame) {
        float x = dodge.axis(228, 0);
        float y = dodge.axis(229, 0);
        int now = y < 0 ? 1 : y > 0 ? 2 : x > 0 ? 3 : x < 0 ? 4 : 0;
        if (now && now != was) {
            if (now == old) result = now;
            else old = now;
        }
        was = now;
    }
    assert(dodge.axis(228, 0) == 0);
    assert(dodge.axis(229, 0) == 0);
    return result;
}

int main() {
    assert(exercise(0, 0) == 1);       // centered: forward
    assert(exercise(0, -.4f) == 1);   // forward
    assert(exercise(0, .4f) == 2);    // backward
    // UT's DODGE_Right applies negative strafe velocity and vice versa.
    assert(exercise(-.4f, 0) == 4);   // physical left -> negative velocity
    assert(exercise(.4f, 0) == 3);    // physical right -> positive velocity
    assert(exercise(.4f, -.2f) == 3); // dominant axis
    assert(exercise(.01f, .01f) == 1); // center deadzone
    ControllerDodge d; d.press(0, -.4f); d.axis(228, 0); d.axis(229, 0);
    d.press(.4f, 0); // repeat presses do not restart an in-progress dodge
    assert(d.pulseX == 0 && d.pulseY < 0 && d.frame == 1);
    std::puts("directional dodge sequence: pass");
}
