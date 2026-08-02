package xyz.srnyx.limitedlives.services.player;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xyz.srnyx.annoyingapi.cooldown.AnnoyingCooldown;
import xyz.srnyx.annoyingapi.message.AnnoyingMessage;
import xyz.srnyx.annoyingapi.message.DefaultReplaceType;
import xyz.srnyx.limitedlives.LimitedLives;
import xyz.srnyx.limitedlives.config.Feature;
import xyz.srnyx.limitedlives.managers.player.PlayerManager;
import xyz.srnyx.limitedlives.managers.player.exception.MoreThanMaxLives;

/** Shared entity-context implementation for consuming and clicking life items. */
public final class LifeItemUseService {
    @NotNull private static final String COOLDOWN_KEY = "use_item";
    @NotNull private final LimitedLives plugin;

    public LifeItemUseService(@NotNull LimitedLives plugin) {
        this.plugin = plugin;
    }

    @NotNull
    public Result use(@NotNull Player player, @NotNull ItemStack item, @NotNull Trigger trigger) {
        final World world = player.getWorld();
        if (!plugin.config.worldsBlacklist.isWorldEnabled(world, Feature.LIFE_USE)) {
            new AnnoyingMessage(plugin, "feature-disabled")
                    .replace("%feature%", Feature.LIFE_USE)
                    .replace("%world%", world.getName())
                    .send(player);
            return Result.REJECTED;
        }

        if (trigger == Trigger.INTERACT) {
            final AnnoyingCooldown cooldown = plugin.cooldownManager.getCooldownElseNew(player.getUniqueId(), COOLDOWN_KEY);
            if (cooldown.isOnCooldownStart(plugin.config.obtaining.crafting.cooldown.toMillis())) {
                new AnnoyingMessage(plugin, "eat.cooldown")
                        .replace("%remaining%", cooldown.getRemaining(), DefaultReplaceType.TIME)
                        .send(player);
                return Result.REJECTED;
            }
            // Preserve the established click behavior: the item is consumed before the max check.
            item.setAmount(item.getAmount() - 1);
        }

        try {
            final int lives = new PlayerManager(plugin, player).addLives(plugin.config.obtaining.crafting.amount);
            new AnnoyingMessage(plugin, "eat.success").replace("%lives%", lives).send(player);
            return Result.APPLIED;
        } catch (final MoreThanMaxLives exception) {
            new AnnoyingMessage(plugin, "eat.max").replace("%max%", plugin.config.lives.max).send(player);
            return Result.REJECTED;
        }
    }

    public enum Trigger { CONSUME, INTERACT }
    public enum Result { APPLIED, REJECTED }
}
