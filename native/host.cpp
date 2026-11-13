#include <android/native_window_jni.h>
#include <android/log.h>
#include <android/dlext.h>
#include <cstdint>
#include <dlfcn.h>
#include <thread>
#include <string>
#include <map>
#include <mutex>
#include <cerrno>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <pthread.h>
#include <time.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <sys/mman.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/syscall.h>
#include <linux/memfd.h>
#include "../experiments/libc-probe/stat_probe.h"
#include "controller_math.h"
#include "controller_bridge.h"
#include "controller_dodge.h"
#include "controller_select.h"

// Isolated fixture for the app-contained loader; the source library is never changed.
extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeMemoryLoader(JNIEnv* env, jclass, jstring sourcePath) {
    const char* path = env->GetStringUTFChars(sourcePath, nullptr);
    if (!path) return env->NewStringUTF("Memory-loader probe: invalid path");
    int src = open(path, O_RDONLY | O_CLOEXEC);
    env->ReleaseStringUTFChars(sourcePath, path);
    if (src < 0) return env->NewStringUTF((std::string("Memory-loader fixture open failed: ") + strerror(errno)).c_str());
    int fd = static_cast<int>(syscall(__NR_memfd_create, "ut-memory-fixture", MFD_CLOEXEC | MFD_ALLOW_SEALING));
    if (fd < 0) { int e = errno; close(src); return env->NewStringUTF((std::string("memfd_create failed: ") + strerror(e)).c_str()); }
    char buffer[65536]; ssize_t n; bool ok = true;
    while ((n = read(src, buffer, sizeof(buffer))) > 0) {
        ssize_t offset = 0;
        while (offset < n) { ssize_t wrote = write(fd, buffer + offset, n - offset); if (wrote <= 0) { ok = false; break; } offset += wrote; }
        if (!ok) break;
    }
    if (n < 0) ok = false;
    close(src);
    if (!ok || fcntl(fd, F_ADD_SEALS, F_SEAL_SEAL | F_SEAL_SHRINK | F_SEAL_GROW | F_SEAL_WRITE) < 0) {
        int e = errno; close(fd); return env->NewStringUTF((std::string("Memory-loader staging or sealing failed: ") + strerror(e)).c_str());
    }
    android_dlextinfo info{}; info.flags = ANDROID_DLEXT_USE_LIBRARY_FD; info.library_fd = fd;
    void* handle = android_dlopen_ext("ut-memory-fixture.so", RTLD_NOW | RTLD_LOCAL, &info);
    if (!handle) { std::string error = dlerror(); close(fd); return env->NewStringUTF(("Memory-loader dlopen failed: " + error).c_str()); }
    auto entry = reinterpret_cast<int (*)(int)>(dlsym(handle, "ut_memory_fixture"));
    int value = entry ? entry(11) : -1;
    dlclose(handle); close(fd);
    std::string result = std::string("Memory-loader fixture ") + (value == 40 ? "PASS" : "FAIL") + ": value=" + std::to_string(value);
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

// Core.so is pinned to the verified 469e build in RuntimeCatalog. Android's
// process-global libc++ can win symbol resolution before our versioned bridge,
// so redirect this one import to the bridge's guarded compatibility wrapper.
static bool patchInputSlot(void** slot, void* replacement, void** previous) {
    const long size = sysconf(_SC_PAGESIZE);
    if (size <= 0) return false;
    void* page = reinterpret_cast<void*>(reinterpret_cast<uintptr_t>(slot) & ~(static_cast<uintptr_t>(size) - 1));
    FILE* maps = fopen("/proc/self/maps", "r");
    if (!maps) return false;
    char line[1024], permissions[5]; unsigned long long begin, end;
    int protection = -1;
    while (fgets(line, sizeof(line), maps)) {
        if (sscanf(line, "%llx-%llx %4s", &begin, &end, permissions) == 3 &&
            reinterpret_cast<uintptr_t>(slot) >= begin && reinterpret_cast<uintptr_t>(slot) < end) {
            protection = (permissions[0] == 'r' ? PROT_READ : 0) |
                (permissions[1] == 'w' ? PROT_WRITE : 0) | (permissions[2] == 'x' ? PROT_EXEC : 0);
            break;
        }
    }
    fclose(maps);
    if (protection < 0 || mprotect(page, static_cast<size_t>(size), protection | PROT_WRITE)) return false;
    if (previous) *previous = *slot;
    *slot = replacement;
    return mprotect(page, static_cast<size_t>(size), protection) == 0;
}

static bool installCoreConfigCast(void* core, void* bridge) {
    Dl_info info{};
    void* symbol = dlsym(core, "_ZN18FLocalizeFileCache17GetAllLocaleFilesER6TArrayI7FStringEPKw");
    void* replacement = dlsym(bridge, "__dynamic_cast");
    if (!symbol || !replacement || !dladdr(symbol, &info)) return false;
    auto slot = reinterpret_cast<void**>(reinterpret_cast<uintptr_t>(info.dli_fbase) + 0x159af8);
    return patchInputSlot(slot, replacement, nullptr);
}

// Pinned 469e SDLDrv sends joystick axes through this PLT slot. Keep the
// game's bindings intact and scale only the two right-stick axis events.
using InputEventFn = int (*)(void*, int, int, float);
static InputEventFn originalInputEvent = nullptr;
static std::atomic<float> cameraSensitivity{1.0f};
static std::atomic<bool> cameraSensitivityReady{false};
static std::atomic<bool> cameraTracePending{true};
static ControllerDodge controllerDodge;
static float lastStickX = 0, lastStickY = 0;
static bool readStandardLeftStick(float& x, float& y);
static int scaledInputEvent(void* viewport, int key, int action, float value) {
    if (key == 209 && action == 1) { // Joy10: standard left shoulder.
        float x = lastStickX, y = lastStickY;
        readStandardLeftStick(x, y);
        controllerDodge.press(x, y);
        __android_log_print(ANDROID_LOG_INFO, "UT99Host", "Dodge requested stick=(%.3f,%.3f)", x, y);
    }
    if (action == 4 && key == 224) lastStickX = value;
    if (action == 4 && key == 225) lastStickY = value;
    if (action == 4 && key >= 224 && key <= 227)
        value = stickDeadzone(value);
    if (action == 4 && (key == 228 || key == 229))
        value = controllerDodge.axis(key, value);
    if (action == 4 && (key == 226 || key == 227)) {
        float input = value;
        float scale = cameraSensitivity.load(std::memory_order_relaxed);
        value *= scale;
        if ((input > 0.05f || input < -0.05f) && cameraTracePending.exchange(false))
            __android_log_print(ANDROID_LOG_INFO, "UT99Host", "Camera axis key=%d input=%.3f multiplier=%.2f output=%.3f", key, input, scale, value);
    }
    return originalInputEvent(viewport, key, action, value);
}
static bool installCameraSensitivity(void* driver) {
    constexpr uintptr_t slotOffset = 0xed468;
    void* symbol = dlsym(driver, "_ZN12USDLViewport15CauseInputEventEi12EInputActionf");
    Dl_info info{};
    if (!symbol || !dladdr(symbol, &info)) return false;
    auto slot = reinterpret_cast<void**>(reinterpret_cast<uintptr_t>(info.dli_fbase) + slotOffset);
    if (*slot != symbol) return false;
    originalInputEvent = reinterpret_cast<InputEventFn>(symbol);
    if (!patchInputSlot(slot, reinterpret_cast<void*>(&scaledInputEvent), nullptr)) return false;
    cameraSensitivityReady.store(true, std::memory_order_release);
    return true;
}

// Adapt only the pinned SDLDrv's joystick imports. The real SDL joystick is
// still owned by SDL; the game receives standard GameController positions as
// its ordinary, configurable Joy inputs.
struct ControllerApi {
    void* (*openJoystick)(int) = nullptr;
    void (*closeJoystick)(void*) = nullptr;
    int (*numAxes)(void*) = nullptr;
    int (*numButtons)(void*) = nullptr;
    int16_t (*axis)(void*, int) = nullptr;
    uint8_t (*button)(void*, int) = nullptr;
    int (*init)(uint32_t) = nullptr;
    int (*numJoysticks)() = nullptr;
    int (*isController)(int) = nullptr;
    void* (*openController)(int) = nullptr;
    void (*closeController)(void*) = nullptr;
    void* (*controllerJoystick)(void*) = nullptr;
    int16_t (*controllerAxis)(void*, int) = nullptr;
    uint8_t (*controllerButton)(void*, int) = nullptr;
    const char* (*controllerName)(void*) = nullptr;
} static controllerApi;
static void* standardJoystick = nullptr;
static void* standardController = nullptr;
static StandardControllerState standardState;
static int touchJoystickIndex = -1;
static void* touchJoystick = nullptr;
static std::atomic<int> touchAxes[4]{};
static std::atomic<uint32_t> touchButtons{0};
static std::atomic<bool> touchJoystickReady{false};

extern "C" JNIEXPORT void JNICALL
Java_org_ut99android_host_ExperimentalSDLActivity_setTouchAxis(JNIEnv*, jclass, jint axis, jfloat value) {
    if (axis < 0 || axis >= 4) return;
    if (value > 1) value = 1; if (value < -1) value = -1;
    touchAxes[axis].store(static_cast<int>(value * 32767), std::memory_order_relaxed);
}
extern "C" JNIEXPORT void JNICALL
Java_org_ut99android_host_ExperimentalSDLActivity_setTouchButton(JNIEnv*, jclass, jint button, jboolean down) {
    if (button < 0 || button >= 16) return;
    uint32_t mask = uint32_t(1) << button;
    if (down) touchButtons.fetch_or(mask, std::memory_order_relaxed);
    else touchButtons.fetch_and(~mask, std::memory_order_relaxed);
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_ut99android_host_ExperimentalSDLActivity_isTouchJoystickReady(JNIEnv*, jclass) {
    return touchJoystickReady.load(std::memory_order_acquire) ? JNI_TRUE : JNI_FALSE;
}
static bool attachTouchJoystick(void* sdl) {
    auto init = reinterpret_cast<int(*)(uint32_t)>(dlsym(sdl, "SDL_InitSubSystem"));
    auto attach = reinterpret_cast<int(*)(int,int,int,int)>(dlsym(sdl, "SDL_JoystickAttachVirtual"));
    if (!init || !attach || init(0x2000) != 0) return false;
    touchJoystickIndex = attach(0, 4, 16, 0); // SDL_JOYSTICK_TYPE_UNKNOWN
    if (touchJoystickIndex < 0) return false;
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "Touch joystick attached at SDL index %d", touchJoystickIndex);
    return true;
}

static bool readStandardLeftStick(float& x, float& y) {
    if (touchJoystick) {
        x = touchAxes[0].load(std::memory_order_relaxed) / 65535.0f;
        y = touchAxes[1].load(std::memory_order_relaxed) / 65535.0f;
        return true;
    }
    if (!standardController) return false;
    x = controllerApi.controllerAxis(standardController, 0) / 65535.0f;
    y = controllerApi.controllerAxis(standardController, 1) / 65535.0f;
    return true;
}

static void* controllerOpen(int index) {
    const bool openingTouch = touchJoystickIndex >= 0 && index == 0;
    int selected = openingTouch ? touchJoystickIndex : index;
    if (!openingTouch && index == 0 && controllerApi.init(0x2000) == 0) {
        selected = chooseControllerIndex(index, controllerApi.numJoysticks(),
                [](int candidate) { return controllerApi.isController(candidate) != 0; });
        if (selected != index) __android_log_print(ANDROID_LOG_INFO, "UT99Host", "Selected SDL-mapped controller %d over unmapped joystick %d", selected, index);
    }
    void* joystick = controllerApi.openJoystick(selected);
    if (joystick && touchJoystickIndex >= 0 && index == 0) {
        touchJoystick = joystick;
        touchJoystickReady.store(true, std::memory_order_release);
        __android_log_print(ANDROID_LOG_INFO, "UT99Host", "Touch joystick opened by UT");
        return joystick;
    }
    if (!joystick || standardController) return joystick;
    // SDL_INIT_GAMECONTROLLER also initializes its joystick dependency.
    if (controllerApi.init(0x2000) != 0 || !controllerApi.isController(selected)) {
        __android_log_print(ANDROID_LOG_WARN, "UT99Host", "Controller %d has no SDL mapping; retaining raw joystick input", selected);
        return joystick;
    }
    void* controller = controllerApi.openController(selected);
    if (!controller || controllerApi.controllerJoystick(controller) != joystick) {
        if (controller) controllerApi.closeController(controller);
        return joystick;
    }
    standardJoystick = joystick;
    standardController = controller;
    standardState = {};
    const char* name = controllerApi.controllerName(controller);
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "SDL standardized controller active: %s", name ? name : "unknown");
    return joystick;
}
static void controllerClose(void* joystick) {
    if (joystick && joystick == touchJoystick) {
        touchJoystickReady.store(false, std::memory_order_release);
        touchJoystick = nullptr;
        touchButtons.store(0, std::memory_order_relaxed);
        for (auto& axis : touchAxes) axis.store(0, std::memory_order_relaxed);
        controllerDodge = {}; lastStickX = lastStickY = 0;
    }
    if (joystick && joystick == standardJoystick) {
        controllerApi.closeController(standardController);
        standardController = nullptr;
        standardJoystick = nullptr;
        standardState = {};
        controllerDodge = {};
        lastStickX = lastStickY = 0;
    }
    controllerApi.closeJoystick(joystick);
}
static int controllerNumAxes(void* joystick) {
    // Reserve two always-present axes for button-triggered dodge pulses.
    return joystick ? 6 : 0;
}
static int controllerNumButtons(void* joystick) {
    if (joystick && joystick == touchJoystick) return 16;
    return joystick == standardJoystick && standardController ? 16 : controllerApi.numButtons(joystick);
}
static int16_t controllerGetAxis(void* joystick, int axis) {
    if (axis == 4 || axis == 5) return 0; // Physical triggers are exposed as buttons.
    if (joystick && joystick == touchJoystick) return axis >= 0 && axis < 4 ? static_cast<int16_t>(touchAxes[axis].load(std::memory_order_relaxed)) : 0;
    if (joystick != standardJoystick || !standardController) return controllerApi.axis(joystick, axis);
    return axis >= 0 && axis < 6 ? controllerApi.controllerAxis(standardController, axis) : 0;
}
static uint8_t controllerGetButton(void* joystick, int button) {
    // Joy8 is reserved internally for the strafe modifier used by the safe
    // movement axes. The left-stick click is therefore not exposed separately.
    if (joystick && button == 7) return 1;
    if (joystick && joystick == touchJoystick) return button >= 0 && button < 16 && (touchButtons.load(std::memory_order_relaxed) & (uint32_t(1) << button)) ? 1 : 0;
    if (joystick != standardJoystick || !standardController) return controllerApi.button(joystick, button);
    return standardState.button(button,
        [](int i) { return controllerApi.controllerAxis(standardController, i); },
        [](int i) { return controllerApi.controllerButton(standardController, i); },
        [joystick](int i) { return controllerApi.button(joystick, i); });
}

static bool installStandardController(void* driver, void* sdl) {
    void* marker = dlsym(driver, "_ZN12USDLViewport15CauseInputEventEi12EInputActionf");
    Dl_info info{};
    if (!marker || !dladdr(marker, &info)) return false;
    struct Hook { uintptr_t offset; const char* name; void* replacement; void** original; } hooks[] = {
        {0xed240, "SDL_JoystickOpen", reinterpret_cast<void*>(&controllerOpen), reinterpret_cast<void**>(&controllerApi.openJoystick)},
        {0xed238, "SDL_JoystickClose", reinterpret_cast<void*>(&controllerClose), reinterpret_cast<void**>(&controllerApi.closeJoystick)},
        {0xed250, "SDL_JoystickNumAxes", reinterpret_cast<void*>(&controllerNumAxes), reinterpret_cast<void**>(&controllerApi.numAxes)},
        {0xed678, "SDL_JoystickNumButtons", reinterpret_cast<void*>(&controllerNumButtons), reinterpret_cast<void**>(&controllerApi.numButtons)},
        {0xed5e0, "SDL_JoystickGetAxis", reinterpret_cast<void*>(&controllerGetAxis), reinterpret_cast<void**>(&controllerApi.axis)},
        {0xed978, "SDL_JoystickGetButton", reinterpret_cast<void*>(&controllerGetButton), reinterpret_cast<void**>(&controllerApi.button)},
    };
#define SDL_BIND(member, symbol) controllerApi.member = reinterpret_cast<decltype(controllerApi.member)>(dlsym(sdl, symbol))
    SDL_BIND(init, "SDL_InitSubSystem");
    SDL_BIND(numJoysticks, "SDL_NumJoysticks");
    SDL_BIND(isController, "SDL_IsGameController");
    SDL_BIND(openController, "SDL_GameControllerOpen");
    SDL_BIND(closeController, "SDL_GameControllerClose");
    SDL_BIND(controllerJoystick, "SDL_GameControllerGetJoystick");
    SDL_BIND(controllerAxis, "SDL_GameControllerGetAxis");
    SDL_BIND(controllerButton, "SDL_GameControllerGetButton");
    SDL_BIND(controllerName, "SDL_GameControllerName");
#undef SDL_BIND
    if (!controllerApi.init || !controllerApi.numJoysticks || !controllerApi.isController || !controllerApi.openController ||
        !controllerApi.closeController || !controllerApi.controllerJoystick || !controllerApi.controllerAxis ||
        !controllerApi.controllerButton || !controllerApi.controllerName) return false;
    uintptr_t base = reinterpret_cast<uintptr_t>(info.dli_fbase);
    for (auto& hook : hooks) {
        void* symbol = dlsym(sdl, hook.name);
        if (!symbol || *reinterpret_cast<void**>(base + hook.offset) != symbol) return false;
        *hook.original = symbol;
    }
    size_t installed = 0;
    for (auto& hook : hooks) {
        if (!patchInputSlot(reinterpret_cast<void**>(base + hook.offset), hook.replacement, nullptr)) {
            // patchInputSlot can fail while restoring page protection after
            // the write, so restore this slot as well as the earlier ones.
            patchInputSlot(reinterpret_cast<void**>(base + hook.offset), *hook.original, nullptr);
            for (size_t i = 0; i < installed; ++i)
                patchInputSlot(reinterpret_cast<void**>(base + hooks[i].offset), *hooks[i].original, nullptr);
            return false;
        }
        ++installed;
    }
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "SDL standardized controller bridge installed");
    return true;
}

extern "C" JNIEXPORT void JNICALL
Java_org_ut99android_host_ExperimentalSDLActivity_setCameraSensitivity(JNIEnv*, jclass, jfloat multiplier) {
    if (multiplier >= 0.25f && multiplier <= 4.0f) {
        cameraSensitivity.store(multiplier, std::memory_order_relaxed);
        cameraTracePending.store(true, std::memory_order_relaxed);
    }
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_ut99android_host_ExperimentalSDLActivity_isCameraSensitivityReady(JNIEnv*, jclass) {
    return cameraSensitivityReady.load(std::memory_order_acquire) ? JNI_TRUE : JNI_FALSE;
}




static std::map<std::string, int> adaptedImages;
static std::mutex adaptedImagesMutex;

extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_registerAdaptedImage(JNIEnv* env, jclass, jstring name, jbyteArray image) {
    if (!name || !image) return env->NewStringUTF("Missing adapted image");
    const char* utf = env->GetStringUTFChars(name, nullptr);
    if (!utf) return env->NewStringUTF("Invalid adapted image name");
    std::string key(utf); env->ReleaseStringUTFChars(name, utf);
    if (key != "ut-launch.so" && key != "Render.so" && key != "IpDrv.so" && key != "ALAudio.so")
        return env->NewStringUTF("Unexpected adapted module name");
    jsize size = env->GetArrayLength(image);
    if (size <= 0 || size > 32 * 1024 * 1024) return env->NewStringUTF("Invalid adapted image size");
    int fd = static_cast<int>(syscall(__NR_memfd_create, "ut99-transient-image", MFD_CLOEXEC | MFD_ALLOW_SEALING));
    if (fd < 0) return env->NewStringUTF((std::string("memfd failed: ") + strerror(errno)).c_str());
    jbyte* bytes = env->GetByteArrayElements(image, nullptr);
    if (!bytes) { close(fd); return env->NewStringUTF("Image allocation failed"); }
    ssize_t offset = 0; bool ok = true;
    while (offset < size) { ssize_t n = write(fd, bytes + offset, size - offset); if (n <= 0) { ok = false; break; } offset += n; }
    env->ReleaseByteArrayElements(image, bytes, JNI_ABORT);
    if (!ok || fcntl(fd, F_ADD_SEALS, F_SEAL_SEAL | F_SEAL_SHRINK | F_SEAL_GROW | F_SEAL_WRITE) < 0) {
        std::string error = std::string("Image seal failed: ") + strerror(errno); close(fd); return env->NewStringUTF(error.c_str());
    }
    std::lock_guard<std::mutex> guard(adaptedImagesMutex);
    auto old = adaptedImages.find(key); if (old != adaptedImages.end()) close(old->second);
    adaptedImages[key] = fd;
    return nullptr;
}

static void* loadInDir(const std::string& dir, const char* name, std::string& result) {
    int fd = -1;
    { std::lock_guard<std::mutex> guard(adaptedImagesMutex); auto it = adaptedImages.find(name); if (it != adaptedImages.end()) fd = it->second; }
    android_dlextinfo info{};
    if (fd >= 0) { info.flags = ANDROID_DLEXT_USE_LIBRARY_FD; info.library_fd = fd; }
    void* handle = fd >= 0 ? android_dlopen_ext(name, RTLD_NOW | RTLD_LOCAL, &info)
                           : dlopen((dir + "/" + name).c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!handle) { const char* e = dlerror(); result = std::string(name) + " load failed: " + (e ? e : "unknown"); }
    return handle;
}

// Experimental direct Bionic load of unmodified modules. Constructors may run;
// this load-only probe never calls the launcher or changes input files.
extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeDirectOriginal(JNIEnv* env, jclass, jstring directory) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Invalid directory");
    std::string dir = std::string(utf) + "/core-load";
    env->ReleaseStringUTFChars(directory, utf);
    std::string error;
    const char* names[] = {"libutlibcprobe.so", "libc.so.6", "libSDL2-2.0.so.0", "libdl.so.2",
        "libstdc++.so.6", "libm.so.6", "libgcc_s.so.1", "ld-linux-aarch64.so.1",
        "Core.so", "Engine.so", "libpthread.so.0", "ut-bin-arm64"};
    for (const char* name : names) {
        __android_log_print(ANDROID_LOG_INFO, "UT99Host", "Direct original probe loading %s", name);
        if (!loadInDir(dir, name, error)) {
            std::string result = std::string("Direct original probe stopped at ") + name + ": " + error;
            __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
            return env->NewStringUTF(result.c_str());
        }
    }
    const char* result = "Direct original load succeeded; launcher was not started";
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result);
    return env->NewStringUTF(result);
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeRegisteredModules(JNIEnv* env, jclass, jstring directory) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Invalid directory");
    std::string dir = std::string(utf) + "/core-load"; env->ReleaseStringUTFChars(directory, utf);
    const char* names[] = {"libutlibcprobe.so", "libc.so.6", "libSDL2-2.0.so.0", "libdl.so.2",
        "libstdc++.so.6", "libm.so.6", "libgcc_s.so.1", "ld-linux-aarch64.so.1", "Core.so", "Engine.so",
        "libpthread.so.0", "Render.so", "IpDrv.so", "libopenal.so.1", "libxmp.so.4", "libmpg123.so", "libsndfile.so.1", "ALAudio.so", "ut-launch.so"};
    std::string error; void* libc = nullptr; void* core = nullptr;
    for (const char* name : names) {
        void* handle = loadInDir(dir, name, error);
        if (!handle) { std::string result = std::string("Registered module probe stopped at ") + name + ": " + error;
            __android_log_print(ANDROID_LOG_ERROR, "UT99Host", "%s", result.c_str()); return env->NewStringUTF(result.c_str()); }
        if (!strcmp(name, "libc.so.6")) libc = handle;
        if (!strcmp(name, "Core.so")) { core = handle; auto setCore = reinterpret_cast<void (*)(void*)>(dlsym(libc, "ut_set_core_tls_module"));
            if (!setCore) return env->NewStringUTF("Core TLS bridge missing"); setCore(core); }
        __android_log_print(ANDROID_LOG_INFO, "UT99Host", "Registered module probe loaded %s", name);
    }
    return env->NewStringUTF("Registered modules loaded; launcher main not called");
}

// Pinned diagnostic: Android receives a temporary adapted launcher image from
// Java; the original file remains untouched. Never enter its code here.
extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeDirectMemory(JNIEnv* env, jclass, jstring directory, jbyteArray image) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf || !image) { if (utf) env->ReleaseStringUTFChars(directory, utf); return env->NewStringUTF("Invalid memory-loader input"); }
    std::string dir = std::string(utf) + "/core-load";
    env->ReleaseStringUTFChars(directory, utf);
    std::string error; void* libc = nullptr; void* core = nullptr;
    const char* names[] = {"libutlibcprobe.so", "libc.so.6", "libSDL2-2.0.so.0", "libdl.so.2", "libstdc++.so.6",
        "libm.so.6", "libgcc_s.so.1", "ld-linux-aarch64.so.1", "Core.so", "Engine.so", "libpthread.so.0"};
    for (const char* name : names) {
        void* handle = loadInDir(dir, name, error);
        if (!handle) return env->NewStringUTF(error.c_str());
        if (!strcmp(name, "libc.so.6")) libc = handle;
        if (!strcmp(name, "Core.so")) core = handle;
    }
    auto setCore = libc ? reinterpret_cast<void (*)(void*)>(dlsym(libc, "ut_set_core_tls_module")) : nullptr;
    if (!setCore || !core) return env->NewStringUTF("Core TLS bridge missing");
    setCore(core);
    int fd = static_cast<int>(syscall(__NR_memfd_create, "ut99-launcher-adapted", MFD_CLOEXEC | MFD_ALLOW_SEALING));
    if (fd < 0) return env->NewStringUTF((std::string("Launcher memfd failed: ") + strerror(errno)).c_str());
    jsize size = env->GetArrayLength(image); jbyte* bytes = env->GetByteArrayElements(image, nullptr);
    if (!bytes) { close(fd); return env->NewStringUTF("Unable to access launcher image"); }
    ssize_t offset = 0; bool ok = true;
    while (offset < size) { ssize_t n = write(fd, bytes + offset, size - offset); if (n <= 0) { ok = false; break; } offset += n; }
    env->ReleaseByteArrayElements(image, bytes, JNI_ABORT);
    if (!ok || fcntl(fd, F_ADD_SEALS, F_SEAL_SEAL | F_SEAL_SHRINK | F_SEAL_GROW | F_SEAL_WRITE) < 0) {
        std::string result = std::string("Launcher memory sealing failed: ") + strerror(errno); close(fd); return env->NewStringUTF(result.c_str());
    }
    android_dlextinfo info{}; info.flags = ANDROID_DLEXT_USE_LIBRARY_FD; info.library_fd = fd;
    void* launcher = android_dlopen_ext("ut99-launcher-adapted.so", RTLD_NOW | RTLD_LOCAL, &info);
    if (!launcher) { const char* e = dlerror(); std::string result = std::string("Launcher in-memory load failed: ") + (e ? e : "unknown"); close(fd); return env->NewStringUTF(result.c_str()); }
    bool hasBss = dlsym(launcher, "__bss_start") != nullptr;
    std::string result = std::string("Launcher in-memory load ") + (hasBss ? "PASS" : "FAIL: missing BSS") + "; main not called";
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    dlclose(launcher); close(fd); return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_ut99android_host_ExperimentalSDLActivity_enableTextInput(JNIEnv* env, jclass, jstring directory) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return JNI_FALSE;
    std::string path = std::string(utf) + "/libSDL2-2.0.so.0";
    env->ReleaseStringUTFChars(directory, utf);
    void* sdl = dlopen(path.c_str(), RTLD_NOW | RTLD_NOLOAD);
    if (!sdl) return JNI_FALSE;
    auto start = reinterpret_cast<void (*)()>(dlsym(sdl, "SDL_StartTextInput"));
    if (start) start();
    dlclose(sdl);
    return start ? JNI_TRUE : JNI_FALSE;
}

static int probeAndroidGL(const std::string& dir) {
    std::string error; void* sdl=loadInDir(dir,"libSDL2-2.0.so.0",error);
    if (!sdl) { __android_log_print(ANDROID_LOG_ERROR,"UT99GL","%s",error.c_str()); return 1; }
    auto init=reinterpret_cast<int(*)(uint32_t)>(dlsym(sdl,"SDL_Init"));
    auto set=reinterpret_cast<int(*)(int,int)>(dlsym(sdl,"SDL_GL_SetAttribute"));
    auto reset=reinterpret_cast<void(*)()>(dlsym(sdl,"SDL_GL_ResetAttributes"));
    auto window=reinterpret_cast<void*(*)(const char*,int,int,int,int,uint32_t)>(dlsym(sdl,"SDL_CreateWindow"));
    auto destroy=reinterpret_cast<void(*)(void*)>(dlsym(sdl,"SDL_DestroyWindow"));
    auto create=reinterpret_cast<void*(*)(void*)>(dlsym(sdl,"SDL_GL_CreateContext"));
    auto drop=reinterpret_cast<void(*)(void*)>(dlsym(sdl,"SDL_GL_DeleteContext"));
    auto getproc=reinterpret_cast<void*(*)(const char*)>(dlsym(sdl,"SDL_GL_GetProcAddress"));
    auto hasext=reinterpret_cast<int(*)(const char*)>(dlsym(sdl,"SDL_GL_ExtensionSupported"));
    auto geterror=reinterpret_cast<const char*(*)()>(dlsym(sdl,"SDL_GetError"));
    auto quit=reinterpret_cast<void(*)()>(dlsym(sdl,"SDL_Quit"));
    if (!init||!set||!reset||!window||!destroy||!create||!drop||!getproc||!hasext||!geterror||!quit) return 2;
    if (init(0x20) != 0) { __android_log_print(ANDROID_LOG_ERROR,"UT99GL","SDL video init failed: %s",geterror()); return 3; }
    const int versions[][2]={{2,0},{3,0},{3,1},{3,2}};
    for (const auto& version : versions) {
        reset(); set(21,4); set(17,version[0]); set(18,version[1]); // SDL ES profile and requested major/minor.
        void* win=window("GL diagnostic",0,0,640,480,0x6);
        if (!win) { __android_log_print(ANDROID_LOG_INFO,"UT99GL","ES %d.%d window failed: %s",version[0],version[1],geterror()); continue; }
        void* ctx=create(win);
        if (!ctx) __android_log_print(ANDROID_LOG_INFO,"UT99GL","ES %d.%d context failed: %s",version[0],version[1],geterror());
        else {
            auto glGetString=reinterpret_cast<const unsigned char*(*)(unsigned int)>(getproc("glGetString"));
            const unsigned char* got=glGetString ? glGetString(0x1f02) : nullptr;
            __android_log_print(ANDROID_LOG_INFO,"UT99GL","ES %d.%d context returned GL_VERSION=%s",version[0],version[1],got?reinterpret_cast<const char*>(got):"<null>");
            __android_log_print(ANDROID_LOG_INFO,"UT99GL","ES %d.%d EXT_buffer_storage=%d resolved_entry=%s ext_entry=%s",version[0],version[1],hasext("GL_EXT_buffer_storage"),getproc("glBufferStorage")?"available":"missing",getproc("glBufferStorageEXT")?"available":"missing");
            if (version[0] == 3 && version[1] == 1) {
                auto gen=reinterpret_cast<void(*)(int,unsigned*)>(getproc("glGenBuffers"));
                auto bind=reinterpret_cast<void(*)(unsigned,unsigned)>(getproc("glBindBuffer"));
                auto storage=reinterpret_cast<void(*)(unsigned,ptrdiff_t,const void*,unsigned)>(getproc("glBufferStorage"));
                auto query=reinterpret_cast<void(*)(unsigned,unsigned,int*)>(getproc("glGetBufferParameteriv"));
                auto glerror=reinterpret_cast<unsigned(*)()>(getproc("glGetError"));
                auto deleteBuffers=reinterpret_cast<void(*)(int,const unsigned*)>(getproc("glDeleteBuffers"));
                if (gen && bind && storage && query && glerror && deleteBuffers) {
                    unsigned id=0; int size=0; for (int i=0;i<16 && glerror();++i) {}
                    gen(1,&id); unsigned eGen=glerror(); bind(0x90d2,id); unsigned eBind=glerror();
                    storage(0x90d2,256,nullptr,0x100); unsigned eStore=glerror();
                    bind(0x8892,id); unsigned eRebind=glerror(); query(0x8892,0x8764,&size); unsigned eQuery=glerror(); deleteBuffers(1,&id);
                    __android_log_print(ANDROID_LOG_INFO,"UT99GL","Buffer storage 256-byte allocation %s id=%u size=%d errors(gen/bind/store/rebind/query)=%x/%x/%x/%x/%x",size==256 && !eGen && !eBind && !eStore && !eRebind && !eQuery ? "PASS" : "FAIL",id,size,eGen,eBind,eStore,eRebind,eQuery);
                } else __android_log_print(ANDROID_LOG_ERROR,"UT99GL","Buffer storage fixture entry missing");
            }
            drop(ctx);
        }
        destroy(win);
    }
    quit(); return 0;
}

// SDLActivity invokes this in its dedicated, debug-only process and SDL thread.
extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeLauncherPath(JNIEnv* env, jclass, jstring directory) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Could not read test directory");
    std::string dir = std::string(utf) + "/core-load"; env->ReleaseStringUTFChars(directory, utf);
    std::string error;
    if (!loadInDir(dir, "libutlibcprobe.so", error)) return env->NewStringUTF(error.c_str());
    void* glibc = loadInDir(dir, "libc.so.6", error);
    if (!glibc) return env->NewStringUTF(error.c_str());
    auto setDir = reinterpret_cast<void (*)(const char*)>(dlsym(glibc, "ut_set_game_directory"));
    auto change = reinterpret_cast<int (*)(const char*)>(dlvsym(glibc, "chdir", "GLIBC_2.17"));
    if (!setDir || !change) return env->NewStringUTF("Path fixture symbols missing");
    pid_t child = fork();
    if (child == 0) {
        setDir(dir.c_str());
        char current[4096];
        char expected[4096];
        if (!realpath(dir.c_str(), expected)) _exit(4);
        if (change("/system/bin") || !getcwd(current, sizeof(current)) || strcmp(expected, current)) _exit(1);
        if (change("/system/bin/arm64") || !getcwd(current, sizeof(current)) || strcmp(expected, current)) _exit(2);
        if (change("/") || !getcwd(current, sizeof(current)) || strcmp(current, "/")) _exit(3);
        _exit(0);
    }
    int status = 0;
    if (child < 0 || waitpid(child, &status, 0) != child) return env->NewStringUTF("Path fixture fork failed");
    bool pass = WIFEXITED(status) && WEXITSTATUS(status) == 0;
    std::string result = std::string("Launcher directory fixture ") + (pass ? "PASS" : "FAIL") + " raw-status=" + std::to_string(status);
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

static int runSdlExperiment(int argc, char** argv) {
    if (argc < 2 || argc > 4 || !argv[1]) return 120;
    if (argc == 3 && !strcmp(argv[2],"gl_probe")) return probeAndroidGL(argv[1]);
    std::string dir(argv[1]); std::string error; void* core = nullptr; void* glibc = nullptr; void* cxx = nullptr; void* sdl = nullptr;
    const char* names[] = {"libutlibcprobe.so", "libc.so.6", "libSDL2-2.0.so.0", "libdl.so.2", "libstdc++.so.6", "libm.so.6", "libgcc_s.so.1", "ld-linux-aarch64.so.1", "Core.so", "Engine.so", "libpthread.so.0"};
    for (const char* name : names) {
        void* handle = loadInDir(dir, name, error);
        if (!handle) { __android_log_print(ANDROID_LOG_ERROR,"UT99Host","SDL host: %s",error.c_str()); return 121; }
        if (!strcmp(name,"Core.so")) core=handle;
        if (!strcmp(name,"libc.so.6")) glibc=handle;
        if (!strcmp(name,"libstdc++.so.6")) cxx=handle;
        if (!strcmp(name,"libSDL2-2.0.so.0")) sdl=handle;
    }
    auto setCore = reinterpret_cast<void (*)(void*)>(dlsym(glibc,"ut_set_core_tls_module"));
    auto setDir = reinterpret_cast<void (*)(const char*)>(dlsym(glibc,"ut_set_game_directory"));
    if (!core || !setCore || !setDir || !cxx || !installCoreConfigCast(core, cxx)) {
        __android_log_print(ANDROID_LOG_ERROR,"UT99Host","Unable to install the pinned Core configuration compatibility fix");
        return 122;
    }
    setCore(core); setDir(dir.c_str());
    setenv("UT99_SYSTEM_DIR",dir.c_str(),1);
    setenv("UT99_DIAG_KEEP_CRASH_HANDLERS","1",1);
    // The phone accelerometer is not a gameplay controller.
    setenv("SDL_ACCELEROMETER_AS_JOYSTICK","0",1);
    if (argc >= 3 && !strcmp(argv[2], "touch") && (!sdl || !attachTouchJoystick(sdl)))
        __android_log_print(ANDROID_LOG_ERROR,"UT99Host","Unable to attach the touch joystick");
    void* driver = loadInDir(dir,"SDLDrv.so",error);
    if (!driver) __android_log_print(ANDROID_LOG_ERROR,"UT99Host","SDLDrv preload: %s",error.c_str());
    else {
        __android_log_print(ANDROID_LOG_INFO,"UT99Host","SDLDrv preload returned");
        if (!installCameraSensitivity(driver))
            __android_log_print(ANDROID_LOG_ERROR,"UT99Host","Camera sensitivity adjustment unavailable for this SDL driver");
        if (!sdl || !installStandardController(driver, sdl))
            __android_log_print(ANDROID_LOG_ERROR,"UT99Host","SDL standard controller bridge unavailable; using raw joystick input");
    }
    void* renderer = loadInDir(dir,"Render.so",error);
    if (!renderer) __android_log_print(ANDROID_LOG_ERROR,"UT99Host","Render preload: %s",error.c_str());
    else __android_log_print(ANDROID_LOG_INFO,"UT99Host","Render preload returned");
    void* network = loadInDir(dir,"IpDrv.so",error);
    if (!network) __android_log_print(ANDROID_LOG_ERROR,"UT99Host","IpDrv preload: %s",error.c_str());
    else __android_log_print(ANDROID_LOG_INFO,"UT99Host","IpDrv preload returned");
    bool audioReady = true;
    const char* audioLibraries[] = {"libopenal.so.1", "libxmp.so.4", "libmpg123.so", "libsndfile.so.1", "ALAudio.so"};
    for (const char* name : audioLibraries) {
        if (!loadInDir(dir, name, error)) {
            __android_log_print(ANDROID_LOG_ERROR, "UT99Host", "Audio preload: %s", error.c_str());
            audioReady = false;
            break;
        }
    }
    void* gl = loadInDir(dir,"OpenGLDrv.so",error);
    if (!gl) __android_log_print(ANDROID_LOG_ERROR,"UT99Host","OpenGLDrv preload: %s",error.c_str());
    else __android_log_print(ANDROID_LOG_INFO,"UT99Host","OpenGLDrv preload returned");
    void* launcher = loadInDir(dir,"ut-launch.so",error);
    if (!launcher) { __android_log_print(ANDROID_LOG_ERROR,"UT99Host","SDL host: %s",error.c_str()); return 123; }
    void* bss=dlsym(launcher,"__bss_start"); if (!bss) return 124;
    auto mainEntry=reinterpret_cast<int (*)(int,char**)>(reinterpret_cast<uintptr_t>(bss)-0x311a8+0x6e90);
    if (chdir(dir.c_str())) return 125;
    setenv("HOME",dir.c_str(),1);
    int capture=open("sdl-launcher-output.txt",O_WRONLY|O_CREAT|O_TRUNC,0600);
    if (capture >= 0) { dup2(capture,STDOUT_FILENO); dup2(capture,STDERR_FILENO); close(capture); }
    char executable[]="ut99"; char windowed[]="-windowed"; char noSound[]="-nosound";
    char* launchArgv[]={executable, argc == 4 ? argv[3] : windowed, argc == 4 ? windowed : noSound, noSound, nullptr};
    int launchArgc = (argc == 4 ? 3 : 2) + (audioReady ? 0 : 1);
    __android_log_print(ANDROID_LOG_INFO,"UT99Host","SDL Java bridge initialized; entering original launcher main");
    int result=mainEntry(launchArgc,launchArgv);
    __android_log_print(ANDROID_LOG_INFO,"UT99Host","SDL-hosted launcher main returned %d",result);
    return result;
}

extern "C" __attribute__((visibility("default"))) int ut_sdl_experiment_main(int argc, char** argv) {
    int result = runSdlExperiment(argc, argv);
    if (argc >= 2 && argv[1]) {
        std::string path = std::string(argv[1]) + "/runtime-status.txt";
        FILE* file = fopen(path.c_str(), "w");
        if (file) { fprintf(file, "SDL-hosted launcher returned %d\n", result); fclose(file); }
    }
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "SDL experiment returned %d", result);
    return result;
}
extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeJumpBaseline(JNIEnv* env, jclass, jstring directory) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Could not read probe directory");
    std::string dir = std::string(utf) + "/core-load"; env->ReleaseStringUTFChars(directory, utf);
    std::string result; void* fixture = loadInDir(dir, "libutjumpbaseline.so", result);
    if (!fixture) return env->NewStringUTF(result.c_str());
    auto run = reinterpret_cast<int (*)(int)>(dlsym(fixture, "jump_fixture_run"));
    if (!run) return env->NewStringUTF("Baseline jump entry missing");
    pid_t child = fork();
    if (child == 0) { alarm(4); _exit(run(1)); }
    int status = 0;
    if (child < 0 || waitpid(child, &status, 0) != child) return env->NewStringUTF("Baseline fork/wait failed");
    result = std::string("Standalone Bionic jump baseline ") + (WIFEXITED(status) && WEXITSTATUS(status) == 0 ? "PASS" : "FAIL") + " raw-status=" + std::to_string(status);
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

// Explicit debug-only experiment. Constructors can execute arbitrary engine code.
extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeExperimentalCore(JNIEnv* env, jclass, jstring directory, jboolean loadEngine, jboolean loadLauncher, jboolean runMain) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Could not read probe directory");
    std::string dir = std::string(utf) + "/core-load"; env->ReleaseStringUTFChars(directory, utf);
    std::string result;
    void* coreHandle = nullptr; void* libcHandle = nullptr;
    const char* names[] = {"libutlibcprobe.so", "libc.so.6", "libSDL2-2.0.so.0", "libdl.so.2", "libstdc++.so.6", "libm.so.6", "libgcc_s.so.1", "ld-linux-aarch64.so.1", "Core.so"};
    for (const char* name : names) {
        __android_log_print(ANDROID_LOG_INFO, "UT99Host", "Experimental load starting %s", name);
        void* loaded = loadInDir(dir, name, result);
        if (!loaded) {
            __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
            return env->NewStringUTF(result.c_str());
        }
        if (!strcmp(name, "Core.so")) coreHandle = loaded;
        if (!strcmp(name, "libc.so.6")) libcHandle = loaded;
        __android_log_print(ANDROID_LOG_INFO, "UT99Host", "Experimental load returned %s", name);
    }
    void* jump = loadInDir(dir, "libutjumpfixture.so", result);
    if (!jump) { __android_log_print(ANDROID_LOG_ERROR, "UT99Host", "%s", result.c_str()); return env->NewStringUTF(result.c_str()); }
    auto jumpTest = reinterpret_cast<int (*)(int)>(dlsym(jump, "jump_fixture_run"));
    if (!jumpTest) return env->NewStringUTF("Jump fixture entry missing");
    bool jumpPassed = true;
    for (int baseline = 1; baseline >= 0; --baseline) {
        pid_t child = fork();
        if (child == 0) { alarm(4); _exit(jumpTest(baseline)); }
        int status = 0;
        if (child < 0 || waitpid(child, &status, 0) != child) return env->NewStringUTF("Jump fixture fork/wait failed");
        bool passed = WIFEXITED(status) && WEXITSTATUS(status) == 0;
        __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s jump fixture %s raw-status=%d", baseline ? "Bionic baseline" : "Paired shim", passed ? "PASS" : "FAIL", status);
        if (!passed && !baseline) jumpPassed = false;
    }
    if (!jumpPassed) return env->NewStringUTF("Jump fixture failed or timed out");
    void* iconvFixture=loadInDir(dir, "libuticonvfixture.so", result);
    auto iconvTest=iconvFixture ? reinterpret_cast<int (*)()>(dlsym(iconvFixture,"iconv_fixture_run")) : nullptr;
    int iconvResult=iconvTest ? iconvTest() : -1;
    __android_log_print(ANDROID_LOG_INFO,"UT99Host","Windows-1252 conversion fixture result=%d %s",iconvResult,iconvResult==0 ? "PASS" : "FAIL");
    if (iconvResult) return env->NewStringUTF("Windows-1252 conversion fixture failed");
    if (loadEngine) {
        __android_log_print(ANDROID_LOG_INFO, "UT99Host", "Experimental load starting Engine.so");
        if (!loadInDir(dir, "Engine.so", result)) {
            __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
            return env->NewStringUTF(result.c_str());
        }
        if (loadLauncher) {
            auto setCore = reinterpret_cast<void (*)(void*)>(dlsym(libcHandle, "ut_set_core_tls_module"));
            void* thisTls = dlsym(coreHandle, "_ZN15UnGuardBlockTLS4DataE");
            void* workerTls = nullptr;
            std::thread worker([&] { workerTls = dlsym(coreHandle, "_ZN15UnGuardBlockTLS4DataE"); });
            worker.join();
            if (!setCore || !thisTls || !workerTls || thisTls == workerTls) {
                result = "Core dynamic TLS lookup failed or was not per-thread";
                __android_log_print(ANDROID_LOG_ERROR, "UT99Host", "%s", result.c_str());
                return env->NewStringUTF(result.c_str());
            }
            setCore(coreHandle);
            auto setGameDirectory = reinterpret_cast<void (*)(const char*)>(dlsym(libcHandle, "ut_set_game_directory"));
            if (!setGameDirectory) return env->NewStringUTF("Game directory adapter missing");
            setGameDirectory(dir.c_str());
            __android_log_print(ANDROID_LOG_INFO, "UT99Host", "Core dynamic TLS lookup passed distinct-thread control");
            auto tlsOffset = reinterpret_cast<intptr_t (*)()>(dlvsym(libcHandle, "__libc_start_main", "GLIBC_2.17"));
            if (!tlsOffset || reinterpret_cast<uintptr_t>(__builtin_thread_pointer()) + tlsOffset() != reinterpret_cast<uintptr_t>(thisTls)) {
                result = "TLS trampoline control failed";
                __android_log_print(ANDROID_LOG_ERROR, "UT99Host", "%s", result.c_str());
                return env->NewStringUTF(result.c_str());
            }
            __android_log_print(ANDROID_LOG_INFO, "UT99Host", "TLS trampoline same-thread address control PASS");
            __android_log_print(ANDROID_LOG_INFO, "UT99Host", "Experimental load starting launcher pthread dependency");
            if (!loadInDir(dir, "libpthread.so.0", result)) { __android_log_print(ANDROID_LOG_ERROR, "UT99Host", "%s", result.c_str()); return env->NewStringUTF(result.c_str()); }
            __android_log_print(ANDROID_LOG_INFO, "UT99Host", "Experimental load starting patched launcher");
            void* launcher = loadInDir(dir, "ut-launch.so", result);
            if (!launcher) { __android_log_print(ANDROID_LOG_ERROR, "UT99Host", "%s", result.c_str()); return env->NewStringUTF(result.c_str()); }
            if (runMain) {
                void* bss = dlsym(launcher, "__bss_start");
                if (!bss) return env->NewStringUTF("Launcher base symbol not found");
                uintptr_t base = reinterpret_cast<uintptr_t>(bss) - 0x311a8;
                auto mainEntry = reinterpret_cast<int (*)(int, char**)>(base + 0x6e90);
                __android_log_print(ANDROID_LOG_INFO, "UT99Host", "Starting original launcher main in app-private child");
                pid_t child = fork();
                if (child == 0) {
                    alarm(8);
                    if (chdir(dir.c_str())) _exit(125);
                    setenv("HOME", dir.c_str(), 1);
                    int capture = open("launcher-output.txt", O_WRONLY | O_CREAT | O_TRUNC, 0600);
                    if (capture >= 0) { dup2(capture, STDOUT_FILENO); dup2(capture, STDERR_FILENO); close(capture); }
                    char executable[] = "ut99"; char windowed[] = "-windowed"; char noSound[] = "-nosound";
                    char* argv[] = {executable, windowed, noSound, nullptr};
                    int code = mainEntry(3, argv);
                    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "Launcher main returned %d", code);
                    _exit(code & 255);
                }
                int status = 0;
                if (child < 0 || waitpid(child, &status, 0) != child) result = "Launcher child fork/wait failed";
                else if (WIFEXITED(status)) result = "Launcher child exited code=" + std::to_string(WEXITSTATUS(status));
                else if (WIFSIGNALED(status)) result = "Launcher child terminated signal=" + std::to_string(WTERMSIG(status));
                else result = "Launcher child ended with unexpected status=" + std::to_string(status);
                __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
                int captured = open((dir + "/launcher-output.txt").c_str(), O_RDONLY);
                if (captured >= 0) {
                    char output[4096]; ssize_t n = read(captured, output, sizeof(output)-1); close(captured);
                    if (n > 0) { output[n] = 0; __android_log_print(ANDROID_LOG_INFO, "UT99Host", "Launcher output: %s", output); }
                }
                return env->NewStringUTF(result.c_str());
            }
            result = "Patched launcher dlopen returned; main was not called.";
            __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
            return env->NewStringUTF(result.c_str());
        }
        result = "Experimental Core and Engine dlopen returned; constructors ran. Engine initialization not called.";
        __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
        return env->NewStringUTF(result.c_str());
    }
    result = "Experimental Core dlopen returned; constructors ran. Engine initialization not called.";
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probePartial(JNIEnv* env, jclass, jstring directory) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Could not read probe directory");
    std::string dir = std::string(utf) + "/partial"; env->ReleaseStringUTFChars(directory, utf);
    std::string result; void* handles[3]{}; int count = 0;
    auto load = [&](const char* name) { void* h = loadInDir(dir, name, result); if (h) handles[count++] = h; return h; };
    if (load("libutlibcprobe.so") && load("libc.so.6")) {
        void* fixture = load("libutpartialfixture.so");
        if (fixture) {
            auto run = reinterpret_cast<int (*)(const char*, int, int64_t)>(dlsym(fixture, "partial_fixture_run"));
            std::string path = dir + "/ut99-partial-XXXXXX";
            int fd = mkstemp(path.data());
            if (!run || fd < 0) result = "Partial libc synthetic fixture setup failed";
            else {
                const char payload[] = "partial-fixture";
                ssize_t wrote = write(fd, payload, sizeof(payload) - 1);
                int rc = wrote == static_cast<ssize_t>(sizeof(payload) - 1) ? run(path.c_str(), fd, sizeof(payload) - 1) : -1;
                result = "Incomplete libc synthetic fixture result=" + std::to_string(rc) + (rc == 0 ? " PASS" : " FAIL");
            }
            if (fd >= 0) { close(fd); unlink(path.c_str()); }
        }
    }
    while (count) dlclose(handles[--count]);
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeFormat(JNIEnv* env, jclass, jstring directory) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Could not read probe directory");
    std::string dir(utf); env->ReleaseStringUTFChars(directory, utf);
    std::string result; void* bridge = loadInDir(dir, "libutlibcprobe.so", result);
    if (bridge) {
        using Narrow = int (*)(FILE*, int, const char*, ...);
        using Wide = int (*)(FILE*, int, const wchar_t*, ...);
        auto narrow = reinterpret_cast<Narrow>(dlvsym(bridge, "ut_gfprintf_chk", "GLIBC_2.17"));
        auto wide = reinterpret_cast<Wide>(dlvsym(bridge, "ut_gfwprintf_chk", "GLIBC_2.17"));
        if (!narrow || !wide) result = "Format probe symbols missing";
        else {
            std::string narrowPath = dir + "/ut99-format-n-XXXXXX";
            int nfd = mkstemp(narrowPath.data()); FILE* nf = nfd >= 0 ? fdopen(nfd, "w+") : nullptr;
            bool ok = nf != nullptr;
            if (nf) {
                const char expected[] = "File Deck16 7 0x1f 1.25";
                int rc = narrow(nf, 1, "File %s %d %#x %.2f", "Deck16", 7, 31, 1.25);
                int target = 777; errno = 0; int bad = narrow(nf, 1, "%n", &target); int badErr = errno;
                errno = 0; int positional = narrow(nf, 1, "%2$d", 1, 2); int positionalErr = errno;
                errno = 0; int invalid = narrow(nf, 1, "%lls", "ignored"); int invalidErr = errno;
                int flushed = fflush(nf); off_t start = lseek(nfd, 0, SEEK_SET);
                char actual[128]{}; ssize_t got = read(nfd, actual, sizeof(actual));
                ok = ok && rc == static_cast<int>(sizeof(expected) - 1) && bad == -1 && badErr == ENOTSUP && target == 777 && positional == -1 && positionalErr == ENOTSUP && invalid == -1 && invalidErr == ENOTSUP && flushed == 0 && start == 0 && got == static_cast<ssize_t>(sizeof(expected) - 1) && std::memcmp(actual, expected, sizeof(expected) - 1) == 0;
                fclose(nf);
            } else if (nfd >= 0) close(nfd);
            if (nfd >= 0) unlink(narrowPath.c_str());
            std::string widePath = dir + "/ut99-format-w-XXXXXX";
            int wfd = mkstemp(widePath.data()); FILE* wf = wfd >= 0 ? fdopen(wfd, "w+") : nullptr;
            if (wf) {
                const char expected[] = "WARNING: Using system ini file: UnrealTournament.ini\n";
                int rc = wide(wf, 1, L"WARNING: Using system ini file: %ls\n", L"UnrealTournament.ini");
                int marker = 777; errno = 0; int rejected = wide(wf, 1, L"%n", &marker); int rejectedErr = errno;
                errno = 0; int positional = wide(wf, 1, L"%2$d", 1, 2); int positionalErr = errno;
                int flushed = fflush(wf); off_t start = lseek(wfd, 0, SEEK_SET);
                char actual[160]{}; ssize_t got = read(wfd, actual, sizeof(actual));
                ok = ok && rc == static_cast<int>(sizeof(expected) - 1) && rejected == -1 && rejectedErr == ENOTSUP && marker == 777 && positional == -1 && positionalErr == ENOTSUP && flushed == 0 && start == 0 && got == static_cast<ssize_t>(sizeof(expected) - 1) && std::memcmp(actual, expected, sizeof(expected) - 1) == 0;
                fclose(wf);
            } else { ok = false; if (wfd >= 0) close(wfd); }
            if (wfd >= 0) unlink(widePath.c_str());
            result = ok ? "Restricted fortified narrow and Core-style wide formatting controls PASS" : "Restricted fortified formatting controls FAIL";
        }
        dlclose(bridge);
    }
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeStat(JNIEnv* env, jclass, jstring directory) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Could not read probe directory");
    std::string dir(utf); env->ReleaseStringUTFChars(directory, utf);
    std::string result; void* bridge = loadInDir(dir, "libutlibcprobe.so", result);
    if (bridge) {
        using XStat = int (*)(int, const char*, UtGlibcStat*);
        using FXStat = int (*)(int, int, UtGlibcStat*);
        auto xstat = reinterpret_cast<XStat>(dlvsym(bridge, "ut_gxstat", "GLIBC_2.17"));
        auto fxstat = reinterpret_cast<FXStat>(dlvsym(bridge, "ut_gfxstat", "GLIBC_2.17"));
        if (!xstat || !fxstat) result = "stat probe exports missing";
        else {
            std::string path = dir + "/ut99-stat-XXXXXX";
            int fd = mkstemp(path.data());
            if (fd < 0) result = "stat fixture creation failed";
            else {
                const char payload[] = "ut99-test";
                bool ok = write(fd, payload, sizeof(payload) - 1) == static_cast<ssize_t>(sizeof(payload) - 1);
                struct stat native{}; ok = (fstat(fd, &native) == 0) && ok;
                struct Guarded { uint64_t before; UtGlibcStat data; uint64_t after; } statBuf{};
                std::memset(&statBuf, 0xa5, sizeof(statBuf));
                int xrc = xstat(0, path.c_str(), &statBuf.data);
                ok = ok && xrc == 0 && statBuf.before == UINT64_C(0xa5a5a5a5a5a5a5a5) && statBuf.after == UINT64_C(0xa5a5a5a5a5a5a5a5);
                ok = ok && statBuf.data.ino == native.st_ino && statBuf.data.dev == native.st_dev && statBuf.data.mode == native.st_mode && statBuf.data.nlink == native.st_nlink && statBuf.data.uid == native.st_uid && statBuf.data.gid == native.st_gid && statBuf.data.rdev == native.st_rdev && statBuf.data.size == native.st_size && statBuf.data.blksize == native.st_blksize && statBuf.data.blocks == native.st_blocks && statBuf.data.mtime_sec == native.st_mtim.tv_sec && statBuf.data.mtime_nsec == native.st_mtim.tv_nsec;
                UtGlibcStat fromFd{}; int frc = fxstat(0, fd, &fromFd);
                ok = ok && frc == 0 && fromFd.ino == statBuf.data.ino && fromFd.size == statBuf.data.size;
                std::string linkError;
                void* exports = loadInDir(dir, "libutfileprobe.so", linkError);
                void* fixture = exports ? loadInDir(dir, "libutfilefixture.so", linkError) : nullptr;
                auto run = fixture ? reinterpret_cast<int (*)(const char*, int, int64_t)>(dlsym(fixture, "stat_fixture_run")) : nullptr;
                int linkedFixture = run ? run(path.c_str(), fd, static_cast<int64_t>(sizeof(payload) - 1)) : -1;
                ok = ok && linkedFixture == 0;
                if (fixture) dlclose(fixture);
                if (exports) dlclose(exports);
                UtGlibcStat unchanged; std::memset(&unchanged, 0x5a, sizeof(unchanged)); UtGlibcStat before = unchanged;
                errno = 0; int invalid = xstat(1, path.c_str(), &unchanged); int invalidErr = errno;
                ok = ok && invalid == -1 && invalidErr == EINVAL && std::memcmp(&unchanged, &before, sizeof(before)) == 0;
                errno = 0; int badFd = fxstat(0, -1, &unchanged); int fdErr = errno;
                ok = ok && badFd == -1 && fdErr == EBADF && std::memcmp(&unchanged, &before, sizeof(before)) == 0;
                std::string link = path + ".link"; bool madeLink = symlink(path.c_str(), link.c_str()) == 0;
                UtGlibcStat linked{}; int linkRc = madeLink ? xstat(0, link.c_str(), &linked) : -1;
                ok = ok && madeLink && linkRc == 0 && linked.ino == native.st_ino && linked.size == native.st_size;
                if (madeLink) unlink(link.c_str()); close(fd); unlink(path.c_str());
                errno = 0; int missing = xstat(0, path.c_str(), &unchanged); int missingErr = errno;
                ok = ok && missing == -1 && missingErr == ENOENT && std::memcmp(&unchanged, &before, sizeof(before)) == 0;
                result = ok ? "AArch64 stat, versioned imports, symlink and failure controls PASS" : "AArch64 stat controls FAIL; versioned fixture=" + std::to_string(linkedFixture) + " " + linkError;
            }
        }
        dlclose(bridge);
    }
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeCtype(JNIEnv* env, jclass, jstring directory) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Could not read probe directory");
    std::string dir(utf); env->ReleaseStringUTFChars(directory, utf);
    std::string result; void* bridge = loadInDir(dir, "libutlibcprobe.so", result);
    if (bridge) {
        using ClassLoc = const uint16_t** (*)(); using ConvertLoc = const int32_t** (*)();
        auto classes = reinterpret_cast<ClassLoc>(dlvsym(bridge, "__ctype_b_loc", "GLIBC_2.17"));
        auto lower = reinterpret_cast<ConvertLoc>(dlvsym(bridge, "__ctype_tolower_loc", "GLIBC_2.17"));
        auto upper = reinterpret_cast<ConvertLoc>(dlvsym(bridge, "__ctype_toupper_loc", "GLIBC_2.17"));
        unsigned char expected[3840]{};
        FILE* file = std::fopen((dir + "/ctype-reference.bin").c_str(), "rb");
        size_t bytes = file ? std::fread(expected, 1, sizeof(expected), file) : 0;
        int extra = file ? std::fgetc(file) : 0;
        if (file) std::fclose(file);
        if (!classes || !lower || !upper || bytes != sizeof(expected) || extra != EOF) result = "C locale reference or symbols missing";
        else {
            const uint16_t** mainSlot = classes();
            bool values = std::memcmp(*mainSlot - 128, expected, 768) == 0 &&
                std::memcmp(*lower() - 128, expected + 768, 1536) == 0 &&
                std::memcmp(*upper() - 128, expected + 2304, 1536) == 0;
            bool threadOk = false;
            std::thread worker([&] { const uint16_t** slot = classes(); threadOk = slot != mainSlot && (*slot)['A'] == (*mainSlot)['A']; });
            worker.join();
            result = values && threadOk ? "C locale tables match pinned glibc for 384 entries; thread slot PASS" : "C locale table or thread slot FAIL";
        }
        dlclose(bridge);
    }
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeEventLink(JNIEnv* env, jclass, jstring directory) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Could not read probe directory");
    std::string dir(utf); env->ReleaseStringUTFChars(directory, utf);
    std::string result; void* handles[3]{}; int count = 0;
    auto load = [&](const char* name) { void* h = loadInDir(dir, name, result); if (h) handles[count++] = h; return h; };
    if (load("libutlibcprobe.so") && load("libutpthreadprobe.so")) {
        void* fixture = load("libuteventfixture.so");
        if (fixture) {
            auto run = reinterpret_cast<int (*)()>(dlsym(fixture, "event_fixture_run"));
            if (!run) result = "Versioned event fixture entry missing";
            else { int rc = run(); result = "Versioned event fixture result=" + std::to_string(rc) + (rc == 0 ? " PASS" : " FAIL"); }
        }
    }
    while (count) dlclose(handles[--count]);
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeCondition(JNIEnv* env, jclass, jstring directory) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Could not read probe directory");
    std::string dir(utf); env->ReleaseStringUTFChars(directory, utf);
    std::string result; void* bridge = loadInDir(dir, "libutlibcprobe.so", result);
    if (bridge) {
        using Op = int (*)(void*); using Init = int (*)(void*, const void*);
        using Wait = int (*)(void*, void*); using Timed = int (*)(void*, void*, const timespec*);
        auto initM = reinterpret_cast<Init>(dlvsym(bridge, "ut_gmutex_init", "GLIBC_2.17"));
        auto lock = reinterpret_cast<Op>(dlvsym(bridge, "ut_gmutex_lock", "GLIBC_2.17"));
        auto attempt = reinterpret_cast<Op>(dlvsym(bridge, "ut_gmutex_trylock", "GLIBC_2.17"));
        auto unlock = reinterpret_cast<Op>(dlvsym(bridge, "ut_gmutex_unlock", "GLIBC_2.17"));
        auto destroyM = reinterpret_cast<Op>(dlvsym(bridge, "ut_gmutex_destroy", "GLIBC_2.17"));
        auto initC = reinterpret_cast<Init>(dlvsym(bridge, "ut_gcond_init", "GLIBC_2.17"));
        auto timed = reinterpret_cast<Timed>(dlvsym(bridge, "ut_gcond_timedwait", "GLIBC_2.17"));
        auto signal = reinterpret_cast<Op>(dlvsym(bridge, "ut_gcond_signal", "GLIBC_2.17"));
        auto broadcast = reinterpret_cast<Op>(dlvsym(bridge, "ut_gcond_broadcast", "GLIBC_2.17"));
        auto destroyC = reinterpret_cast<Op>(dlvsym(bridge, "ut_gcond_destroy", "GLIBC_2.17"));
        auto wait = reinterpret_cast<Wait>(dlvsym(bridge, "ut_gcond_wait", "GLIBC_2.17"));
        if (!initM || !lock || !attempt || !unlock || !destroyM || !initC || !wait || !timed || !signal || !broadcast || !destroyC) result = "Condition probe exports missing";
        else {
            // Core's FEventUnix disassembly places cond at +8 and mutex at +56.
            alignas(8) unsigned char event[120]{};
            unsigned char* cond = event + 8; unsigned char* mutex = event + 56;
            std::memset(event + 104, 0xa5, 16);
            alignas(8) unsigned char unsupported[48]{}; unsupported[0] = 1;
            int mi = initM(mutex, nullptr), ci = initC(cond, nullptr);
            bool ok = mi == 0 && ci == 0 && sizeof(timespec) == 16;
            int gotLock = mi == 0 ? lock(mutex) : -1;
            timespec deadline{}; clock_gettime(CLOCK_REALTIME, &deadline); deadline.tv_nsec += 30000000;
            if (deadline.tv_nsec >= 1000000000) { ++deadline.tv_sec; deadline.tv_nsec -= 1000000000; }
            int timeout = gotLock == 0 && ci == 0 ? timed(cond, mutex, &deadline) : -1;
            int stillLocked = gotLock == 0 ? attempt(mutex) : -1;
            if (stillLocked == 0) unlock(mutex);
            int released = gotLock == 0 ? unlock(mutex) : -1;
            ok = ok && gotLock == 0 && timeout == ETIMEDOUT && stillLocked == EBUSY && released == 0;
            std::atomic<int> ready{0}; std::atomic<bool> workersOk{true}; bool proceed = false;
            auto work = [&] {
                int acquired = lock(mutex); ++ready;
                if (acquired != 0) { workersOk = false; return; }
                timespec until{}; clock_gettime(CLOCK_REALTIME, &until); until.tv_sec += 3;
                while (!proceed) { int rc = timed(cond, mutex, &until); if (rc) { workersOk = false; break; } }
                if (unlock(mutex) != 0) workersOk = false;
            };
            int busy = -1, notified = -1, mainUnlock = -1;
            if (mi == 0 && ci == 0 && released == 0) {
                std::thread first(work), second(work);
                auto end = std::chrono::steady_clock::now() + std::chrono::seconds(2);
                while (ready.load() < 2 && std::chrono::steady_clock::now() < end) std::this_thread::yield();
                if (lock(mutex) == 0) {
                    busy = destroyC(cond); proceed = true; notified = broadcast(cond); mainUnlock = unlock(mutex);
                } else workersOk = false;
                first.join(); second.join();
            } else workersOk = false;
            int signaled = ci == 0 ? signal(cond) : -1;
            std::atomic<bool> oneReady{false}; bool singleProceed = false;
            int singleSignal = -1, singleUnlock = -1;
            if (mi == 0 && ci == 0 && released == 0) {
                std::thread one([&] {
                    int acquired = lock(mutex); oneReady = true;
                    if (acquired != 0) { workersOk = false; return; }
                    while (!singleProceed) { int rc = wait(cond, mutex); if (rc) { workersOk = false; break; } }
                    if (unlock(mutex) != 0) workersOk = false;
                });
                auto end = std::chrono::steady_clock::now() + std::chrono::seconds(2);
                while (!oneReady.load() && std::chrono::steady_clock::now() < end) std::this_thread::yield();
                if (lock(mutex) == 0) { singleProceed = true; singleSignal = signal(cond); singleUnlock = unlock(mutex); }
                else workersOk = false;
                one.join();
            }
            alignas(8) unsigned char recursive[48]{}; int32_t recursiveKind = 1;
            std::memcpy(recursive + 16, &recursiveKind, sizeof(recursiveKind));
            int recLock = lock(recursive);
            int recWait = recLock == 0 ? wait(cond, recursive) : -1;
            int recUnlock = recLock == 0 ? unlock(recursive) : -1;
            int recDestroy = destroyM(recursive);
            int badInit = initC(unsupported, unsupported);
            int badSignal = signal(unsupported);
            int dc = ci == 0 ? destroyC(cond) : -1;
            int dm = mi == 0 ? destroyM(mutex) : -1;
            bool sentinels = true; for (int i = 104; i < 120; ++i) sentinels = sentinels && event[i] == 0xa5;
            ok = ok && ready == 2 && workersOk && busy == EBUSY && notified == 0 && mainUnlock == 0 && signaled == 0 && oneReady && singleSignal == 0 && singleUnlock == 0 && recLock == 0 && recWait == ENOTSUP && recUnlock == 0 && recDestroy == 0 && badInit == ENOTSUP && badSignal == ENOTSUP && dc == 0 && dm == 0 && sentinels;
            result = ok ? "Condition wait, signal, timeout, broadcast and lifecycle controls PASS" : "Condition controls FAIL";
            result += " timeout=" + std::to_string(timeout) + " busy=" + std::to_string(busy) + " workers=" + std::to_string(ready.load());
        }
        dlclose(bridge);
    }
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeMutex(JNIEnv* env, jclass, jstring directory) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Could not read probe directory");
    std::string dir(utf); env->ReleaseStringUTFChars(directory, utf);
    std::string result;
    void* bridge = loadInDir(dir, "libutlibcprobe.so", result);
    if (bridge) {
        using Op = int (*)(void*);
        using Init = int (*)(void*, const void*);
        auto init = reinterpret_cast<Init>(dlvsym(bridge, "ut_gmutex_init", "GLIBC_2.17"));
        auto lock = reinterpret_cast<Op>(dlvsym(bridge, "ut_gmutex_lock", "GLIBC_2.17"));
        auto attempt = reinterpret_cast<Op>(dlvsym(bridge, "ut_gmutex_trylock", "GLIBC_2.17"));
        auto unlock = reinterpret_cast<Op>(dlvsym(bridge, "ut_gmutex_unlock", "GLIBC_2.17"));
        auto destroy = reinterpret_cast<Op>(dlvsym(bridge, "ut_gmutex_destroy", "GLIBC_2.17"));
        if (!init || !lock || !attempt || !unlock || !destroy) result = "Mutex probe exports missing";
        else {
            alignas(8) unsigned char nativeRisk[48]{};
            int32_t recursiveKind = 1;
            std::memcpy(nativeRisk + 16, &recursiveKind, sizeof(recursiveKind));
            auto* direct = reinterpret_cast<pthread_mutex_t*>(nativeRisk);
            int directLock = pthread_mutex_lock(direct);
            int directTry = directLock == 0 ? pthread_mutex_trylock(direct) : -1;
            if (directTry == 0) pthread_mutex_unlock(direct);
            if (directLock == 0) pthread_mutex_unlock(direct);
            pthread_mutex_destroy(direct);

            alignas(8) unsigned char normal[48]{};
            alignas(8) unsigned char recursive[48]{};
            alignas(8) unsigned char unsupported[48]{};
            int32_t unsupportedKind = 3;
            std::memcpy(recursive + 16, &recursiveKind, sizeof(recursiveKind));
            std::memcpy(unsupported + 16, &unsupportedKind, sizeof(unsupportedKind));
            bool ok = sizeof(pthread_mutex_t) == 40 && directLock == 0 && directTry == EBUSY;
            int initRc = init(normal, nullptr);
            int lockRc = initRc == 0 ? lock(normal) : -1;
            int busyRc = lockRc == 0 ? destroy(normal) : -1;
            int unlockRc = lockRc == 0 ? unlock(normal) : -1;
            ok = ok && initRc == 0 && lockRc == 0 && busyRc == EBUSY && unlockRc == 0;
            int counter = 0; std::atomic<bool> workersOk{true};
            auto work = [&] { for (int i = 0; i < 1000; ++i) {
                if (lock(normal) != 0) { workersOk = false; return; }
                ++counter;
                if (unlock(normal) != 0) { workersOk = false; return; }
            } };
            if (initRc == 0 && unlockRc == 0) { std::thread a(work), b(work); a.join(); b.join(); }
            int normalDestroy = initRc == 0 ? destroy(normal) : -1;
            ok = ok && workersOk && counter == 2000 && normalDestroy == 0;
            int recLock = lock(recursive);
            int recTry = recLock == 0 ? attempt(recursive) : -1;
            int recUnlock1 = recTry == 0 ? unlock(recursive) : -1;
            int recUnlock2 = recLock == 0 ? unlock(recursive) : -1;
            int recDestroy = destroy(recursive);
            ok = ok && recLock == 0 && recTry == 0 && recUnlock1 == 0 && recUnlock2 == 0 && recDestroy == 0;
            int unsupportedLock = lock(unsupported);
            int unsupportedInit = init(unsupported, unsupported);
            ok = ok && unsupportedLock == ENOTSUP && unsupportedInit == ENOTSUP;
            result = std::string("glibc mutex 48/Bionic ") + std::to_string(sizeof(pthread_mutex_t)) +
                " direct recursive try=" + std::to_string(directTry) +
                (ok ? "; restricted side-table controls PASS" : "; restricted side-table controls FAIL");
        }
        dlclose(bridge);
    }
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeReferenceRtti(JNIEnv* env, jclass, jstring directory) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Could not read probe directory");
    std::string dir(utf); env->ReleaseStringUTFChars(directory, utf);
    std::string result; void* handles[3]{}; int count = 0;
    auto load = [&](const char* name) { void* h = loadInDir(dir, name, result); if (h) handles[count++] = h; return h; };
    if (load("libc++_shared.so") && load("libcxxref_thrower.so")) {
        void* fixture = load("libcxxref_fixture.so");
        if (fixture) {
            auto run = reinterpret_cast<int (*)()>(dlsym(fixture, "inline_fixture_run"));
            if (!run) result = "NDK RTTI control entry missing";
            else { int outcome = run(); result = "NDK inline RTTI result=" + std::to_string(outcome) + (outcome == 99 ? " PASS" : " FAIL"); }
        }
    }
    while (count) dlclose(handles[--count]);
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeInlineRtti(JNIEnv* env, jclass, jstring directory, jboolean globalThrower) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Could not read probe directory");
    std::string dir(utf); env->ReleaseStringUTFChars(directory, utf);
    std::string result;
    void* handles[4]{}; int count = 0;
    auto load = [&](const char* name) { void* h = loadInDir(dir, name, result); if (h) handles[count++] = h; return h; };
    if (load("libgcc_s.so.1") && load("libstdc++.so.6")) {
        void* thrower = nullptr;
        if (globalThrower) {
            thrower = dlopen((dir + "/libcxxinline_thrower.so").c_str(), RTLD_NOW | RTLD_GLOBAL);
            if (thrower) handles[count++] = thrower;
            else { const char* e = dlerror(); result = std::string("Inline thrower load failed: ") + (e ? e : "unknown"); }
        } else thrower = load("libcxxinline_thrower.so");
        if (thrower) {
        void* fixture = load("libcxxinline_fixture.so");
        if (fixture) {
            auto run = reinterpret_cast<int (*)()>(dlsym(fixture, "inline_fixture_run"));
            if (!run) result = "Inline RTTI fixture entry missing";
            else { int outcome = run(); result = std::string(globalThrower ? "Global" : "Local") + " inline RTTI result=" + std::to_string(outcome) + (outcome == 99 ? " PASS" : " FAIL"); }
        }
        }
    }
    while (count) dlclose(handles[--count]);
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeErrno(JNIEnv* env, jclass, jstring directory) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Could not read probe directory");
    std::string dir(utf); env->ReleaseStringUTFChars(directory, utf);
    std::string result;
    void* bridge = loadInDir(dir, "libutlibcprobe.so", result);
    if (bridge) {
        auto location = reinterpret_cast<int* (*)()>(dlvsym(bridge, "__errno_location", "GLIBC_2.17"));
        if (!location) result = "errno bridge symbol lookup FAIL";
        else {
            int saved = errno; int* parent = location(); *parent = 123;
            bool childOk = false;
            std::thread worker([&] { int* child = location(); *child = 234; childOk = child != parent && *child == 234 && errno == 234; });
            worker.join();
            bool ok = childOk && *parent == 123 && errno == 123;
            errno = saved;
            result = ok ? "errno bridge thread-local controls PASS" : "errno bridge thread-local controls FAIL";
        }
        dlclose(bridge);
    }
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeGcc(JNIEnv* env, jclass, jstring directory) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Could not read probe directory");
    std::string dir(utf); env->ReleaseStringUTFChars(directory, utf);
    std::string result;
    void* gcc = loadInDir(dir, "libgcc_s.so.1", result);
    if (gcc) {
        auto from = reinterpret_cast<long double (*)(int)>(dlvsym(gcc, "__floatsitf", "GCC_3.0"));
        auto add = reinterpret_cast<long double (*)(long double, long double)>(dlvsym(gcc, "__addtf3", "GCC_3.0"));
        auto to = reinterpret_cast<int (*)(long double)>(dlvsym(gcc, "__fixtfsi", "GCC_3.0"));
        void* unwind = dlvsym(gcc, "_Unwind_Resume", "GCC_3.0");
        bool ok = from && add && to && unwind && to(add(from(19), from(23))) == 42;
        result = ok ? "GCC bridge binary128 controls PASS; unwind semantics untested" : "GCC bridge binary128 controls FAIL";
        dlclose(gcc);
    }
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeMath(JNIEnv* env, jclass, jstring directory) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Could not read probe directory");
    std::string dir(utf); env->ReleaseStringUTFChars(directory, utf);
    std::string result;
    void* math = loadInDir(dir, "libm.so.6", result);
    if (math) {
        auto cosine = reinterpret_cast<double (*)(double)>(dlvsym(math, "cos", "GLIBC_2.17"));
        auto power = reinterpret_cast<double (*)(double, double)>(dlvsym(math, "pow", "GLIBC_2.17"));
        auto root = reinterpret_cast<float (*)(float)>(dlvsym(math, "sqrtf", "GLIBC_2.17"));
        bool ok = cosine && power && root && cosine(0.0) == 1.0 && power(2.0, 3.0) == 8.0 && root(9.0f) == 3.0f;
        result = ok ? "Math bridge basic numerical controls PASS" : "Math bridge controls FAIL";
        dlclose(math);
    }
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeCxx(JNIEnv* env, jclass, jstring directory, jboolean withCore) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Could not read probe directory");
    std::string dir(utf); env->ReleaseStringUTFChars(directory, utf);
    std::string result;
    void* handles[6]{}; int count = 0;
    auto load = [&](const char* name) { void* h = loadInDir(dir, name, result); if (h) handles[count++] = h; return h; };
    if (withCore) {
        if (load("libSDL2-2.0.so.0") && load("libdl.so.2") && load("libstdc++.so.6") && load("libm.so.6") && load("libgcc_s.so.1")) {
            if (load("Core.so")) result = "Core loaded; constructors may have run. Engine initialization not called.";
        }
    } else if (load("libgcc_s.so.1") && load("libstdc++.so.6") && load("libcxxthrower.so")) {
        void* fixture = load("libcxxfixture.so");
        if (fixture) {
            auto run = reinterpret_cast<int (*)()>(dlsym(fixture, "fixture_run"));
            if (!run) result = "C++ fixture entry missing";
            else { int outcome = run(); result = outcome == 1 ? "C++ cross-library exception, unwind cleanup, RTTI and allocation PASS" : "C++ fixture FAIL code=" + std::to_string(outcome); }
        }
    }
    while (count) dlclose(handles[--count]);
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeDlBridge(JNIEnv* env, jclass, jstring directory) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Could not read probe directory");
    std::string path = std::string(utf) + "/libdl.so.2";
    env->ReleaseStringUTFChars(directory, utf);
    void* bridge = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
    std::string result;
    if (!bridge) {
        const char* error = dlerror(); result = std::string("Bridge load failed: ") + (error ? error : "unknown");
    } else {
        using Open = void* (*)(const char*, int);
        using Symbol = void* (*)(void*, const char*);
        using Close = int (*)(void*);
        using Error = char* (*)();
        auto open = reinterpret_cast<Open>(dlvsym(bridge, "dlopen", "GLIBC_2.17"));
        auto sym = reinterpret_cast<Symbol>(dlvsym(bridge, "dlsym", "GLIBC_2.17"));
        auto close = reinterpret_cast<Close>(dlvsym(bridge, "dlclose", "GLIBC_2.17"));
        auto error = reinterpret_cast<Error>(dlvsym(bridge, "dlerror", "GLIBC_2.17"));
        if (!open || !sym || !close || !error) result = "Bridge versioned export lookup FAIL";
        else {
            void* math = open("libm.so", RTLD_NOW | RTLD_LOCAL);
            void* cosine = math ? sym(math, "cos") : nullptr;
            int closeResult = math ? close(math) : -1;
            void* missing = open("libut99_probe_missing.so", RTLD_NOW | RTLD_LOCAL);
            const char* missingError = error();
            bool ok = math && cosine && closeResult == 0 && !missing && missingError;
            result = ok ? "Bridge positive and missing-library controls PASS" : "Bridge controls FAIL";
            if (missing) close(missing);
        }
        dlclose(bridge);
    }
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeSdlDlCore(JNIEnv* env, jclass, jstring directory) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Could not read probe directory");
    std::string dir(utf); env->ReleaseStringUTFChars(directory, utf);
    void* sdl = dlopen((dir + "/libSDL2-2.0.so.0").c_str(), RTLD_NOW | RTLD_LOCAL);
    std::string result;
    if (!sdl) { const char* e = dlerror(); result = std::string("SDL preload failed: ") + (e ? e : "unknown"); }
    else {
        void* bridge = dlopen((dir + "/libdl.so.2").c_str(), RTLD_NOW | RTLD_LOCAL);
        if (!bridge) { const char* e = dlerror(); result = std::string("Bridge preload failed: ") + (e ? e : "unknown"); }
        else {
            void* core = dlopen((dir + "/Core.so").c_str(), RTLD_NOW | RTLD_LOCAL);
            if (!core) { const char* e = dlerror(); result = std::string("SDL and bridge preloaded; Core load failed: ") + (e ? e : "unknown"); }
            else { result = "Core loaded; constructors may have run. Engine initialization not called."; dlclose(core); }
            dlclose(bridge);
        }
        dlclose(sdl);
    }
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_probeSdlCore(JNIEnv* env, jclass, jstring directory) {
    const char* utf = env->GetStringUTFChars(directory, nullptr);
    if (!utf) return env->NewStringUTF("Could not read probe directory");
    std::string dir(utf);
    env->ReleaseStringUTFChars(directory, utf);
    std::string sdl = dir + "/libSDL2-2.0.so.0";
    std::string core = dir + "/Core.so";
    void* sdlHandle = dlopen(sdl.c_str(), RTLD_NOW | RTLD_LOCAL);
    std::string result;
    if (!sdlHandle) {
        const char* error = dlerror();
        result = std::string("SDL preload failed: ") + (error ? error : "unknown error");
    } else {
        void* coreHandle = dlopen(core.c_str(), RTLD_NOW | RTLD_LOCAL);
        if (!coreHandle) {
            const char* error = dlerror();
            result = std::string("SDL preload succeeded; Core load failed: ") + (error ? error : "unknown error");
        } else {
            result = "SDL and Core loaded; constructors may have run. Engine initialization not called.";
            dlclose(coreHandle);
        }
        dlclose(sdlHandle);
    }
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_ut99android_host_MainActivity_runProbe(JNIEnv* env, jclass, jstring path, jboolean original) {
    const char* utf = env->GetStringUTFChars(path, nullptr);
    if (!utf) return env->NewStringUTF("Could not read probe path");
    std::string name(utf);
    env->ReleaseStringUTFChars(path, utf);
    dlerror();
    void* handle = dlopen(name.c_str(), RTLD_NOW | RTLD_LOCAL);
    std::string result;
    if (!handle) {
        const char* error = dlerror();
        result = std::string(original ? "Core.so load failed: " : "Fixture load failed: ") + (error ? error : "unknown error");
    } else if (original) {
        result = "Core.so loaded; constructors may have run. Engine initialization was not called.";
        dlclose(handle);
    } else {
        dlerror();
        auto step = reinterpret_cast<int (*)()>(dlsym(handle, "probe_step"));
        const char* error = dlerror();
        if (error || !step) result = std::string("Fixture symbol failed: ") + (error ? error : "missing symbol");
        else {
            int first = step(), second = step(), other = 0;
            std::thread worker([&] { other = step(); });
            worker.join();
            result = "Fixture results main=" + std::to_string(first) + "," + std::to_string(second) + " thread=" + std::to_string(other) + ((first == 18 && second == 19 && other == 18) ? " PASS" : " FAIL");
        }
        dlclose(handle);
    }
    __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_org_ut99android_host_MainActivity_lifecycle(JNIEnv* env, jclass, jstring event) {
    const char* s = env->GetStringUTFChars(event, nullptr);
    if (s) { __android_log_print(ANDROID_LOG_INFO, "UT99Host", "%s", s); env->ReleaseStringUTFChars(event, s); }
}
extern "C" JNIEXPORT void JNICALL
Java_org_ut99android_host_MainActivity_renderFrame(JNIEnv* env, jclass, jobject surface) {
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) return;
    ANativeWindow_setBuffersGeometry(window, 0, 0, WINDOW_FORMAT_RGBA_8888);
    ANativeWindow_Buffer buffer{};
    if (ANativeWindow_lock(window, &buffer, nullptr) == 0) {
        auto* pixels = static_cast<uint32_t*>(buffer.bits);
        for (int y = 0; y < buffer.height; ++y)
            for (int x = 0; x < buffer.width; ++x)
                pixels[y * buffer.stride + x] = ((x / 64 + y / 64) % 2) ? 0xff513c29u : 0xff382a20u;
        ANativeWindow_unlockAndPost(window);
        __android_log_print(ANDROID_LOG_INFO, "UT99Host", "native diagnostic frame posted %dx%d", buffer.width, buffer.height);
    } else __android_log_print(ANDROID_LOG_ERROR, "UT99Host", "surface lock failed");
    ANativeWindow_release(window);
}
