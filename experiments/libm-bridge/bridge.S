// ARM64 tail calls preserve the calling convention, not GNU math semantics.
.text
.macro FORWARD name
.global bridge_\name
.type bridge_\name, %function
bridge_\name:
    b android_\name
.size bridge_\name, .-bridge_\name
.symver android_\name,\name@LIBC
.symver bridge_\name,\name@@GLIBC_2.17
.endm
FORWARD cos
FORWARD acos
FORWARD sqrtf
FORWARD fmod
FORWARD atan2
FORWARD exp
FORWARD sin
FORWARD pow
FORWARD atan
FORWARD tan
FORWARD frexpl
FORWARD log
FORWARD sqrt
FORWARD frexp
FORWARD modf
FORWARD sincos
FORWARD cosf
FORWARD log10
FORWARD lrint
FORWARD lrintf
FORWARD lround
.global bridge_ldexp
.type bridge_ldexp, %function
bridge_ldexp: b android_scalbn
.symver android_scalbn,scalbn@LIBC
.symver bridge_ldexp,ldexp@@GLIBC_2.17
.global bridge_powf
.type bridge_powf, %function
bridge_powf: b android_powf
.symver android_powf,powf@LIBC
.symver bridge_powf,powf@@GLIBC_2.27
.global bridge_log2f
.type bridge_log2f, %function
bridge_log2f: b android_log2f
.symver android_log2f,log2f@LIBC
.symver bridge_log2f,log2f@@GLIBC_2.27
.section .note.GNU-stack,"",%progbits
