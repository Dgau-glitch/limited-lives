package xyz.srnyx.limitedlives.services.player;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xyz.srnyx.limitedlives.LimitedLives;
import xyz.srnyx.limitedlives.managers.player.PlayerManager;
import xyz.srnyx.limitedlives.managers.player.exception.ActionException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Coordinates a stable-lock-order transfer without cross-region entity access. */
public final class LifeTransferService {
    @NotNull private final LimitedLives plugin;

    public LifeTransferService(@NotNull LimitedLives plugin) {
        this.plugin = plugin;
    }

    public void transfer(@NotNull Player source, @NotNull List<OfflinePlayer> selectedTargets, int requested,
                         @NotNull Consumer<Outcome> completion) {
        final Participant sourceSnapshot = snapshot(source, new PlayerManager(plugin, source).getMaxLives());
        final List<TargetSlot> slots = new ArrayList<>();
        for (final OfflinePlayer target : selectedTargets) {
            final UUID uuid = target.getUniqueId();
            if (uuid.equals(sourceSnapshot.uuid)) continue;
            final Player online = Bukkit.getPlayer(uuid);
            final String offlineName = online == null ? target.getName() : null;
            final String name = offlineName == null ? uuid.toString() : offlineName;
            slots.add(new TargetSlot(uuid, name));
        }
        if (slots.isEmpty()) {
            completion.accept(Outcome.selfOnly());
            return;
        }

        final AtomicInteger remaining = new AtomicInteger(slots.size());
        for (final TargetSlot slot : slots) {
            final Player online = Bukkit.getPlayer(slot.uuid);
            if (online == null) {
                slot.participant = new Participant(slot.uuid, slot.name, plugin.config.lives.max);
                finishSnapshot(source, sourceSnapshot, slots, requested, completion, remaining);
            } else {
                plugin.execution.runForEntityOrNow(online, () -> {
                    slot.participant = snapshot(online, new PlayerManager(plugin, online).getMaxLives());
                    finishSnapshot(source, sourceSnapshot, slots, requested, completion, remaining);
                }, () -> {
                    slot.participant = new Participant(slot.uuid, slot.name, plugin.config.lives.max);
                    finishSnapshot(source, sourceSnapshot, slots, requested, completion, remaining);
                });
            }
        }
    }

    private void finishSnapshot(Player source, Participant sourceSnapshot, List<TargetSlot> slots, int requested,
                                Consumer<Outcome> completion, AtomicInteger remaining) {
        if (remaining.decrementAndGet() != 0) return;
        plugin.execution.runForEntityOrNow(source, () -> completion.accept(apply(sourceSnapshot, slots, requested)),
                () -> completion.accept(Outcome.sourceRetired()));
    }

    @NotNull
    private Outcome apply(Participant source, List<TargetSlot> slots, int requested) {
        final List<Participant> targets = slots.stream().map(slot -> slot.participant).toList();
        final List<UUID> lockOrder = new ArrayList<>();
        lockOrder.add(source.uuid);
        targets.forEach(target -> lockOrder.add(target.uuid));
        try {
            return plugin.lifeStore.atomicAllChecked(lockOrder, () -> {
                final PlayerManager sourceManager = source.manager(plugin);
                final int sourceLives = sourceManager.getLives();
                if (sourceLives <= plugin.config.lives.min + 1) return Outcome.lastLife();
                final int commonAmount = LifeTransferPolicy.commonAmount(sourceLives, plugin.config.lives.min, requested, targets.size());
                if (commonAmount <= 0) return Outcome.lastLife();

                final List<Transfer> transfers = new ArrayList<>();
                for (final Participant target : targets) {
                    final PlayerManager targetManager = target.manager(plugin);
                    final int targetLives = targetManager.getLives();
                    final int amount = LifeTransferPolicy.targetAmount(targetLives, target.maxLives, commonAmount);
                    if (amount <= 0) continue;
                    final int newSourceLives = sourceManager.removeLives(amount, null);
                    final int newTargetLives = targetManager.addLives(amount);
                    transfers.add(new Transfer(target.uuid, target.name, amount, newSourceLives, newTargetLives));
                }
                return Outcome.success(transfers);
            });
        } catch (final ActionException exception) {
            return Outcome.failed();
        }
    }

    private Participant snapshot(Player player, int maxLives) {
        return new Participant(player.getUniqueId(), player.getName(), maxLives);
    }

    private record Participant(UUID uuid, String name, int maxLives) {
        private PlayerManager manager(LimitedLives plugin) {
            return new PlayerManager(plugin, uuid, name, maxLives);
        }
    }

    private static final class TargetSlot {
        private final UUID uuid;
        private final String name;
        private volatile Participant participant;

        private TargetSlot(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    public record Transfer(UUID targetUuid, String targetName, int amount, int sourceLives, int targetLives) {}

    public record Outcome(Status status, List<Transfer> transfers) {
        private static Outcome success(List<Transfer> transfers) { return new Outcome(Status.SUCCESS, transfers); }
        private static Outcome selfOnly() { return new Outcome(Status.SELF_ONLY, List.of()); }
        private static Outcome lastLife() { return new Outcome(Status.LAST_LIFE, List.of()); }
        private static Outcome failed() { return new Outcome(Status.FAILED, List.of()); }
        private static Outcome sourceRetired() { return new Outcome(Status.SOURCE_RETIRED, List.of()); }
    }

    public enum Status { SUCCESS, SELF_ONLY, LAST_LIFE, FAILED, SOURCE_RETIRED }
}
