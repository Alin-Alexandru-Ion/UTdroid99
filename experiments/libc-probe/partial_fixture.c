#include "stat_probe.h"
#include <errno.h>
#include <stdint.h>
#include <time.h>
extern int g_mutex_init(void*, const void*);
extern int g_mutex_lock(void*);
extern int g_mutex_unlock(void*);
extern int g_mutex_destroy(void*);
extern int g_cond_init(void*, const void*);
extern int g_cond_timedwait(void*, void*, const struct timespec*);
extern int g_cond_destroy(void*);
extern int g_xstat(int, const char*, UtGlibcStat*);
extern const uint16_t** g_ctype(void);
extern int* g_errno(void);
__asm__(".symver g_mutex_init,pthread_mutex_init@GLIBC_2.17");
__asm__(".symver g_mutex_lock,pthread_mutex_lock@GLIBC_2.17");
__asm__(".symver g_mutex_unlock,pthread_mutex_unlock@GLIBC_2.17");
__asm__(".symver g_mutex_destroy,pthread_mutex_destroy@GLIBC_2.17");
__asm__(".symver g_cond_init,pthread_cond_init@GLIBC_2.17");
__asm__(".symver g_cond_timedwait,pthread_cond_timedwait@GLIBC_2.17");
__asm__(".symver g_cond_destroy,pthread_cond_destroy@GLIBC_2.17");
__asm__(".symver g_xstat,__xstat@GLIBC_2.17");
__asm__(".symver g_ctype,__ctype_b_loc@GLIBC_2.17");
__asm__(".symver g_errno,__errno_location@GLIBC_2.17");
extern int partial_memory_test(void);
extern int partial_data_test(void);
extern int partial_stdio_test(int fd);
extern int partial_fortify_test(void);
int partial_fixture_run(const char* path, int fd, int64_t expected_size) {
    int memory = partial_memory_test(); if (memory) return 10 + memory;
    if (partial_data_test()) return 30;
    if (partial_stdio_test(fd)) return 31;
    int fortify = partial_fortify_test(); if (fortify) return 40 + fortify;
    UtGlibcStat st;
    if (g_xstat(0, path, &st) || st.size != expected_size) return 1;
    if (!((*g_ctype())['A'] & 0x0100) || ((*g_ctype())['A'] & 0x0002)) return 2;
    int* error = g_errno(); int saved = *error; *error = 123; if (*error != 123) return 3; *error = saved;
    _Alignas(8) unsigned char event[120] = {0};
    void* cond = event + 8; void* mutex = event + 56;
    if (g_mutex_init(mutex, 0)) return 4;
    if (g_cond_init(cond, 0)) { g_mutex_destroy(mutex); return 5; }
    if (g_mutex_lock(mutex)) { g_cond_destroy(cond); g_mutex_destroy(mutex); return 6; }
    struct timespec until;
    if (clock_gettime(CLOCK_REALTIME, &until)) { g_mutex_unlock(mutex); g_cond_destroy(cond); g_mutex_destroy(mutex); return 7; }
    until.tv_nsec += 20000000;
    if (until.tv_nsec >= 1000000000) { ++until.tv_sec; until.tv_nsec -= 1000000000; }
    int rc = g_cond_timedwait(cond, mutex, &until);
    int a = g_mutex_unlock(mutex), b = g_cond_destroy(cond), c = g_mutex_destroy(mutex);
    return rc == ETIMEDOUT && !a && !b && !c ? 0 : 8;
}
