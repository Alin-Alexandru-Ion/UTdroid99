#pragma once
#include <cmath>

// SDLDrv submits the two reserved dodge axes once per input update. Four
// successive updates form a release/press/release/press sequence for the
// game's ordinary dodge code; normal movement uses separate axes.
struct ControllerDodge {
    int frame = -1;
    float pulseX = 0, pulseY = 0;

    void press(float x, float y) {
        if (frame >= 0) return;
        constexpr float dead = (32767.0f / 65535.0f) * .10f;
        constexpr float full = 32767.0f / 65535.0f;
        pulseX = pulseY = 0;
        // For diagonals use the dominant axis, with vertical winning ties.
        if (std::fabs(x) <= dead && std::fabs(y) <= dead) pulseY = -full;
        // The game's DODGE_Left/Right names are reversed relative to the
        // velocity vectors in Dodge(); retain the actual strafe sign.
        else if (std::fabs(x) > std::fabs(y)) pulseX = x < 0 ? -full : full;
        else pulseY = y < 0 ? -full : full;
        frame = 0;
    }

    float axis(int key, float input) {
        if (frame < 0 || (key != 228 && key != 229)) return input;
        const bool pulse = frame == 1 || frame == 3;
        const float value = pulse ? (key == 228 ? pulseX : pulseY) : 0.0f;
        if (key == 229 && ++frame == 4) frame = -1;
        return value;
    }
};
