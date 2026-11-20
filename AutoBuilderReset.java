package pl.blockflow;

/** Pomost: Flow.rebuild() resetuje stan auto-budowy. */
final class AutoBuilderReset {
    private AutoBuilderReset() {}
    static void reset() { AutoBuilder.reset(); }
}
