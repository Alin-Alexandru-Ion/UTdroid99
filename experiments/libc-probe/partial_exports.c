#include <stdarg.h>
#include <stdio.h>
#include <wchar.h>
#include <stdlib.h>
#include <string.h>
/* Incomplete, test-only libc surface. Never use this as a complete libc. */
extern int ut_gvfprintf_chk(FILE*, int, const char*, va_list);
extern int ut_gvfwprintf_chk(FILE*, int, const wchar_t*, va_list);
extern int ut_gvsnprintf_chk(char*, size_t, int, size_t, const char*, va_list);
extern int ut_gvsprintf_chk(char*, int, size_t, const char*, va_list);
extern void* bionic_memset_direct(void*, int, size_t);
__asm__(".symver bionic_memset_direct,memset@LIBC");
void* shim_memset_chk(void* dst, int c, size_t n, size_t object_size) {
    if (n > object_size) abort();
    return bionic_memset_direct(dst, c, n);
}
int shim_snprintf_chk(char* dst, size_t n, int flag, size_t object_size, const char* fmt, ...) {
    va_list args; va_start(args, fmt); int rc = ut_gvsnprintf_chk(dst, n, flag, object_size, fmt, args); va_end(args); return rc;
}
int shim_sprintf_chk(char* dst, int flag, size_t object_size, const char* fmt, ...) {
    va_list args; va_start(args, fmt); int rc = ut_gvsprintf_chk(dst, flag, object_size, fmt, args); va_end(args); return rc;
}
__asm__(".symver shim_memset_chk,__memset_chk@@GLIBC_2.17");
__asm__(".symver shim_snprintf_chk,__snprintf_chk@@GLIBC_2.17");
__asm__(".symver shim_sprintf_chk,__sprintf_chk@@GLIBC_2.17");
int shim_fprintf_chk(FILE* file, int flag, const char* fmt, ...) {
    va_list args; va_start(args, fmt); int rc = ut_gvfprintf_chk(file, flag, fmt, args); va_end(args); return rc;
}
int shim_printf_chk(int flag, const char* fmt, ...) {
    va_list args; va_start(args, fmt); int rc = ut_gvfprintf_chk(stdout, flag, fmt, args); va_end(args); return rc;
}
int shim_wprintf_chk(int flag, const wchar_t* fmt, ...) {
    va_list args; va_start(args, fmt); int rc = ut_gvfwprintf_chk(stdout, flag, fmt, args); va_end(args); return rc;
}
__asm__(".symver shim_fprintf_chk,__fprintf_chk@@GLIBC_2.17");
__asm__(".symver shim_printf_chk,__printf_chk@@GLIBC_2.17");
__asm__(".symver shim_wprintf_chk,__wprintf_chk@@GLIBC_2.17");
