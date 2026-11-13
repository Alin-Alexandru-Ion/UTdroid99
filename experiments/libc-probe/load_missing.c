// Load-only fallbacks: not production implementations.
#include <errno.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
extern char** bionic_environment;
__asm__(".symver bionic_environment,environ@LIBC");
char** load_environ;
char** load_dunder_environ;
__asm__(".symver load_environ,environ@@GLIBC_2.17");
__asm__(".symver load_dunder_environ,__environ@@GLIBC_2.17");
__attribute__((constructor)) static void snapshot_environment(void) {
    load_environ = load_dunder_environ = bionic_environment;
}
int load_backtrace(void** buffer, int size) { (void)buffer; (void)size; return 0; }
char** load_backtrace_symbols(void* const* buffer, int size) { (void)buffer; (void)size; errno = ENOSYS; return NULL; }
int load_malloc_trim(size_t padding) { (void)padding; return 0; }
__asm__(".symver load_backtrace,backtrace@@GLIBC_2.17");
__asm__(".symver load_backtrace_symbols,backtrace_symbols@@GLIBC_2.17");
__asm__(".symver load_malloc_trim,malloc_trim@@GLIBC_2.17");
extern void* android_memcpy(void*, const void*, size_t);
extern void* android_memmove(void*, const void*, size_t);
__asm__(".symver android_memcpy,memcpy@LIBC");
__asm__(".symver android_memmove,memmove@LIBC");
void* load_memcpy_chk(void* d, const void* s, size_t n, size_t cap) { if (n > cap) abort(); return android_memcpy(d,s,n); }
void* load_memmove_chk(void* d, const void* s, size_t n, size_t cap) { if (n > cap) abort(); return android_memmove(d,s,n); }
__asm__(".symver load_memcpy_chk,__memcpy_chk@@GLIBC_2.17");
__asm__(".symver load_memmove_chk,__memmove_chk@@GLIBC_2.17");
// Used only by the patched launcher TLS-access trampoline. The executable's
// original _start must never be called in this experiment.
static void* core_tls_module;
void ut_set_core_tls_module(void* handle) { core_tls_module = handle; }
void* ut_resolve_core_tls(void) {
    void* value = core_tls_module ? dlsym(core_tls_module, "_ZN15UnGuardBlockTLS4DataE") : NULL;
    if (!value) abort();
    return value;
}
char* load_realpath_chk(const char* path, char* out, size_t size) {
    char* resolved = realpath(path, NULL);
    if (!resolved) return NULL;
    size_t n = strlen(resolved) + 1;
    if (n > size) { free(resolved); abort(); }
    memcpy(out, resolved, n); free(resolved); return out;
}
__asm__(".symver load_realpath_chk,__realpath_chk@@GLIBC_2.17");
long load_fdelt_chk(long fd) { if (fd < 0 || fd >= 1024) abort(); return fd / 64; }
__asm__(".symver load_fdelt_chk,__fdelt_chk@@GLIBC_2.17");
