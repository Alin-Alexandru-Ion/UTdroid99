#include <stdint.h>
#include <stdio.h>
/* Test-only initial snapshots. Reassignment and cross-libc FILE internals are
 * not established; this is not a general glibc standard-stream adapter. */
extern FILE* bionic_stdin;
extern FILE* bionic_stdout;
extern FILE* bionic_stderr;
extern uintptr_t bionic_guard;
__asm__(".symver bionic_stdin,stdin@LIBC");
__asm__(".symver bionic_stdout,stdout@LIBC");
__asm__(".symver bionic_stderr,stderr@LIBC");
__asm__(".symver bionic_guard,__stack_chk_guard@LIBC");
FILE* glibc_stdin;
FILE* glibc_stdout;
FILE* glibc_stderr;
uintptr_t glibc_guard;
__asm__(".symver glibc_stdin,stdin@@GLIBC_2.17");
__asm__(".symver glibc_stdout,stdout@@GLIBC_2.17");
__asm__(".symver glibc_stderr,stderr@@GLIBC_2.17");
__asm__(".symver glibc_guard,__stack_chk_guard@@GLIBC_2.17");
__attribute__((constructor)) static void initialize_probe_data(void) {
    glibc_stdin = bionic_stdin;
    glibc_stdout = bionic_stdout;
    glibc_stderr = bionic_stderr;
    glibc_guard = bionic_guard;
}
