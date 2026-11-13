#include <stdio.h>
#include <errno.h>
#include <unistd.h>
#define IMPORT(alias, name) __asm__(".symver " #alias "," #name "@GLIBC_2.17")
extern size_t g_fread(void*, size_t, size_t, FILE*); IMPORT(g_fread, fread);
extern int g_fseek(FILE*, long, int); IMPORT(g_fseek, fseek);
extern int g_fileno(FILE*); IMPORT(g_fileno, fileno);
extern int g_ferror(FILE*); IMPORT(g_ferror, ferror);
extern int g_fclose(FILE*); IMPORT(g_fclose, fclose);
extern int g_fprintf_chk(FILE*, int, const char*, ...); IMPORT(g_fprintf_chk, __fprintf_chk);
extern int g_memcmp(const void*, const void*, size_t); IMPORT(g_memcmp, memcmp);
int partial_stdio_test(int fd) {
    int duplicate = dup(fd);
    if (duplicate < 0) return 1;
    FILE* file = fdopen(duplicate, "r");
    if (!file) { close(duplicate); return 2; }
    char bytes[32];
    int ok = g_fileno(file) == duplicate && g_fseek(file, 0, SEEK_SET) == 0;
    size_t got = g_fread(bytes, 1, sizeof(bytes), file);
    ok = ok && got == 15 && !g_memcmp(bytes, "partial-fixture", 15) && g_ferror(file) == 0;
    int closed = g_fclose(file);
    if (!ok || closed) return 3;
    int ends[2]; if (pipe(ends)) return 4;
    FILE* output = fdopen(ends[1], "w");
    if (!output) { close(ends[0]); close(ends[1]); return 5; }
    int written = g_fprintf_chk(output, 1, "Map %s %d", "Deck", 16);
    int marker = 55; errno = 0;
    int rejected = g_fprintf_chk(output, 1, "%n", &marker); int rejected_errno = errno;
    int flushed = fflush(output); closed = g_fclose(output);
    char text[32]; ssize_t count = read(ends[0], text, sizeof(text)); close(ends[0]);
    return written == 11 && rejected == -1 && rejected_errno == ENOTSUP && marker == 55 && !flushed && !closed && count == 11 && !g_memcmp(text, "Map Deck 16", 11) ? 0 : 6;
}
