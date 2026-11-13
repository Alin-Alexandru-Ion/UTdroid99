#include <pthread.h>
#include <stdint.h>

/* Research-only glibc C-locale table. Other locales and locale changes are
 * deliberately unsupported. Never use as a complete libc implementation. */
static uint16_t classes[384];
static int32_t lowercase[384], uppercase[384];
static pthread_once_t once = PTHREAD_ONCE_INIT;
static _Thread_local const uint16_t* thread_class;
static _Thread_local const int32_t* thread_lower;
static _Thread_local const int32_t* thread_upper;

static void build_tables(void) {
    for (int i = 0; i < 384; ++i) {
        int c = i - 128;
        int byte = c < 0 ? c + 256 : c;
        int upper = byte >= 'A' && byte <= 'Z';
        int lower = byte >= 'a' && byte <= 'z';
        int digit = byte >= '0' && byte <= '9';
        int alpha = upper || lower;
        int graph = byte >= 33 && byte <= 126;
        int print = byte >= 32 && byte <= 126;
        int space = byte == ' ' || (byte >= 9 && byte <= 13);
        int blank = byte == ' ' || byte == 9;
        int control = byte < 32 || byte == 127;
        int hex = digit || (byte >= 'A' && byte <= 'F') || (byte >= 'a' && byte <= 'f');
        uint16_t mask = (upper ? 0x0100 : 0) | (lower ? 0x0200 : 0) |
            (alpha ? 0x0400 : 0) | (digit ? 0x0800 : 0) | (hex ? 0x1000 : 0) |
            (space ? 0x2000 : 0) | (print ? 0x4000 : 0) | (graph ? 0x8000 : 0) |
            (blank ? 0x0001 : 0) | (control ? 0x0002 : 0) |
            ((graph && !alpha && !digit) ? 0x0004 : 0) | ((alpha || digit) ? 0x0008 : 0);
        classes[i] = c == -1 ? 0 : mask;
        lowercase[i] = c == -1 ? -1 : (upper ? byte + ('a' - 'A') : byte);
        uppercase[i] = c == -1 ? -1 : (lower ? byte - ('a' - 'A') : byte);
    }
}
const uint16_t** __ctype_b_loc(void) {
    pthread_once(&once, build_tables); thread_class = classes + 128; return &thread_class;
}
const int32_t** __ctype_tolower_loc(void) {
    pthread_once(&once, build_tables); thread_lower = lowercase + 128; return &thread_lower;
}
const int32_t** __ctype_toupper_loc(void) {
    pthread_once(&once, build_tables); thread_upper = uppercase + 128; return &thread_upper;
}
