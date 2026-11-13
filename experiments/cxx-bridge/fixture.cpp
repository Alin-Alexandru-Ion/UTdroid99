#include "shared.hpp"
extern "C" int fixture_run() {
    int value = 0;
    try { fixture_throw(); } catch (int n) { value = n; } catch (...) { return -100; }
    Base* object = fixture_create();
    Derived* derived = dynamic_cast<Derived*>(object);
    int result = derived ? derived->value() : -1;
    delete object;
    return value == 73 && result == 99 && fixture_cleanup_count() == 1 ? 1 : 10000 + value * 100 + result;
}
