#include "shared.hpp"
Base::~Base() = default;
int Derived::value() const { return 99; }
static volatile int cleanupCount = 0;
struct Guard { ~Guard() { ++cleanupCount; } };
extern "C" void fixture_throw() { Guard guard; throw 73; }
extern "C" int fixture_cleanup_count() { return cleanupCount; }
extern "C" Base* fixture_create() { return new Derived; }
