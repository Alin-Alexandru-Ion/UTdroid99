#include <iconv.h>
#include <errno.h>
#include <stdint.h>
#include <string.h>
extern iconv_t g_open(const char*, const char*);
extern size_t g_convert(iconv_t, char**, size_t*, char**, size_t*);
extern int g_close(iconv_t);
__asm__(".symver g_open,iconv_open@GLIBC_2.17");
__asm__(".symver g_convert,iconv@GLIBC_2.17");
__asm__(".symver g_close,iconv_close@GLIBC_2.17");
static int pair(const char* to, const char* from, const void* source, size_t n, const void* expected, size_t m) {
    iconv_t cd=g_open(to,from); if (cd==(iconv_t)-1) return 1;
    char* p=(char*)source; char result[64]={0}; char* q=result; size_t left=n, space=sizeof(result);
    size_t rc=g_convert(cd,&p,&left,&q,&space);
    int ok=rc==0 && left==0 && (size_t)(q-result)==m && !memcmp(result,expected,m) && !g_close(cd);
    return !ok;
}
int iconv_fixture_run(void) {
    const unsigned char cp[]={0x41,0x80,0xe9};
    const uint32_t wide[]={0x41,0x20ac,0xe9};
    const uint16_t utf16[]={0x41,0x20ac,0xe9};
    if (pair("WCHAR_T","WINDOWS-1252",cp,3,wide,sizeof(wide))) return 1;
    if (pair("UTF-16LE","WINDOWS-1252",cp,3,utf16,sizeof(utf16))) return 2;
    if (pair("WINDOWS-1252//IGNORE","WCHAR_T",wide,sizeof(wide),cp,3)) return 3;
    if (pair("WINDOWS-1252//IGNORE","UTF-16LE",utf16,sizeof(utf16),cp,3)) return 4;
    iconv_t cd=g_open("WCHAR_T","WINDOWS-1252"); if (cd==(iconv_t)-1) return 5;
    char* in=(char*)cp; size_t inleft=3; char buf[3]; char* out=buf; size_t outleft=sizeof(buf); errno=0;
    int ok=g_convert(cd,&in,&inleft,&out,&outleft)==(size_t)-1 && errno==E2BIG && inleft==3; g_close(cd); if (!ok) return 6;
    unsigned char invalid=0x81; in=(char*)&invalid; inleft=1; char wideout[4]; out=wideout; outleft=4;
    cd=g_open("WCHAR_T","WINDOWS-1252"); errno=0; ok=g_convert(cd,&in,&inleft,&out,&outleft)==(size_t)-1 && errno==EILSEQ && inleft==1; g_close(cd); if (!ok) return 7;
    uint16_t partial=0xd800; in=(char*)&partial; inleft=2; out=buf; outleft=3;
    cd=g_open("WINDOWS-1252//IGNORE","UTF-16LE"); errno=0; ok=g_convert(cd,&in,&inleft,&out,&outleft)==(size_t)-1 && errno==EINVAL && inleft==2; g_close(cd); return ok ? 0 : 8;
}
