#include <errno.h>
/* Narrow experiment only: does not provide a libc.so.6 or other GNU APIs. */
int *__errno_location(void) { return &errno; }
