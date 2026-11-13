#include "inline.hpp"
extern "C" InlineBase* inline_create() { return new InlineDerived; }
