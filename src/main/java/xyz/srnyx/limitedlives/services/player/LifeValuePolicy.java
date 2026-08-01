package xyz.srnyx.limitedlives.services.player;

import xyz.srnyx.limitedlives.managers.player.exception.LessThanMinLives;
import xyz.srnyx.limitedlives.managers.player.exception.MoreThanMaxLives;

/** Pure life-boundary rules shared by every persistence-facing operation. */
public final class LifeValuePolicy {
    private LifeValuePolicy() {}

    public static int set(int amount, int min, int max) throws LessThanMinLives, MoreThanMaxLives {
        if (amount < min) throw new LessThanMinLives();
        if (amount > max) throw new MoreThanMaxLives();
        return amount;
    }

    public static int add(int current, int amount, int max) throws MoreThanMaxLives {
        final int updated = Math.addExact(current, amount);
        if (updated > max) throw new MoreThanMaxLives();
        return updated;
    }

    public static int remove(int current, int amount, int min) throws LessThanMinLives {
        final int updated = Math.subtractExact(current, amount);
        if (updated < min) throw new LessThanMinLives();
        return updated;
    }

    public static long graceLeft(long startMillis, long durationMillis, long nowMillis) {
        return Math.max(0, durationMillis - (nowMillis - startMillis));
    }

    public static boolean shouldRevive(int previous, int updated, int min) {
        return previous <= min && updated > min;
    }

    public static boolean shouldKill(int updated, int min) {
        return updated == min;
    }
}
