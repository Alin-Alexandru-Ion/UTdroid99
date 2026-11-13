#pragma once
#include <stdint.h>
/* Fixed-width Linux AArch64 LP64 layout, not portable to other targets. */
typedef struct {
    uint64_t dev, ino;
    uint32_t mode, nlink, uid, gid;
    uint64_t rdev, pad1;
    int64_t size;
    int32_t blksize, pad2;
    int64_t blocks;
    int64_t atime_sec, atime_nsec, mtime_sec, mtime_nsec, ctime_sec, ctime_nsec;
    uint32_t reserved4, reserved5;
} UtGlibcStat;
int ut_gxstat(int version, const char* path, UtGlibcStat* out);
int ut_gfxstat(int version, int fd, UtGlibcStat* out);
