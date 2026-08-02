package xyz.srnyx.limitedlives.api.internal;

import org.jetbrains.annotations.NotNull;
import xyz.srnyx.limitedlives.api.LifeMutationResult;
import xyz.srnyx.limitedlives.api.LifeOverflowPolicy;

import java.util.Objects;

/** Pure overflow/min/max calculation shared by the public mutation API. */
public final class LifeMutationCalculator {
    private LifeMutationCalculator() {}

    @NotNull
    public static LifeMutationResult add(int oldLives, int amount, int minimum, int maximum,
                                         @NotNull LifeOverflowPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        if (maximum < minimum) throw new IllegalArgumentException("maximum must not be below minimum");
        final long requestedValue = (long) oldLives + amount;
        final boolean overflow = requestedValue > maximum;
        if (overflow && policy == LifeOverflowPolicy.REJECT) {
            return new LifeMutationResult(oldLives, oldLives, amount, 0, true, false, true);
        }
        if (oldLives >= maximum) {
            return new LifeMutationResult(oldLives, oldLives, amount, 0, true, false, false);
        }
        final int newLives = Math.max(minimum, (int) Math.min(requestedValue, maximum));
        final int applied = Math.max(0, newLives - oldLives);
        return new LifeMutationResult(oldLives, newLives, amount, applied, overflow,
                oldLives <= minimum && newLives > minimum, false);
    }
}
