struct InlineBase { virtual ~InlineBase() = default; virtual int value() const = 0; };
struct InlineDerived : InlineBase { int value() const override { return 99; } };
extern "C" InlineBase* inline_create();
