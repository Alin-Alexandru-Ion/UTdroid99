// Narrow AArch64 forwarding list for basic memory/string and allocation APIs.
// Tests cover selected cases only; these exports do not prove glibc semantics.
.text
.macro FORWARD name
.global utforward_\name
.type utforward_\name, %function
utforward_\name:
    b bionic_\name
.size utforward_\name, .-utforward_\name
.symver bionic_\name,\name@LIBC
.symver utforward_\name,\name@@GLIBC_2.17
.endm
FORWARD memcpy
FORWARD memmove
FORWARD memset
FORWARD memcmp
FORWARD strlen
FORWARD strnlen
FORWARD strchr
FORWARD wcscpy
FORWARD wcslen
FORWARD wcsncpy
FORWARD wcscmp
FORWARD wcsncmp
FORWARD wcscat
FORWARD wcschr
FORWARD wcsstr
FORWARD wcsnlen
FORWARD calloc
FORWARD free
FORWARD realloc
FORWARD memalign
FORWARD strdup
FORWARD wcsdup
FORWARD fread
FORWARD fseek
FORWARD fileno
FORWARD ferror
FORWARD fclose
.section .note.GNU-stack,"",%progbits
