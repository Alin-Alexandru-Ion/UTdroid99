#include <stdint.h>
#include <stddef.h>
#include <setjmp.h>
#include <pthread.h>
// GNU AArch64 jmp_buf: 22 64-bit words, mask flag/alignment and 128-byte mask.
typedef struct { uint64_t regs[22]; int saved; int padding; unsigned char mask[128]; } gnu_jump_storage;
_Static_assert(sizeof(gnu_jump_storage) == 312, "GNU AArch64 jump buffer size");
_Static_assert(sizeof(jmp_buf) <= sizeof(gnu_jump_storage), "Bionic buffer exceeds GNU storage");
extern int b_setjmp(void*) __attribute__((returns_twice));
extern void b_longjmp(void*, int) __attribute__((noreturn));
#ifndef BASELINE_ONLY
extern int g_setjmp(void*) __attribute__((returns_twice));
extern void g_longjmp(void*, int) __attribute__((noreturn));
__asm__(".symver g_setjmp,_setjmp@GLIBC_2.17");
__asm__(".symver g_longjmp,__longjmp_chk@GLIBC_2.17");
#else
#define g_setjmp b_setjmp
#define g_longjmp b_longjmp
#endif
__asm__(".symver b_setjmp,_setjmp@LIBC");
__asm__(".symver b_longjmp,_longjmp@LIBC");
typedef struct { uint64_t before; gnu_jump_storage env; uint64_t after; } protected_buffer;
__attribute__((noinline)) static void jump_from_nested(void* env, int value, int baseline) { if (baseline) b_longjmp(env, value); else g_longjmp(env, value); }
static int one_jump(int value, int expected, int baseline) {
    protected_buffer b = { .before=0x123456789abcdef0ULL, .after=0xfedcba9876543210ULL };
    volatile int visits = 0;
    volatile int result = baseline ? b_setjmp(&b.env) : g_setjmp(&b.env);
    if (++visits > 3) return 0;
    if (!result) jump_from_nested(&b.env, value, baseline);
    return result == expected && b.before == 0x123456789abcdef0ULL && b.after == 0xfedcba9876543210ULL;
}
static void* worker(void* arg) { return (void*)(uintptr_t)one_jump(7, 7, (int)(uintptr_t)arg); }
int jump_fixture_run(int baseline) {
    if (!one_jump(37, 37, baseline)) return 1;
    if (!one_jump(0, 1, baseline)) return 4;
    if (!one_jump(-9, -9, baseline)) return 5;
    pthread_t thread;
    if (pthread_create(&thread, NULL, worker, (void*)(uintptr_t)baseline)) return 2;
    void* result = NULL;
    if (pthread_join(thread, &result) || (uintptr_t)result != 1) return 3;
    return 0;
}
