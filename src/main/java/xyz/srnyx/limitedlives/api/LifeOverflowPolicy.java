package xyz.srnyx.limitedlives.api;

/** Behavior when an addition would exceed the configured global maximum. */
public enum LifeOverflowPolicy {
    /** Apply as many lives as fit and cap the new value at the maximum. */
    CLAMP,
    /** Reject the complete mutation and leave the old value unchanged. */
    REJECT
}
