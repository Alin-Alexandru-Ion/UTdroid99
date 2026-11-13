#include "../native/controller_bridge.h"
#include <cassert>
#include <cstdio>

int main() {
    StandardControllerState state;
    int16_t axes[6]{};
    uint8_t buttons[16]{}, raw[16]{};
    auto axis = [&](int i) { return axes[i]; };
    auto button = [&](int i) { return buttons[i]; };
    auto rawButton = [&](int i) { return raw[i]; };
    auto read = [&](int i) { return state.button(i, axis, button, rawButton); };
    for (int i = 0; i < 15; ++i) {
        if (i == 5) continue;
        buttons[i] = 1;
        assert(read(i) == 1);
        buttons[i] = 0;
        assert(read(i) == 0);
    }
    // Triggers fire in their own slots, with hysteresis and no stick crossover.
    axes[0] = -32768; axes[1] = 32767; axes[2] = -32768; axes[3] = 32767;
    assert(!read(5) && !read(15));
    axes[4] = 14744; assert(!read(5));
    axes[4] = 14745; assert(read(5) && !read(15));
    axes[4] = 9830; assert(read(5));
    axes[4] = 9829; assert(!read(5));
    axes[5] = 32767; assert(read(15) && !read(5));
    axes[5] = 0; assert(!read(15));
    raw[5] = raw[15] = 1; assert(read(5) && read(15));
    raw[5] = raw[15] = 0; assert(!read(5) && !read(15));
    assert(!read(-1) && !read(16));
    std::puts("standard controller mapping: pass");
}
