package xyz.srnyx.limitedlives.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import org.jetbrains.annotations.NotNull;

import xyz.srnyx.annoyingapi.AnnoyingListener;
import xyz.srnyx.annoyingapi.data.ItemData;

import xyz.srnyx.limitedlives.LimitedLives;
import xyz.srnyx.limitedlives.managers.player.PlayerManager;
import xyz.srnyx.limitedlives.services.player.LifeItemUseService;


public class PlayerItemConsumeListener extends AnnoyingListener {
    @NotNull private final LimitedLives plugin;

    public PlayerItemConsumeListener(@NotNull LimitedLives plugin) {
        this.plugin = plugin;
    }

    @Override @NotNull
    public LimitedLives getAnnoyingPlugin() {
        return plugin;
    }

    @EventHandler
    public void onPlayerItemConsume(@NotNull PlayerItemConsumeEvent event) {
        // Not eating life item
        if (!new ItemData(plugin, event.getItem()).has(PlayerManager.ITEM_KEY)) return;

        if (plugin.lifeItemUseService.use(event.getPlayer(), event.getItem(), LifeItemUseService.Trigger.CONSUME) == LifeItemUseService.Result.REJECTED) event.setCancelled(true);
    }
}
