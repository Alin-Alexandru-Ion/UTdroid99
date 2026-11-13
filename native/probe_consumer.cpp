extern "C" int probe_versioned_add(int);
static thread_local int counter = 0;
extern "C" __attribute__((visibility("default"))) int probe_step() {
    return probe_versioned_add(++counter);
}
