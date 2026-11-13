#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <sys/ptrace.h>
#include <sys/types.h>
#include <sys/user.h>
#include <sys/wait.h>
#include <unistd.h>
#include <signal.h>

int main(int argc, char **argv) {
    if (argc < 3 || strcmp(argv[1], "--trace") != 0) {
        puts("UTDROID_EXEC_PROBE_OK");
        return 0;
    }
    pid_t child = fork();
    if (child < 0) { perror("fork"); return 1; }
    if (child == 0) {
        if (ptrace(PTRACE_TRACEME, 0, 0, 0) < 0) { perror("ptrace TRACEME"); _exit(1); }
        raise(SIGSTOP);
        execv(argv[2], argv + 2);
        perror("execv"); _exit(1);
    }
    int status = 0;
    if (waitpid(child, &status, 0) < 0 || !WIFSTOPPED(status)) { puts("child not traced"); return 1; }
    if (ptrace(PTRACE_SETOPTIONS, child, 0, PTRACE_O_TRACESYSGOOD) < 0) { perror("ptrace SETOPTIONS"); return 1; }
    long last = -1;
    int translated = 0;
    int fake_success = 0;
    long fake_result = 0;
    int outcome = 1;
    for (;;) {
        if (ptrace(PTRACE_SYSCALL, child, 0, 0) < 0) { perror("ptrace SYSCALL"); break; }
        if (waitpid(child, &status, 0) < 0) { perror("waitpid"); break; }
        if (WIFEXITED(status)) { outcome = WEXITSTATUS(status); printf("child exit=%d last_syscall=%ld translated=%d\n", outcome, last, translated); break; }
        if (WIFSIGNALED(status)) { outcome = 128 + WTERMSIG(status); printf("child signal=%d last_syscall=%ld\n", WTERMSIG(status), last); break; }
        if (!WIFSTOPPED(status)) continue;
        int signal_number = WSTOPSIG(status);
        if (signal_number == (SIGTRAP | 0x80)) {
            struct user_regs_struct regs;
            if (ptrace(PTRACE_GETREGS, child, 0, &regs) == 0) {
                last = (long)regs.orig_rax;
                /* Android's x86_64 sandbox blocks the old access syscall. */
                if (regs.rax == (unsigned long)-ENOSYS && regs.orig_rax == 21) {
                    regs.rdx = regs.rsi; regs.rsi = regs.rdi;
                    regs.rdi = (unsigned long)-100; regs.orig_rax = 269;
                    if (ptrace(PTRACE_SETREGS, child, 0, &regs) < 0) { perror("ptrace SETREGS"); break; }
                    translated++;
                } else if (regs.rax == (unsigned long)-ENOSYS && regs.orig_rax == 273) {
                    /* Fixture only: single-threaded glibc probe does not need a robust futex list. */
                    regs.orig_rax = 39;
                    if (ptrace(PTRACE_SETREGS, child, 0, &regs) < 0) { perror("ptrace SETREGS"); break; }
                    fake_success = 1;
                    fake_result = 0;
                    translated++;
                } else if (regs.rax == (unsigned long)-ENOSYS && regs.orig_rax == 334) {
                    /* Tell glibc restartable sequences are unavailable. */
                    regs.orig_rax = 39;
                    if (ptrace(PTRACE_SETREGS, child, 0, &regs) < 0) { perror("ptrace SETREGS"); break; }
                    fake_success = 1;
                    fake_result = -ENOSYS;
                    translated++;
                } else if (fake_success && regs.orig_rax == 39 && regs.rax != (unsigned long)-ENOSYS) {
                    regs.rax = (unsigned long)fake_result;
                    if (ptrace(PTRACE_SETREGS, child, 0, &regs) < 0) { perror("ptrace SETREGS"); break; }
                    fake_success = 0;
                }
            }
        } else if (signal_number != SIGTRAP && signal_number != SIGSTOP) {
            printf("child stopped signal=%d last_syscall=%ld\n", signal_number, last);
            outcome = 128 + signal_number;
            ptrace(PTRACE_CONT, child, 0, signal_number);
            waitpid(child, &status, 0);
            break;
        }
    }
    return outcome;
}
