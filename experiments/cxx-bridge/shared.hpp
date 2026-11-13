struct Base { virtual ~Base(); virtual int value() const = 0; };
struct Derived : Base { int value() const override; };
extern "C" void fixture_throw();
extern "C" Base* fixture_create();
extern "C" int fixture_cleanup_count();
