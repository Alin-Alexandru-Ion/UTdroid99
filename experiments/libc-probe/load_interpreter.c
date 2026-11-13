// Dependency-shaped test shim, not a Linux dynamic loader.
#include <stdint.h>
extern uintptr_t android_guard;
__asm__(".symver android_guard,__stack_chk_guard@LIBC");
uintptr_t gnu_loader_guard;
__asm__(".symver gnu_loader_guard,__stack_chk_guard@@GLIBC_2.17");
__attribute__((constructor)) static void copy_guard(void) { gnu_loader_guard = android_guard; }
