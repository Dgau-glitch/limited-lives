package xyz.srnyx.limitedlives.api;

/** Immutable result of an atomic UUID-only life addition. */
public record LifeMutationResult(
        int oldLives,
        int newLives,
        int requestedAmount,
        int appliedAmount,
        boolean limitedByMaximum,
        boolean revived,
        boolean rejected
) {
    public boolean changed() {
        return appliedAmount != 0;
    }
}
