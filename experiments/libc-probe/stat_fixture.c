#include "stat_probe.h"
#include <errno.h>
#include <string.h>
extern int g_xstat(int, const char*, UtGlibcStat*);
extern int g_fxstat(int, int, UtGlibcStat*);
__asm__(".symver g_xstat,__xstat@GLIBC_2.17");
__asm__(".symver g_fxstat,__fxstat@GLIBC_2.17");
int stat_fixture_run(const char* path, int fd, int64_t expected_size) {
    UtGlibcStat by_path, by_fd;
    if (g_xstat(0, path, &by_path) || g_fxstat(0, fd, &by_fd)) return 1;
    if (by_path.size != expected_size || by_fd.size != expected_size || by_path.ino != by_fd.ino) return 2;
    UtGlibcStat sentinel, copy; memset(&sentinel, 0x5a, sizeof(sentinel)); copy = sentinel;
    errno = 0;
    if (g_xstat(1, path, &sentinel) != -1 || errno != EINVAL || memcmp(&sentinel, &copy, sizeof(copy))) return 3;
    return 0;
}
