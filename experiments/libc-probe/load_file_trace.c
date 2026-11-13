// Private launcher diagnostic: report path operations to captured stderr.
#include "stat_probe.h"
#include <errno.h>
#include <stdio.h>
#include <unistd.h>
#include <string.h>
#include <stdlib.h>
#include <dirent.h>
extern int android_chdir(const char*);
extern int android_access(const char*,int);
extern FILE* android_fopen(const char*,const char*);
extern DIR* android_opendir(const char*);
__asm__(".symver android_chdir,chdir@LIBC");
__asm__(".symver android_access,access@LIBC");
__asm__(".symver android_fopen,fopen@LIBC");
__asm__(".symver android_opendir,opendir@LIBC");
static const char* game_directory;
void ut_set_game_directory(const char* path) { game_directory = path; }
int load_trace_chdir(const char* p) {
    /* The Linux launcher derives this directory from /proc/self/exe. Android
       reports /system/bin/arm64 under translation and /system/bin on a native
       ARM64 device. Both refer to the host process, not the UT game folder. */
    const int android_process_dir = p && (!strcmp(p,"/system/bin/arm64") || !strcmp(p,"/system/bin"));
    const char* dest = game_directory && android_process_dir ? game_directory : p;
    int r=android_chdir(dest), e=errno; dprintf(2,"TRACE chdir(%s -> %s)=%d errno=%d\n",p,dest,r,e); errno=e; return r;
}
int load_trace_access(const char* p,int mode) { int r=android_access(p,mode), e=errno; dprintf(2,"TRACE access(%s)=%d errno=%d\n",p,r,e); errno=e; return r; }
FILE* load_trace_fopen(const char* p,const char* mode) { FILE* r=android_fopen(p,mode); int e=errno; dprintf(2,"TRACE fopen(%s,%s)=%s errno=%d\n",p,mode,r?"ok":"fail",e); errno=e; return r; }
DIR* load_trace_opendir(const char* p) { DIR* r=android_opendir(p); int e=errno; dprintf(2,"TRACE opendir(%s)=%s errno=%d\n",p,r?"ok":"fail",e); errno=e; return r; }
int load_trace_xstat(int v,const char* p,UtGlibcStat* s) { int r=ut_gxstat(v,p,s), e=errno; dprintf(2,"TRACE xstat(%s)=%d errno=%d\n",p,r,e); errno=e; return r; }
int load_trace_fxstat(int v,int fd,UtGlibcStat* s) { return ut_gfxstat(v,fd,s); }
__asm__(".symver load_trace_chdir,chdir@@GLIBC_2.17");
__asm__(".symver load_trace_access,access@@GLIBC_2.17");
__asm__(".symver load_trace_fopen,fopen@@GLIBC_2.17");
__asm__(".symver load_trace_opendir,opendir@@GLIBC_2.17");
__asm__(".symver load_trace_xstat,__xstat@@GLIBC_2.17");
__asm__(".symver load_trace_fxstat,__fxstat@@GLIBC_2.17");
