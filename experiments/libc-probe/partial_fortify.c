#include <errno.h>
#include <signal.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>
#define IMPORT(alias, name) __asm__(".symver " #alias "," #name "@GLIBC_2.17")
extern void* g_memset_chk(void*, int, size_t, size_t); IMPORT(g_memset_chk, __memset_chk);
extern int g_snprintf_chk(char*, size_t, int, size_t, const char*, ...); IMPORT(g_snprintf_chk, __snprintf_chk);
extern int g_sprintf_chk(char*, int, size_t, const char*, ...); IMPORT(g_sprintf_chk, __sprintf_chk);

static int overflow_aborts(int which) {
    pid_t child = fork();
    if (child < 0) return 0;
    if (!child) {
        char b[4];
        if (which == 0) g_memset_chk(b, 0, 5, sizeof(b));
        if (which == 1) g_snprintf_chk(b, 5, 1, sizeof(b), "%s", "x");
        if (which == 2) g_sprintf_chk(b, 1, sizeof(b), "%s", "long");
        _exit(0);
    }
    int status;
    return waitpid(child, &status, 0) == child && WIFSIGNALED(status) && WTERMSIG(status) == SIGABRT;
}
int partial_fortify_test(void) {
    char b[16];
    if (g_memset_chk(b, 'x', 3, sizeof(b)) != b || memcmp(b, "xxx", 3)) return 1;
    if (g_sprintf_chk(b, 1, sizeof(b), "/proc/self/fd/%d", 7) != 15 || strcmp(b, "/proc/self/fd/7")) return 2;
    if (g_snprintf_chk(b, 5, 1, sizeof(b), "%s-%d", "Deck", 16) != 7 || strcmp(b, "Deck")) return 3;
    if (g_sprintf_chk(b, 1, SIZE_MAX, "%d", 42) != 2 || strcmp(b, "42")) return 4;
    int marker = 17; errno = 0;
    if (g_snprintf_chk(b, sizeof(b), 1, sizeof(b), "%n", &marker) != -1 || errno != ENOTSUP || marker != 17) return 5;
    for (int i = 0; i < 3; ++i) if (!overflow_aborts(i)) return 6 + i;
    return 0;
}
