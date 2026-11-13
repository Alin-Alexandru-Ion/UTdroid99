#include <time.h>
/* Research-only versioned symbol adapter; not a complete pthread ABI. */
extern int ut_gmutex_init(void*, const void*);
extern int ut_gmutex_lock(void*);
extern int ut_gmutex_unlock(void*);
extern int ut_gmutex_destroy(void*);
extern int ut_gcond_init(void*, const void*);
extern int ut_gcond_wait(void*, void*);
extern int ut_gcond_timedwait(void*, void*, const struct timespec*);
extern int ut_gcond_signal(void*);
extern int ut_gcond_broadcast(void*);
extern int ut_gcond_destroy(void*);
int shim_mutex_init(void* m, const void* a) { return ut_gmutex_init(m, a); }
int shim_mutex_lock(void* m) { return ut_gmutex_lock(m); }
int shim_mutex_unlock(void* m) { return ut_gmutex_unlock(m); }
int shim_mutex_destroy(void* m) { return ut_gmutex_destroy(m); }
int shim_cond_init(void* c, const void* a) { return ut_gcond_init(c, a); }
int shim_cond_wait(void* c, void* m) { return ut_gcond_wait(c, m); }
int shim_cond_timedwait(void* c, void* m, const struct timespec* t) { return ut_gcond_timedwait(c, m, t); }
int shim_cond_signal(void* c) { return ut_gcond_signal(c); }
int shim_cond_broadcast(void* c) { return ut_gcond_broadcast(c); }
int shim_cond_destroy(void* c) { return ut_gcond_destroy(c); }
__asm__(".symver shim_mutex_init,pthread_mutex_init@@GLIBC_2.17");
__asm__(".symver shim_mutex_lock,pthread_mutex_lock@@GLIBC_2.17");
__asm__(".symver shim_mutex_unlock,pthread_mutex_unlock@@GLIBC_2.17");
__asm__(".symver shim_mutex_destroy,pthread_mutex_destroy@@GLIBC_2.17");
__asm__(".symver shim_cond_init,pthread_cond_init@@GLIBC_2.17");
__asm__(".symver shim_cond_wait,pthread_cond_wait@@GLIBC_2.17");
__asm__(".symver shim_cond_timedwait,pthread_cond_timedwait@@GLIBC_2.17");
__asm__(".symver shim_cond_signal,pthread_cond_signal@@GLIBC_2.17");
__asm__(".symver shim_cond_broadcast,pthread_cond_broadcast@@GLIBC_2.17");
__asm__(".symver shim_cond_destroy,pthread_cond_destroy@@GLIBC_2.17");
