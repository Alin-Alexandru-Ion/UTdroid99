/* Experimental forwarding only. GNU and Android linker semantics differ. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
extern void *bionic_dlopen(const char *, int);
extern int bionic_dlclose(void *);
extern void *bionic_dlsym(void *, const char *);
extern char *bionic_dlerror(void);
__asm__(".symver bionic_dlopen,dlopen@LIBC");
__asm__(".symver bionic_dlclose,dlclose@LIBC");
__asm__(".symver bionic_dlsym,dlsym@LIBC");
__asm__(".symver bionic_dlerror,dlerror@LIBC");
void *bridge_dlopen(const char *path, int flags) {
    void* result = bionic_dlopen(path, flags);
    const char* dir = getenv("UT99_SYSTEM_DIR");
    if (!result && path && dir) {
        const char* base = strrchr(path, '/'); base = base ? base + 1 : path;
        if (strstr(base,".so") && !strchr(base,'/')) {
            char full[1024]; int n = snprintf(full,sizeof(full),"%s/%s",dir,base);
            if (n > 0 && (size_t)n < sizeof(full)) result = bionic_dlopen(full, flags);
        }
    }
    dprintf(2,"TRACE dlopen(%s,%d)=%s\n",path ? path : "<self>",flags,result ? "ok" : "fail");
    return result;
}
int bridge_dlclose(void *handle) { return bionic_dlclose(handle); }
void *bridge_dlsym(void *handle, const char *name) { return bionic_dlsym(handle, name); }
char *bridge_dlerror(void) { return bionic_dlerror(); }
__asm__(".symver bridge_dlopen,dlopen@@GLIBC_2.17");
__asm__(".symver bridge_dlclose,dlclose@@GLIBC_2.17");
__asm__(".symver bridge_dlsym,dlsym@@GLIBC_2.17");
__asm__(".symver bridge_dlerror,dlerror@@GLIBC_2.17");
