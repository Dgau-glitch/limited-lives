package xyz.srnyx.limitedlives.listeners;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import org.jetbrains.annotations.NotNull;

import xyz.srnyx.annoyingapi.AnnoyingListener;
import xyz.srnyx.annoyingapi.AnnoyingPlugin;
import xyz.srnyx.annoyingapi.data.EntityData;
import xyz.srnyx.annoyingapi.message.AnnoyingMessage;
import xyz.srnyx.annoyingapi.message.DefaultReplaceType;

import xyz.srnyx.limitedlives.config.Feature;
import xyz.srnyx.limitedlives.config.GracePeriodTrigger;
import xyz.srnyx.limitedlives.LimitedLives;
import xyz.srnyx.limitedlives.managers.player.PlayerManager;
import xyz.srnyx.limitedlives.managers.player.exception.ActionException;
import xyz.srnyx.limitedlives.managers.player.exception.LessThanMinLives;
import xyz.srnyx.limitedlives.services.player.LifeLossPolicy;
import xyz.srnyx.limitedlives.api.LifeLossContext;
import xyz.srnyx.limitedlives.api.event.PlayerLifeLossAttemptEvent;
import xyz.srnyx.limitedlives.api.event.PlayerLifeLostEvent;
import xyz.srnyx.limitedlives.api.event.PlayerStoleLifeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;


public class PlayerListener extends AnnoyingListener {
    @NotNull private final LimitedLives plugin;

    public PlayerListener(@NotNull LimitedLives plugin) {
        this.plugin = plugin;
    }

    @Override @NotNull
    public LimitedLives getAnnoyingPlugin() {
        return plugin;
    }

    @EventHandler
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        final Player player = event.getEntity();
        
        // Check if plugin enabled in world or player bypasses
        final World world = player.getWorld();
        if (!plugin.config.worldsBlacklist.isWorldEnabled(world, Feature.LIFE_LOSS) || player.hasPermission("limitedlives.bypass")) return;

        // Get killer
        final Player killer = player.getKiller();
        final boolean isPvp = killer != null && killer != player;
        final UUID killerUuid = isPvp ? killer.getUniqueId() : null;
        final String killerName = isPvp ? killer.getName() : null;

        // Get death cause
        String cause = "PLAYER_ATTACK";
        if (!isPvp) {
            final EntityDamageEvent damageEvent = player.getLastDamageCause();
            cause = damageEvent != null ? damageEvent.getCause().name() : null;
        }

        // Check PvP toggle and death cause before entering the life-loss flow.
        if (!LifeLossPolicy.shouldLoseLife(isPvp, plugin.config.lives.loseOnPlayerKill, cause, plugin.config.deathCauses)) return;
        // Public API protection is UUID-only and safe in every Folia context.
        if (!plugin.getApi().isLifeLossEnabled(player.getUniqueId())) return;
        // Check WorldGuard regions
        if (plugin.worldGuard != null && !plugin.worldGuard.test(player, player.getLocation())) return;
        // Check grace
        final PlayerManager manager = new PlayerManager(plugin, player);
        if (cause == null || !plugin.config.gracePeriod.bypassCauses.contains(cause)) {
            final long graceLeft = manager.getGraceLeft();
            if (graceLeft > 0) {
                new AnnoyingMessage(plugin, "lives.grace")
                        .replace("%remaining%", graceLeft, DefaultReplaceType.TIME)
                        .send(player);
                return;
            }
        }

        final LifeLossContext lossContext = new LifeLossContext(
                player.getUniqueId(), player.getName(), cause, isPvp,
                killerUuid, killerName, System.currentTimeMillis());
        final PlayerLifeLossAttemptEvent attemptEvent = new PlayerLifeLossAttemptEvent(player, lossContext);
        Bukkit.getPluginManager().callEvent(attemptEvent);
        if (attemptEvent.isCancelled()) return;

        // Remove life
        try {
            final int newLives = manager.removeLives(1, killerUuid, killerName);
            Bukkit.getPluginManager().callEvent(new PlayerLifeLostEvent(player, lossContext, newLives + 1, newLives));
            if (isPvp) plugin.execution.runForEntityOrNow(killer,
                    () -> Bukkit.getPluginManager().callEvent(new PlayerStoleLifeEvent(killer, lossContext, newLives + 1, newLives)),
                    () -> {});
            if (newLives <= plugin.config.lives.min) {
                // No more lives
                new AnnoyingMessage(plugin, "lives.zero").send(player);
            } else if (isPvp) {
                // Lose to player
                new AnnoyingMessage(plugin, "lives.lose.player")
                        .replace("%killer%", killerName)
                        .replace("%lives%", newLives)
                        .send(player);
            } else {
                // Lose to other
                new AnnoyingMessage(plugin, "lives.lose.other")
                        .replace("%lives%", newLives)
                        .send(player);
            }
        } catch (final LessThanMinLives e) {
            // No more lives
            new AnnoyingMessage(plugin, "lives.zero").send(player);
        }

        // keepInventory integration
        if (plugin.config.keepInventory.enabled && plugin.config.worldsBlacklist.isWorldEnabled(world, Feature.KEEP_INVENTORY)) plugin.config.keepInventory.actions.getAction(manager.getDeaths()).consumer.accept(event);

        // Give life to killer
        if (plugin.config.obtaining.stealing && isPvp && plugin.config.worldsBlacklist.isWorldEnabled(world, Feature.OBTAINING_STEALING)) {
            final String victimName = player.getName();
            plugin.execution.runForEntityOrNow(killer, () -> {
                try {
                    new AnnoyingMessage(plugin, "lives.steal")
                            .replace("%target%", victimName)
                            .replace("%lives%", new PlayerManager(plugin, killer).addLives(1))
                            .send(killer);
                } catch (final ActionException ignored) {}
            }, () -> {});
        }
    }

    @EventHandler
    public void onPlayerRespawn(@NotNull PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        final String killerString = plugin.lifeStore.get(uuid, PlayerManager.DEAD_KEY);
        if (killerString == null) return;
        plugin.lifeStore.remove(uuid, PlayerManager.DEAD_KEY);

        // Get killer
        String killerName = null;
        if (!killerString.equals("null")) try {
            killerName = Bukkit.getOfflinePlayer(UUID.fromString(killerString)).getName();
        } catch (final IllegalArgumentException ignored) {}

        // Run respawn commands
        new PlayerManager(plugin, player).dispatchCommands(plugin.config.commands.punishment.respawn, killerName);
    }

    @EventHandler
    public void onEntityDamageByEntity(@NotNull EntityDamageEvent event) {
        final Entity entity = event.getEntity();
        if (!(entity instanceof Player)) return;
        final String cause = event instanceof EntityDamageByEntityEvent && ((EntityDamageByEntityEvent) event).getDamager() instanceof Player ? "PLAYER_ATTACK" : event.getCause().name();
        if (plugin.config.gracePeriod.disabledDamageCauses.contains(cause) && new PlayerManager(plugin, (Player) entity).hasGrace()) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        plugin.onlinePlayers.joined(player);
        final EntityData data = new EntityData(plugin, player);
        final String playerName = player.getName();

        // Legacy file/database conversion is blocking and must never run on the entity tick thread.
        plugin.execution.runAsync(() -> {
            final Map<String, String> failed = data.convertOldData(true, PlayerManager.LIVES_KEY, PlayerManager.DEAD_KEY);
            if (failed == null) {
                AnnoyingPlugin.log(Level.SEVERE, "Failed to convert old data for player " + playerName);
            } else if (!failed.isEmpty()) {
                AnnoyingPlugin.log(Level.WARNING, "Failed to convert some old data for player " + playerName + ": " + failed);
            }
        });

        // Start grace period
        if (plugin.config.gracePeriod.enabled && (plugin.config.gracePeriod.triggers.contains(GracePeriodTrigger.JOIN) || (plugin.config.gracePeriod.triggers.contains(GracePeriodTrigger.FIRST_JOIN) && !player.hasPlayedBefore()))) plugin.lifeStore.set(player.getUniqueId(), PlayerManager.GRACE_START_KEY, System.currentTimeMillis());
        plugin.placeholders.capture(player);
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        plugin.onlinePlayers.quit(event.getPlayer());
        plugin.placeholders.remove(event.getPlayer().getUniqueId());
    }
}
