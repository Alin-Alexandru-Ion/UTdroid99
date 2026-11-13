#include <errno.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <wchar.h>

/* Test-only subset: reject unsupported positional, locale-dependent, long
 * double and %n conversions instead of pretending to implement them. FILE*
 * must originate from Bionic; GNU FILE objects are not supported. */
static int safe_narrow(const char* f) {
    if (!f) return 0;
    while (*f) {
        if (*f++ != '%') continue;
        if (*f == '%') { ++f; continue; }
        while (*f && (*f == '-' || *f == '+' || *f == ' ' || *f == '#' || *f == '0')) ++f;
        if (*f == '*') ++f; else while (*f >= '0' && *f <= '9') ++f;
        if (*f == '.') { ++f; if (*f == '*') ++f; else while (*f >= '0' && *f <= '9') ++f; }
        int length = 0;
        if (*f == 'h' || *f == 'l') { char c = *f++; length = c; if (*f == c) { ++f; length = c == 'h' ? 1 : 2; } }
        else if (*f == 'j' || *f == 'z' || *f == 't') length = *f++;
        char c = *f;
        if (!c) return 0;
        if (!(c == 'd' || c == 'i' || c == 'o' || c == 'u' || c == 'x' || c == 'X' ||
              c == 'f' || c == 'F' || c == 'e' || c == 'E' || c == 'g' || c == 'G' ||
              c == 'a' || c == 'A' || c == 'c' || c == 's' || c == 'p')) return 0;
        if ((c == 'c' || c == 's') && length && length != 'l') return 0;
        if (c == 'p' && length) return 0;
        if ((c == 'f' || c == 'F' || c == 'e' || c == 'E' || c == 'g' || c == 'G' || c == 'a' || c == 'A') && length && length != 'l') return 0;
        ++f;
    }
    return 1;
}
static int safe_wide(const wchar_t* f) {
    if (!f) return 0;
    while (*f) {
        if (*f++ != L'%') continue;
        if (*f == L'%') { ++f; continue; }
        while (*f && (*f == L'-' || *f == L'+' || *f == L' ' || *f == L'#' || *f == L'0')) ++f;
        if (*f == L'*') ++f; else while (*f >= L'0' && *f <= L'9') ++f;
        if (*f == L'.') { ++f; if (*f == L'*') ++f; else while (*f >= L'0' && *f <= L'9') ++f; }
        int length = 0;
        if (*f == L'h' || *f == L'l') { wchar_t c = *f++; length = c; if (*f == c) { ++f; length = c == L'h' ? 1 : 2; } }
        else if (*f == L'j' || *f == L'z' || *f == L't') length = *f++;
        wchar_t c = *f;
        if (!c) return 0;
        if (!(c == L'd' || c == L'i' || c == L'o' || c == L'u' || c == L'x' || c == L'X' ||
              c == L'f' || c == L'F' || c == L'e' || c == L'E' || c == L'g' || c == L'G' ||
              c == L'a' || c == L'A' || c == L'c' || c == L's' || c == L'p')) return 0;
        if ((c == L'c' || c == L's') && length && length != L'l') return 0;
        if (c == L'p' && length) return 0;
        if ((c == L'f' || c == L'F' || c == L'e' || c == L'E' || c == L'g' || c == L'G' || c == L'a' || c == L'A') && length && length != L'l') return 0;
        ++f;
    }
    return 1;
}
int ut_gvfprintf_chk(FILE* file, int flag, const char* format, va_list args) {
    if (!file || flag < 0 || flag > 1 || !safe_narrow(format)) { errno = ENOTSUP; return -1; }
    return vfprintf(file, format, args);
}
int ut_gvfwprintf_chk(FILE* file, int flag, const wchar_t* format, va_list args) {
    if (!file || flag < 0 || flag > 1 || !safe_wide(format)) { errno = ENOTSUP; return -1; }
    return vfwprintf(file, format, args);
}
int ut_gvsnprintf_chk(char* dst, size_t limit, int flag, size_t object_size, const char* format, va_list args) {
    if (limit > object_size) abort();
    if (flag < 0 || flag > 1 || !safe_narrow(format)) { errno = ENOTSUP; return -1; }
    return vsnprintf(dst, limit, format, args);
}
int ut_gvsprintf_chk(char* dst, int flag, size_t object_size, const char* format, va_list args) {
    if (flag < 0 || flag > 1 || !safe_narrow(format)) { errno = ENOTSUP; return -1; }
    if (object_size == SIZE_MAX) return vsprintf(dst, format, args);
    int result = vsnprintf(dst, object_size, format, args);
    if (result >= 0 && (size_t)result >= object_size) abort();
    return result;
}
int ut_gfprintf_chk(FILE* file, int flag, const char* format, ...) {
    va_list args; va_start(args, format); int result = ut_gvfprintf_chk(file, flag, format, args); va_end(args); return result;
}
int ut_gprintf_chk(int flag, const char* format, ...) {
    va_list args; va_start(args, format); int result = ut_gvfprintf_chk(stdout, flag, format, args); va_end(args); return result;
}
int ut_gfwprintf_chk(FILE* file, int flag, const wchar_t* format, ...) {
    va_list args; va_start(args, format); int result = ut_gvfwprintf_chk(file, flag, format, args); va_end(args); return result;
}
int ut_gwprintf_chk(int flag, const wchar_t* format, ...) {
    va_list args; va_start(args, format); int result = ut_gvfwprintf_chk(stdout, flag, format, args); va_end(args); return result;
}
