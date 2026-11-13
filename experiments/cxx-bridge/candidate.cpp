// Forces a C++ link; the candidate is an experimental NDK libc++/libc++abi build.
#include <stddef.h>
#include <stdint.h>
#include <string.h>

extern "C" void* ut_original_dynamic_cast(const void*, const void*, const void*, ptrdiff_t);

// OldUnreal's Linux DSOs carry distinct RTTI instances for this class. NDK
// libc++abi fails this particular GCC-produced multiple-inheritance cast. Keep
// its general implementation and correct only the observed exact-type case.
extern "C" void* __dynamic_cast(const void* object, const void* from,
                                 const void* to, ptrdiff_t hint) {
    void* result = ut_original_dynamic_cast(object, from, to, hint);
    if (result || !object || !from || !to || hint != 0) return result;
    auto source = static_cast<const char* const*>(from)[1];
    auto target = static_cast<const char* const*>(to)[1];
    if (!source || !target || strcmp(source, "12FConfigCache") ||
        strcmp(target, "15FConfigCacheIni")) return nullptr;
    auto vtable = *reinterpret_cast<const void* const* const*>(object);
    if (reinterpret_cast<intptr_t>(vtable[-2]) != 0) return nullptr;
    auto actual = static_cast<const char* const*>(vtable[-1]);
    return actual && actual[1] && !strcmp(actual[1], target)
        ? const_cast<void*>(object) : nullptr;
}
extern "C" int ut99_cxx_candidate_marker() { return 469; }
// Experimental candidate; exported C++ guards and sized delete support launcher imports.
