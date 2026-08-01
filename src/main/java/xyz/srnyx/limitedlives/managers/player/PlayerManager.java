package xyz.srnyx.limitedlives.managers.player;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import xyz.srnyx.annoyingapi.AnnoyingPlugin;
import xyz.srnyx.annoyingapi.utility.BukkitUtility;

import xyz.srnyx.limitedlives.LimitedLives;
import xyz.srnyx.limitedlives.config.GracePeriodTrigger;
import xyz.srnyx.limitedlives.managers.player.exception.ActionException;
import xyz.srnyx.limitedlives.managers.player.exception.LessThanMinLives;
import xyz.srnyx.limitedlives.managers.player.exception.MoreThanMaxLives;
import xyz.srnyx.limitedlives.managers.player.exception.RecipeNotSet;
import xyz.srnyx.limitedlives.services.player.LifeValuePolicy;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;


public class PlayerManager {
    @NotNull public static final String LIVES_KEY = "ll_lives";
    @NotNull public static final String DEAD_KEY = "ll_dead";
    @NotNull public static final String GRACE_START_KEY = "grace_start";
    @NotNull public static final String ITEM_KEY = "ll_item";

    @NotNull private final LimitedLives plugin;
    @NotNull private final OfflinePlayer offline;
    @NotNull private final UUID uuid;

    public PlayerManager(@NotNull LimitedLives plugin, @NotNull OfflinePlayer offline) {
        this.plugin = plugin;
        this.offline = offline;
        this.uuid = offline.getUniqueId();
    }

    public int getLives() {
        return plugin.lifeStore.atomic(uuid, this::getLivesUnlocked);
    }

    private int getLivesUnlocked() {
        final String livesString = plugin.lifeStore.get(uuid, LIVES_KEY);
        if (livesString != null) try {
            return Integer.parseInt(livesString);
        } catch (final NumberFormatException e) {
            AnnoyingPlugin.log(Level.WARNING, "&cRemoving invalid lives from &4" + offline.getName() + "&c: &4" + livesString);
            plugin.lifeStore.remove(uuid, LIVES_KEY);
        }
        return plugin.config.lives.def;
    }

    public int getMaxLives() {
        final Player online = offline.getPlayer();
        if (online == null) return plugin.config.lives.max;
        return BukkitUtility.getPermissionValue(online, "limitedlives.max.")
                .map(Long::intValue)
                .orElse(plugin.config.lives.max);
    }

    /**
     * {@link #getMaxLives()} - {@link #getLives()}
     *
     * @return  the amount of deaths the player has
     */
    public int getDeaths() {
        return getMaxLives() - getLives();
    }

    public long getGraceLeft() {
        return plugin.lifeStore.atomic(uuid, this::getGraceLeftUnlocked);
    }

    private long getGraceLeftUnlocked() {
        if (!plugin.config.gracePeriod.enabled) return 0;
        final String graceStart = plugin.lifeStore.get(uuid, GRACE_START_KEY);
        if (graceStart == null) return 0;

        // Calculate
        final long graceLeft;
        try {
            graceLeft = LifeValuePolicy.graceLeft(Long.parseLong(graceStart), plugin.config.gracePeriod.duration.toMillis(), System.currentTimeMillis());
        } catch (final NumberFormatException e) {
            AnnoyingPlugin.log(Level.WARNING, "&cRemoved invalid " + GRACE_START_KEY + " value for &4" + offline.getName() + "&c: &4" + graceStart, e);
            plugin.lifeStore.remove(uuid, GRACE_START_KEY);
            return 0;
        }

        // Return
        if (graceLeft <= 0) {
            plugin.lifeStore.remove(uuid, GRACE_START_KEY);
            return 0;
        }
        return graceLeft;
    }

    public boolean hasGrace() {
        return getGraceLeft() > 0;
    }

    public int setLives(int amount) throws ActionException {
        return plugin.lifeStore.atomicChecked(uuid, () -> {
            LifeValuePolicy.set(amount, plugin.config.lives.min, getMaxLives());
            final int oldLives = getLivesUnlocked();
            plugin.lifeStore.set(uuid, LIVES_KEY, amount);
            if (LifeValuePolicy.shouldRevive(oldLives, amount, plugin.config.lives.min)) revive();
            if (LifeValuePolicy.shouldKill(amount, plugin.config.lives.min)) kill(null);
            return amount;
        });
    }

    public int addLives(int amount) throws MoreThanMaxLives {
        return plugin.lifeStore.atomicChecked(uuid, () -> {
            final int oldLives = getLivesUnlocked();
            final int newLives = LifeValuePolicy.add(oldLives, amount, getMaxLives());
            plugin.lifeStore.set(uuid, LIVES_KEY, newLives);
            if (LifeValuePolicy.shouldRevive(oldLives, newLives, plugin.config.lives.min)) revive();
            return newLives;
        });
    }

    public int removeLives(int amount, @Nullable Player killer) throws LessThanMinLives {
        return plugin.lifeStore.atomicChecked(uuid, () -> {
            final int newLives = LifeValuePolicy.remove(getLivesUnlocked(), amount, plugin.config.lives.min);
            plugin.lifeStore.set(uuid, LIVES_KEY, newLives);
            if (LifeValuePolicy.shouldKill(newLives, plugin.config.lives.min)) kill(killer);
            return newLives;
        });
    }

    public int withdrawLives(@NotNull Player sender, int amount) throws LessThanMinLives, RecipeNotSet {
        if (plugin.config.obtaining.crafting.recipe == null) throw new RecipeNotSet();
        final ItemStack item = plugin.config.obtaining.crafting.recipe.getResult();
        item.setAmount(amount);
        sender.getInventory().addItem(item);
        return removeLives(amount, null);
    }

    private void revive() {
        plugin.lifeStore.remove(uuid, DEAD_KEY);
        // Start grace period
        if (plugin.config.gracePeriod.triggers.contains(GracePeriodTrigger.REVIVE)) plugin.lifeStore.set(uuid, GRACE_START_KEY, System.currentTimeMillis());
        // Dispatch revive commands
        dispatchCommands(plugin.config.commands.revive, null);
    }

    private void kill(@Nullable Player killer) {
        plugin.lifeStore.set(uuid, DEAD_KEY, killer != null ? killer.getUniqueId().toString() : "null");
        dispatchCommands(plugin.config.commands.punishment.death, killer);
    }

    public void dispatchCommands(@NotNull List<String> commands, @Nullable OfflinePlayer killer) {
        plugin.execution.runGlobal(() -> {
            for (String command : commands) {
                if (command.contains("%killer%")) {
                    if (killer == null) continue;
                    command = command.replace("%killer%", killer.getName());
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", offline.getName()));
            }
        });
    }

}
