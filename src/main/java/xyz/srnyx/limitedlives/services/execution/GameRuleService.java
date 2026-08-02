package xyz.srnyx.limitedlives.services.execution;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.Registry;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import xyz.srnyx.annoyingapi.AnnoyingPlugin;
import xyz.srnyx.limitedlives.LimitedLives;
import xyz.srnyx.limitedlives.config.LimitedConfig;

import java.util.logging.Level;

/** Applies world-global game rule effects only from the global region context. */
public final class GameRuleService {
    @SuppressWarnings("unchecked")
    private static final GameRule<Boolean> KEEP_INVENTORY = (GameRule<Boolean>) Registry.GAME_RULE.get(NamespacedKey.minecraft("keep_inventory"));
    @NotNull private final LimitedLives plugin;

    public GameRuleService(@NotNull LimitedLives plugin) {
        this.plugin = plugin;
    }

    public void apply(@NotNull LimitedConfig snapshot) {
        if (!snapshot.keepInventory.enabled) return;
        for (final World world : plugin.getServer().getWorlds()) {
            if (!Boolean.TRUE.equals(world.getGameRuleValue(KEEP_INVENTORY))) continue;
            AnnoyingPlugin.log(Level.WARNING, "keepInventory is enabled in " + world.getName() + "; disabling it for LimitedLives keep-inventory handling");
            world.setGameRule(KEEP_INVENTORY, false);
        }
    }
}
