// Translate the glibc addrinfo ABI used by the Linux engine to Android's ABI.
#include <netdb.h>
#include <stddef.h>
#include <string.h>

typedef struct GNUAddrInfo {
    int flags, family, socktype, protocol;
    socklen_t addrlen;
    struct sockaddr* addr;
    char* canonname;
    struct GNUAddrInfo* next;
} GNUAddrInfo;

_Static_assert(sizeof(GNUAddrInfo) == sizeof(struct addrinfo), "unexpected addrinfo size");
_Static_assert(offsetof(GNUAddrInfo, addr) == offsetof(struct addrinfo, ai_canonname), "unexpected addrinfo layout");

extern int bionic_getaddrinfo(const char*, const char*, const struct addrinfo*, struct addrinfo**);
extern void bionic_freeaddrinfo(struct addrinfo*);
__asm__(".symver bionic_getaddrinfo,getaddrinfo@LIBC");
__asm__(".symver bionic_freeaddrinfo,freeaddrinfo@LIBC");

static int gnu_flags(int flags, int* result) {
    if (flags & ~(0x1 | 0x2 | 0x4 | 0x8 | 0x10 | 0x20 | 0x400)) return -1;
    *result = flags & 0x7;
    if (flags & 0x8) *result |= AI_V4MAPPED;
    if (flags & 0x10) *result |= AI_ALL;
    if (flags & 0x20) *result |= AI_ADDRCONFIG;
    if (flags & 0x400) *result |= AI_NUMERICSERV;
    return 0;
}

static int gnu_error(int error) {
    switch (error) {
        case 0: return 0;
        case EAI_ADDRFAMILY: return -9;
        case EAI_AGAIN: return -3;
        case EAI_BADFLAGS: return -1;
        case EAI_FAIL: return -4;
        case EAI_FAMILY: return -6;
        case EAI_MEMORY: return -10;
        case EAI_NODATA: return -5;
        case EAI_NONAME: return -2;
        case EAI_SERVICE: return -8;
        case EAI_SOCKTYPE: return -7;
        case EAI_SYSTEM: return -11;
        case EAI_OVERFLOW: return -12;
        default: return -4;
    }
}

// The two pointer fields occupy the same slots in opposite order. Swapping
// them retains the original allocator ownership and the native linked list.
static void swap_fields(struct addrinfo* list) {
    for (struct addrinfo* it = list; it; it = it->ai_next) {
        void* first;
        void* second;
        memcpy(&first, &it->ai_canonname, sizeof(first));
        memcpy(&second, &it->ai_addr, sizeof(second));
        memcpy(&it->ai_canonname, &second, sizeof(second));
        memcpy(&it->ai_addr, &first, sizeof(first));
    }
}

int load_getaddrinfo(const char* node, const char* service, const GNUAddrInfo* hints, GNUAddrInfo** result) {
    struct addrinfo native_hints;
    struct addrinfo* native_result = NULL;
    if (!result) return -4;
    *result = NULL;
    if (hints) {
        memset(&native_hints, 0, sizeof(native_hints));
        if (gnu_flags(hints->flags, &native_hints.ai_flags)) return -1;
        native_hints.ai_family = hints->family;
        native_hints.ai_socktype = hints->socktype;
        native_hints.ai_protocol = hints->protocol;
    }
    int error = bionic_getaddrinfo(node, service, hints ? &native_hints : NULL, &native_result);
    if (error) return gnu_error(error);
    swap_fields(native_result);
    *result = (GNUAddrInfo*)native_result;
    return 0;
}

void load_freeaddrinfo(GNUAddrInfo* result) {
    if (!result) return;
    swap_fields((struct addrinfo*)result);
    bionic_freeaddrinfo((struct addrinfo*)result);
}

__asm__(".symver load_getaddrinfo,getaddrinfo@@GLIBC_2.17");
__asm__(".symver load_freeaddrinfo,freeaddrinfo@@GLIBC_2.17");
