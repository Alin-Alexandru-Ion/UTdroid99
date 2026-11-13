#include "stat_probe.h"
/* Test-only versioned aliases; no other GNU libc behavior is implied. */
int shim_xstat(int version, const char* path, UtGlibcStat* out) { return ut_gxstat(version, path, out); }
int shim_fxstat(int version, int fd, UtGlibcStat* out) { return ut_gfxstat(version, fd, out); }
__asm__(".symver shim_xstat,__xstat@@GLIBC_2.17");
__asm__(".symver shim_fxstat,__fxstat@@GLIBC_2.17");
