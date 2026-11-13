// Narrow Windows-1252 adapter for the original launcher's four conversion pairs.
#include <errno.h>
#include <iconv.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

extern iconv_t bionic_iconv_open(const char*, const char*);
extern size_t bionic_iconv(iconv_t, char**, size_t*, char**, size_t*);
extern int bionic_iconv_close(iconv_t);
__asm__(".symver bionic_iconv_open,iconv_open@LIBC_P");
__asm__(".symver bionic_iconv,iconv@LIBC_P");
__asm__(".symver bionic_iconv_close,iconv_close@LIBC_P");

enum { CP1252=1, UTF16=2, WIDE=3 };
typedef struct { int source, target, ignore, delegated; iconv_t delegate; } Converter;
static const uint16_t cp_high[32] = {
    0x20ac,0,0x201a,0x0192,0x201e,0x2026,0x2020,0x2021,
    0x02c6,0x2030,0x0160,0x2039,0x0152,0,0x017d,0,
    0,0x2018,0x2019,0x201c,0x201d,0x2022,0x2013,0x2014,
    0x02dc,0x2122,0x0161,0x203a,0x0153,0,0x017e,0x0178
};
static int encoding(const char* name) {
    if (!strcasecmp(name,"WINDOWS-1252") || !strcasecmp(name,"CP1252")) return CP1252;
    if (!strcasecmp(name,"WINDOWS-1252//IGNORE") || !strcasecmp(name,"CP1252//IGNORE")) return CP1252;
    if (!strcasecmp(name,"WCHAR_T")) return WIDE;
    if (!strcasecmp(name,"UTF-16LE")) return UTF16;
    return 0;
}
iconv_t load_iconv_open(const char* target, const char* source) {
    if (!target || !source) { errno=EINVAL; return (iconv_t)-1; }
    int to=encoding(target), from=encoding(source);
    Converter* c=calloc(1,sizeof(*c)); if (!c) return (iconv_t)-1;
    if ((from == CP1252 && (to == WIDE || to == UTF16)) || (to == CP1252 && (from == WIDE || from == UTF16))) {
        c->source=from; c->target=to; c->ignore=strstr(target,"//IGNORE") != NULL;
    } else {
        c->delegate=bionic_iconv_open(target,source);
        if (c->delegate == (iconv_t)-1) { free(c); return (iconv_t)-1; }
        c->delegated=1;
    }
    return (iconv_t)c;
}
static uint32_t get_le(const unsigned char* p, size_t n) { uint32_t v=0; for (size_t i=0;i<n;i++) v |= (uint32_t)p[i] << (8*i); return v; }
static void put_le(unsigned char* p, uint32_t v, size_t n) { for (size_t i=0;i<n;i++) p[i]=(unsigned char)(v >> (8*i)); }
static int cp_byte(uint32_t wc) {
    if (wc < 0x80 || (wc >= 0xa0 && wc <= 0xff)) return (int)wc;
    for (int i=0;i<32;i++) if (cp_high[i] && cp_high[i]==wc) return 0x80+i;
    return -1;
}
size_t load_iconv(iconv_t handle, char** in, size_t* inleft, char** out, size_t* outleft) {
    Converter* c=(Converter*)handle;
    if (!c || handle==(iconv_t)-1) { errno=EBADF; return (size_t)-1; }
    if (c->delegated) return bionic_iconv(c->delegate,in,inleft,out,outleft);
    if (!in || !*in) return 0;
    if (!inleft || !out || !*out || !outleft) { errno=EINVAL; return (size_t)-1; }
    int ignored=0;
    while (*inleft) {
        unsigned char* src=(unsigned char*)*in;
        size_t used = c->source == CP1252 ? 1 : c->source == UTF16 ? 2 : 4;
        if (*inleft < used) { errno=EINVAL; return (size_t)-1; }
        uint32_t wc=get_le(src,used);
        if (c->source == CP1252 && wc >= 0x80 && wc < 0xa0) wc=cp_high[wc-0x80];
        if (c->source == CP1252 && !wc && *src) { errno=EILSEQ; return (size_t)-1; }
        if (c->source == UTF16 && wc >= 0xd800 && wc <= 0xdbff) {
            if (*inleft < 4) { errno=EINVAL; return (size_t)-1; }
            uint32_t lo=get_le(src+2,2);
            if (lo < 0xdc00 || lo > 0xdfff) { errno=EILSEQ; return (size_t)-1; }
            wc=0x10000+((wc-0xd800)<<10)+(lo-0xdc00); used=4;
        } else if ((wc >= 0xdc00 && wc <= 0xdfff) || wc > 0x10ffff || (c->source == WIDE && wc >= 0xd800 && wc <= 0xdbff)) { errno=EILSEQ; return (size_t)-1; }
        size_t written = c->target == CP1252 ? 1 : c->target == UTF16 ? 2 : 4;
        int byte = c->target == CP1252 ? cp_byte(wc) : 0;
        if (byte < 0) {
            if (!c->ignore) { errno=EILSEQ; return (size_t)-1; }
            *in += used; *inleft -= used; ignored=1; continue;
        }
        if (*outleft < written) { errno=E2BIG; return (size_t)-1; }
        put_le((unsigned char*)*out,c->target == CP1252 ? (uint32_t)byte : wc,written);
        *in+=used; *inleft-=used; *out+=written; *outleft-=written;
    }
    if (ignored) { errno=EILSEQ; return (size_t)-1; }
    return 0;
}
int load_iconv_close(iconv_t handle) {
    Converter* c=(Converter*)handle;
    if (!c || handle==(iconv_t)-1) { errno=EBADF; return -1; }
    int rc=c->delegated ? bionic_iconv_close(c->delegate) : 0; free(c); return rc;
}
__asm__(".symver load_iconv_open,iconv_open@@GLIBC_2.17");
__asm__(".symver load_iconv,iconv@@GLIBC_2.17");
__asm__(".symver load_iconv_close,iconv_close@@GLIBC_2.17");
