package xyz.srnyx.limitedlives.services.player;

/** Pure clamping rules for multi-target life transfers. */
public final class LifeTransferPolicy {
    private LifeTransferPolicy() {}

    public static int commonAmount(int sourceLives, int minLives, int requested, int targetCount) {
        if (targetCount <= 0 || requested <= 0) return 0;
        final int maxTotal = sourceLives - (minLives + 1);
        if (maxTotal <= 0) return 0;
        return (long) requested * targetCount > maxTotal ? maxTotal / targetCount : requested;
    }

    public static int targetAmount(int targetLives, int targetMax, int commonAmount) {
        return Math.max(0, Math.min(commonAmount, targetMax - targetLives));
    }
}
