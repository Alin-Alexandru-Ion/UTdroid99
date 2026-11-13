#include "../native/controller_bridge.h"
#include <SDL.h>
#include <cassert>
#include <cstdio>
#include <string>

int main() {
    assert(SDL_Init(SDL_INIT_GAMECONTROLLER) == 0);
    int index = SDL_JoystickAttachVirtual(SDL_JOYSTICK_TYPE_UNKNOWN, 6, 16, 0);
    assert(index >= 0);
    char guid[33]; SDL_JoystickGetGUIDString(SDL_JoystickGetDeviceGUID(index), guid, sizeof(guid));
    // Deliberately swap physical axes and buttons: the exposed positions must
    // follow SDL's mapping, not the underlying device's numbering.
    std::string mapping = std::string(guid) +
        ",UT99 virtual test,a:b2,b:b3,back:b4,start:b6,leftx:a2,lefty:a3,rightx:a0,righty:a1,lefttrigger:a4,righttrigger:a5,";
    assert(SDL_GameControllerAddMapping(mapping.c_str()) >= 0);
    SDL_Joystick* joy = SDL_JoystickOpen(index); assert(joy);
    SDL_GameController* pad = SDL_GameControllerOpen(index); assert(pad);
    assert(SDL_GameControllerGetJoystick(pad) == joy);
    auto setAxis = [&](int i, Sint16 value) { assert(SDL_JoystickSetVirtualAxis(joy, i, value) == 0); };
    auto setButton = [&](int i, Uint8 value) { assert(SDL_JoystickSetVirtualButton(joy, i, value) == 0); };
    setAxis(4, -32768); setAxis(5, -32768);
    setAxis(0, 25000); setAxis(2, -18000); setButton(2, 1);
    SDL_JoystickUpdate();
    assert(SDL_GameControllerGetAxis(pad, SDL_CONTROLLER_AXIS_LEFTX) == -18000);
    assert(SDL_GameControllerGetAxis(pad, SDL_CONTROLLER_AXIS_RIGHTX) == 25000);
    assert(SDL_GameControllerGetButton(pad, SDL_CONTROLLER_BUTTON_A));
    assert(!SDL_GameControllerGetButton(pad, SDL_CONTROLLER_BUTTON_B));
    StandardControllerState state;
    auto axis = [&](int i) { return SDL_GameControllerGetAxis(pad, static_cast<SDL_GameControllerAxis>(i)); };
    auto button = [&](int i) { return SDL_GameControllerGetButton(pad, static_cast<SDL_GameControllerButton>(i)); };
    auto raw = [&](int i) { return SDL_JoystickGetButton(joy, i); };
    assert(state.button(0, axis, button, raw));
    assert(!state.button(5, axis, button, raw) && !state.button(15, axis, button, raw));
    setAxis(4, 32767); SDL_JoystickUpdate();
    assert(state.button(5, axis, button, raw) && !state.button(15, axis, button, raw));
    setAxis(4, -32768); setAxis(5, 32767); SDL_JoystickUpdate();
    assert(!state.button(5, axis, button, raw) && state.button(15, axis, button, raw));
    SDL_GameControllerClose(pad); SDL_JoystickClose(joy);
    assert(SDL_JoystickDetachVirtual(index) == 0);
    SDL_Quit();
    std::puts("SDL virtual controller remapping: pass");
}
