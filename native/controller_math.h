#pragma once
#include <cmath>

// The 469e SDL driver divides the signed 16-bit value by 65535, yielding
// approximately +/-0.5. Remove 10% around center and rescale the rest.
inline float stickDeadzone(float input) {
    constexpr float full = 32767.0f / 65535.0f;
    constexpr float dead = full * 0.10f;
    const float magnitude = std::fabs(input);
    if (magnitude <= dead) return 0.0f;
    const float scaled = (magnitude - dead) * full / (full - dead);
    return std::copysign(scaled > full ? full : scaled, input);
}
