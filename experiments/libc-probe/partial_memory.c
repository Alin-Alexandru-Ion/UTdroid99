#include <stddef.h>
#include <stdint.h>
#include <wchar.h>
#define IMPORT(alias, name) __asm__(".symver " #alias "," #name "@GLIBC_2.17")
extern void* g_memcpy(void*, const void*, size_t); IMPORT(g_memcpy, memcpy);
extern void* g_memmove(void*, const void*, size_t); IMPORT(g_memmove, memmove);
extern void* g_memset(void*, int, size_t); IMPORT(g_memset, memset);
extern int g_memcmp(const void*, const void*, size_t); IMPORT(g_memcmp, memcmp);
extern size_t g_strlen(const char*); IMPORT(g_strlen, strlen);
extern size_t g_strnlen(const char*, size_t); IMPORT(g_strnlen, strnlen);
extern char* g_strchr(const char*, int); IMPORT(g_strchr, strchr);
extern char* g_strdup(const char*); IMPORT(g_strdup, strdup);
extern wchar_t* g_wcscpy(wchar_t*, const wchar_t*); IMPORT(g_wcscpy, wcscpy);
extern size_t g_wcslen(const wchar_t*); IMPORT(g_wcslen, wcslen);
extern wchar_t* g_wcsncpy(wchar_t*, const wchar_t*, size_t); IMPORT(g_wcsncpy, wcsncpy);
extern int g_wcscmp(const wchar_t*, const wchar_t*); IMPORT(g_wcscmp, wcscmp);
extern int g_wcsncmp(const wchar_t*, const wchar_t*, size_t); IMPORT(g_wcsncmp, wcsncmp);
extern wchar_t* g_wcscat(wchar_t*, const wchar_t*); IMPORT(g_wcscat, wcscat);
extern wchar_t* g_wcschr(const wchar_t*, wchar_t); IMPORT(g_wcschr, wcschr);
extern wchar_t* g_wcsstr(const wchar_t*, const wchar_t*); IMPORT(g_wcsstr, wcsstr);
extern size_t g_wcsnlen(const wchar_t*, size_t); IMPORT(g_wcsnlen, wcsnlen);
extern wchar_t* g_wcsdup(const wchar_t*); IMPORT(g_wcsdup, wcsdup);
extern void* g_calloc(size_t, size_t); IMPORT(g_calloc, calloc);
extern void* g_realloc(void*, size_t); IMPORT(g_realloc, realloc);
extern void g_free(void*); IMPORT(g_free, free);
extern void* g_memalign(size_t, size_t); IMPORT(g_memalign, memalign);

int partial_memory_test(void) {
    char b[16];
    if (g_memset(b, 'x', sizeof(b)) != b || g_memcpy(b, "abcdef", 7) != b) return 1;
    if (g_memmove(b + 1, b, 5) != b + 1 || g_memcmp(b, "aabcde", 6)) return 2;
    if (g_strlen(b) != 6 || g_strnlen(b, 3) != 3 || g_strchr(b, 'd') != b + 4) return 3;
    wchar_t w[16], pad[4] = {9, 9, 9, 9};
    if (g_wcscpy(w, L"De") != w || g_wcslen(w) != 2 || g_wcscat(w, L"ck") != w) return 4;
    if (g_wcscmp(w, L"Deck") || g_wcsncmp(w, L"Deer", 2) || g_wcsnlen(w, 3) != 3) return 5;
    if (g_wcschr(w, L'c') != w + 2 || g_wcsstr(w, L"ec") != w + 1) return 6;
    if (g_wcsncpy(pad, L"a", 4) != pad || pad[0] != L'a' || pad[1] || pad[2] || pad[3]) return 7;
    char* duplicate = g_strdup("Deck"); wchar_t* wide_duplicate = g_wcsdup(L"Deck");
    if (!duplicate || !wide_duplicate) { if (duplicate) g_free(duplicate); if (wide_duplicate) g_free(wide_duplicate); return 14; }
    int same = !g_memcmp(duplicate, "Deck", 5) && !g_wcscmp(wide_duplicate, L"Deck");
    g_free(duplicate); g_free(wide_duplicate); if (!same) return 15;
    unsigned char* allocation = g_calloc(4, 4);
    if (!allocation) return 8;
    for (int i = 0; i < 16; ++i) if (allocation[i]) { g_free(allocation); return 9; }
    allocation[0] = 0x5a;
    unsigned char* larger = g_realloc(allocation, 32);
    if (!larger) { g_free(allocation); return 10; }
    int preserved = larger[0] == 0x5a; g_free(larger);
    if (!preserved) return 11;
    void* aligned = g_memalign(64, 17);
    if (!aligned) return 12;
    int correct = ((uintptr_t)aligned & 63) == 0; g_free(aligned);
    return correct ? 0 : 13;
}
