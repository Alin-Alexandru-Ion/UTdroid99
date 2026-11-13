#include "inline.hpp"
extern "C" int inline_fixture_run() {
    InlineBase* object = inline_create();
    InlineDerived* derived = dynamic_cast<InlineDerived*>(object);
    int result = derived ? derived->value() : 0;
    delete object;
    return result;
}
