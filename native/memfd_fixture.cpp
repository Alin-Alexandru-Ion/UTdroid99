// Deliberately independent fixture: does not contain or load game code.
extern "C" __attribute__((visibility("default"))) int ut_memory_fixture(int value) {
    return value * 3 + 7;
}
