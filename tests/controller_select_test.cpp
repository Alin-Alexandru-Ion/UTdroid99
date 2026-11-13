#include "../native/controller_select.h"
#include <cassert>
#include <cstdio>

int main() {
    bool mapped[] = {false, false, true, true};
    auto isMapped = [&](int index) { return mapped[index]; };
    assert(chooseControllerIndex(0, 4, isMapped) == 2);
    assert(chooseControllerIndex(1, 4, isMapped) == 1);
    assert(chooseControllerIndex(0, 2, isMapped) == 0);
    assert(chooseControllerIndex(0, 0, isMapped) == 0);
    mapped[0] = true;
    assert(chooseControllerIndex(0, 4, isMapped) == 0);
    std::puts("SDL controller selection: pass");
}
