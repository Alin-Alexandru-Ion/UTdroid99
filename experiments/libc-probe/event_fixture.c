#include <errno.h>
#include <stdint.h>
#include <string.h>
#include <time.h>
extern int g_mutex_init(void*, const void*);
extern int g_mutex_lock(void*);
extern int g_mutex_unlock(void*);
extern int g_mutex_destroy(void*);
extern int g_cond_init(void*, const void*);
extern int g_cond_timedwait(void*, void*, const struct timespec*);
extern int g_cond_destroy(void*);
__asm__(".symver g_mutex_init,pthread_mutex_init@GLIBC_2.17");
__asm__(".symver g_mutex_lock,pthread_mutex_lock@GLIBC_2.17");
__asm__(".symver g_mutex_unlock,pthread_mutex_unlock@GLIBC_2.17");
__asm__(".symver g_mutex_destroy,pthread_mutex_destroy@GLIBC_2.17");
__asm__(".symver g_cond_init,pthread_cond_init@GLIBC_2.17");
__asm__(".symver g_cond_timedwait,pthread_cond_timedwait@GLIBC_2.17");
__asm__(".symver g_cond_destroy,pthread_cond_destroy@GLIBC_2.17");
int event_fixture_run(void) {
    _Alignas(8) unsigned char event[120] = {0};
    void* condition = event + 8; void* mutex = event + 56;
    memset(event + 104, 0xa5, 16);
    if (g_mutex_init(mutex, 0)) return 1;
    if (g_cond_init(condition, 0)) { g_mutex_destroy(mutex); return 2; }
    if (g_mutex_lock(mutex)) { g_cond_destroy(condition); g_mutex_destroy(mutex); return 3; }
    struct timespec until;
    if (clock_gettime(CLOCK_REALTIME, &until)) { g_mutex_unlock(mutex); g_cond_destroy(condition); g_mutex_destroy(mutex); return 4; }
    until.tv_nsec += 30000000;
    if (until.tv_nsec >= 1000000000) { ++until.tv_sec; until.tv_nsec -= 1000000000; }
    int wait_result = g_cond_timedwait(condition, mutex, &until);
    int unlock_result = g_mutex_unlock(mutex);
    int cond_result = g_cond_destroy(condition);
    int mutex_result = g_mutex_destroy(mutex);
    for (int i = 104; i < 120; ++i) if (event[i] != 0xa5) return 5;
    return wait_result == ETIMEDOUT && !unlock_result && !cond_result && !mutex_result ? 0 : 6;
}
