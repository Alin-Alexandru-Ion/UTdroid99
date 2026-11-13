// Isolated diagnostic: preserve Android crash handlers when explicitly enabled.
#include <signal.h>
#include <errno.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
extern int bionic_sigaction(int, const struct sigaction*, struct sigaction*);
extern void (*bionic_signal(int, void (*)(int)))(int);
__asm__(".symver bionic_sigaction,sigaction@LIBC");
__asm__(".symver bionic_signal,signal@LIBC");
static int preserve(int signum) {
    const char* enabled=getenv("UT99_DIAG_KEEP_CRASH_HANDLERS");
    return enabled && !strcmp(enabled,"1") && (signum==SIGSEGV || signum==SIGBUS || signum==SIGILL || signum==SIGABRT || signum==SIGFPE);
}
int load_sigaction(int signum, const struct sigaction* action, struct sigaction* old) {
    int olderrno=errno;
    if (preserve(signum) && action && !old) {
        dprintf(2,"TRACE preserving Android handler for sigaction(%d)\n",signum); errno=olderrno; return 0;
    }
    errno=olderrno; int rc=bionic_sigaction(signum,action,old); int e=errno;
    dprintf(2,"TRACE sigaction(%d)=%d errno=%d\n",signum,rc,e); errno=e; return rc;
}
void (*load_signal(int signum, void (*handler)(int)))(int) {
    int olderrno=errno;
    if (preserve(signum)) { dprintf(2,"TRACE preserving Android handler for signal(%d)\n",signum); errno=olderrno; return SIG_DFL; }
    errno=olderrno; void (*result)(int)=bionic_signal(signum,handler); int e=errno;
    dprintf(2,"TRACE signal(%d)=%s errno=%d\n",signum,result==SIG_ERR?"fail":"ok",e); errno=e; return result;
}
__asm__(".symver load_sigaction,sigaction@@GLIBC_2.17");
__asm__(".symver load_signal,signal@@GLIBC_2.17");
