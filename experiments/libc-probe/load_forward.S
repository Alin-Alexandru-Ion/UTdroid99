// Exploratory load-only exports. ABI/semantic compatibility is not established.
.text
.macro FORWARD name, ver=LIBC
.global load_\name
.type load_\name, %function
load_\name:
    b android_\name
.size load_\name, .-load_\name
.symver android_\name,\name@\ver
.symver load_\name,\name@@GLIBC_2.17
.endm
FORWARD __cxa_atexit
FORWARD __cxa_finalize
FORWARD __stack_chk_fail
FORWARD _exit
FORWARD asctime_r
FORWARD btowc
FORWARD chmod
FORWARD closedir
FORWARD difftime
FORWARD execvpe
FORWARD exit
FORWARD fork
FORWARD getenv
FORWARD gethostname
FORWARD getlogin
FORWARD getpid
FORWARD getrlimit
FORWARD gettimeofday
FORWARD gmtime_r
FORWARD iswdigit
FORWARD localtime_r
FORWARD mbstowcs
FORWARD mktime
FORWARD nanosleep
FORWARD pthread_attr_destroy
FORWARD pthread_attr_init
FORWARD qsort
FORWARD raise
FORWARD rand
FORWARD readdir
FORWARD readlink
FORWARD setlocale
FORWARD setrlimit
FORWARD sigemptyset
FORWARD sleep
FORWARD srand
FORWARD strerror
FORWARD strtod
FORWARD sysconf
FORWARD system
FORWARD time
FORWARD tolower
FORWARD toupper
FORWARD towupper
FORWARD unlink
FORWARD usleep
FORWARD waitpid
FORWARD wcscasecmp
FORWARD wcstol
FORWARD wcstombs
FORWARD wcstoul
FORWARD select
FORWARD puts
FORWARD gmtime
FORWARD remove
FORWARD memchr
FORWARD inet_pton
FORWARD fputc
FORWARD fflush
FORWARD malloc
FORWARD abort
FORWARD fgetc
FORWARD fwrite
FORWARD fcntl
FORWARD setbuf
FORWARD utime
FORWARD ftell
FORWARD setvbuf
FORWARD mkdir
FORWARD getcwd
FORWARD getuid
FORWARD rmdir
FORWARD endpwent, LIBC_O
FORWARD getpwent, LIBC_O
FORWARD setpwent, LIBC_O
FORWARD strstr
FORWARD open
FORWARD isspace
FORWARD strtol
FORWARD read
FORWARD strcpy
FORWARD mmap
FORWARD strncmp
FORWARD strncpy
FORWARD munmap
FORWARD strrchr
FORWARD close
FORWARD strcmp
FORWARD write
FORWARD stpcpy
FORWARD socket
FORWARD send
FORWARD getsockopt
FORWARD setsockopt
FORWARD recvfrom
FORWARD __recvfrom_chk
FORWARD getsockname
FORWARD recv
FORWARD inet_addr
FORWARD inet_ntop
FORWARD accept
FORWARD connect
FORWARD shutdown
FORWARD bind
FORWARD sendto
FORWARD listen
.global load_h_errno_location
.type load_h_errno_location, %function
load_h_errno_location: b android_get_h_errno
.symver android_get_h_errno,__get_h_errno@LIBC
.symver load_h_errno_location,__h_errno_location@@GLIBC_2.17
.global load_io_getc
.type load_io_getc, %function
load_io_getc: b android_fgetc
.symver load_io_getc,_IO_getc@@GLIBC_2.17
.global load_io_putc
.type load_io_putc, %function
load_io_putc: b android_fputc
.symver load_io_putc,_IO_putc@@GLIBC_2.17
// Paired calls use Bionic's private buffer representation inside GNU storage.
// Never mix these buffers with a real glibc implementation.
FORWARD _setjmp
.global load_checked_longjmp
.type load_checked_longjmp, %function
load_checked_longjmp:
    cmp w1, #0
    csinc w1, w1, wzr, ne
    b android_private_longjmp
.size load_checked_longjmp, .-load_checked_longjmp
.symver android_private_longjmp,_longjmp@LIBC
.symver load_checked_longjmp,__longjmp_chk@@GLIBC_2.17
.section .note.GNU-stack,"",%progbits
