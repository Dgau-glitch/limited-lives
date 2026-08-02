package xyz.srnyx.limitedlives.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import org.jetbrains.annotations.NotNull;

import xyz.srnyx.annoyingapi.AnnoyingListener;
import xyz.srnyx.annoyingapi.data.ItemData;

import xyz.srnyx.limitedlives.LimitedLives;
import xyz.srnyx.limitedlives.config.CraftingTrigger;
import xyz.srnyx.limitedlives.managers.player.PlayerManager;
import xyz.srnyx.limitedlives.services.player.LifeItemUseService;


public class PlayerInteractListener extends AnnoyingListener {
    @NotNull private final LimitedLives plugin;

    public PlayerInteractListener(@NotNull LimitedLives plugin) {
        this.plugin = plugin;
    }

    @Override @NotNull
    public LimitedLives getAnnoyingPlugin() {
        return plugin;
    }

    @EventHandler
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        final Action action = event.getAction();
        final ItemStack item = event.getItem();

        // Return if:
        if (
                // Physical "click"
                action == Action.PHYSICAL
                // Isn't left click
                || (((action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) || !plugin.config.obtaining.crafting.triggers.contains(CraftingTrigger.LEFT_CLICK))
                // Isn't right click
                && ((action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) || !plugin.config.obtaining.crafting.triggers.contains(CraftingTrigger.RIGHT_CLICK)))
                // Isn't holding item
                || (item == null || !new ItemData(plugin, item).has(PlayerManager.ITEM_KEY))) return;

        event.setCancelled(true);
        plugin.lifeItemUseService.use(event.getPlayer(), item, LifeItemUseService.Trigger.INTERACT);
    }
}
