package com.nododiiiii.ponderer.interfaces.sandbox;

/**
 * Fidelity tiers of an interface session (design doc 14.1). T3 needs adapters and is not
 * produced yet.
 */
public enum Fidelity {
    /** T2: menu and screen built by the mod's own code; real client-side item interaction. */
    INTERACTIVE("T2"),
    /** T1: built and rendered, input disabled (tripwire, dispatch failure, missing guard). */
    READ_ONLY("T1"),
    /** T0: nothing could be built; a placeholder panel explains why. */
    SCHEMATIC("T0");

    private final String tier;

    Fidelity(String tier) {
        this.tier = tier;
    }

    public String tier() {
        return tier;
    }
}
