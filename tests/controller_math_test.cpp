#include "../native/controller_math.h"
#include <cmath>
#include <cstdio>

int main() {
    const float full = 32767.0f / 65535.0f;
    if (stickDeadzone(0.0f) != 0.0f || stickDeadzone(full * 0.09f) != 0.0f ||
        stickDeadzone(-full * 0.09f) != 0.0f || stickDeadzone(full * 0.10f) != 0.0f) return 1;
    if (std::fabs(stickDeadzone(full) - full) > 0.00001f ||
        std::fabs(stickDeadzone(-full) + full) > 0.00001f) return 2;
    float previous = 0.0f;
    for (int i = 1; i <= 100; ++i) {
        float input = full * i / 100.0f;
        float output = stickDeadzone(input);
        if (output < previous || std::fabs(output + stickDeadzone(-input)) > 0.00001f) return 3;
        previous = output;
    }
    std::puts("stick deadzone: pass");
    return 0;
}
