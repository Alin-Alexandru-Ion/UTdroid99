#include "stat_probe.h"
#include <errno.h>
#include <stddef.h>
#include <string.h>
#include <sys/stat.h>

#if !defined(__aarch64__) || !defined(__LP64__)
#error This experiment only supports the Android AArch64 LP64 ABI.
#endif
_Static_assert(sizeof(UtGlibcStat) == 128, "unexpected glibc stat size");
_Static_assert(sizeof(struct stat) == 128, "unexpected Android stat size");
_Static_assert(offsetof(UtGlibcStat, ino) == offsetof(struct stat, st_ino), "inode offset");
_Static_assert(offsetof(UtGlibcStat, mode) == offsetof(struct stat, st_mode), "mode offset");
_Static_assert(offsetof(UtGlibcStat, size) == offsetof(struct stat, st_size), "size offset");
_Static_assert(offsetof(UtGlibcStat, blksize) == offsetof(struct stat, st_blksize), "block size offset");
_Static_assert(offsetof(UtGlibcStat, blocks) == offsetof(struct stat, st_blocks), "blocks offset");
_Static_assert(offsetof(UtGlibcStat, atime_sec) == offsetof(struct stat, st_atim), "atime offset");
_Static_assert(offsetof(UtGlibcStat, mtime_sec) == offsetof(struct stat, st_mtim), "mtime offset");
_Static_assert(offsetof(UtGlibcStat, ctime_sec) == offsetof(struct stat, st_ctim), "ctime offset");

static void copy_stat(UtGlibcStat* out, const struct stat* in) {
    UtGlibcStat result;
    memset(&result, 0, sizeof(result));
    result.dev = in->st_dev; result.ino = in->st_ino;
    result.mode = in->st_mode; result.nlink = in->st_nlink;
    result.uid = in->st_uid; result.gid = in->st_gid; result.rdev = in->st_rdev;
    result.size = in->st_size; result.blksize = in->st_blksize; result.blocks = in->st_blocks;
    result.atime_sec = in->st_atim.tv_sec; result.atime_nsec = in->st_atim.tv_nsec;
    result.mtime_sec = in->st_mtim.tv_sec; result.mtime_nsec = in->st_mtim.tv_nsec;
    result.ctime_sec = in->st_ctim.tv_sec; result.ctime_nsec = in->st_ctim.tv_nsec;
    *out = result;
}
int ut_gxstat(int version, const char* path, UtGlibcStat* out) {
    if (version != 0) { errno = EINVAL; return -1; }
    if (!path || !out) { errno = EFAULT; return -1; }
    struct stat in;
    if (stat(path, &in) != 0) return -1;
    copy_stat(out, &in); return 0;
}
int ut_gfxstat(int version, int fd, UtGlibcStat* out) {
    if (version != 0) { errno = EINVAL; return -1; }
    if (!out) { errno = EFAULT; return -1; }
    struct stat in;
    if (fstat(fd, &in) != 0) return -1;
    copy_stat(out, &in); return 0;
}
