#include <stdint.h>
#include <stdio.h>
#define VERSION(alias, name, version) __asm__(".symver " #alias "," #name "@" #version)
extern FILE* g_in; VERSION(g_in, stdin, GLIBC_2.17);
extern FILE* g_out; VERSION(g_out, stdout, GLIBC_2.17);
extern FILE* g_err; VERSION(g_err, stderr, GLIBC_2.17);
extern uintptr_t g_guard; VERSION(g_guard, __stack_chk_guard, GLIBC_2.17);
extern FILE* b_in; VERSION(b_in, stdin, LIBC);
extern FILE* b_out; VERSION(b_out, stdout, LIBC);
extern FILE* b_err; VERSION(b_err, stderr, LIBC);
extern uintptr_t b_guard; VERSION(b_guard, __stack_chk_guard, LIBC);
int partial_data_test(void) {
    return g_in && g_out && g_err && g_in == b_in && g_out == b_out && g_err == b_err && g_guard == b_guard && g_guard != 0 ? 0 : 1;
}
