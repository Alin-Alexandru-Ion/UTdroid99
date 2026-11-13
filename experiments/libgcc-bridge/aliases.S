// These aliases expose selected NDK compiler-rt implementations for testing.
// They do not establish GNU libgcc compatibility or unwind interoperability.
.text
.macro ALIAS name version
.global utbridge_\name
.type utbridge_\name, %function
utbridge_\name:
    b utimpl_\name
.size utbridge_\name, .-utbridge_\name
.symver utbridge_\name,\name@@\version
.endm
ALIAS __gttf2 GCC_3.0
ALIAS __subtf3 GCC_3.0
ALIAS __multf3 GCC_3.0
ALIAS __floatsitf GCC_3.0
ALIAS __fixunstfsi GCC_3.0
ALIAS __extenddftf2 GCC_3.0
ALIAS __netf2 GCC_3.0
ALIAS __addtf3 GCC_3.0
ALIAS _Unwind_Resume GCC_3.0
ALIAS __fixtfsi GCC_3.0
ALIAS __floatunsitf GCC_4.2.0
ALIAS __unordtf2 GCC_4.5.0
.section .note.GNU-stack,"",%progbits
