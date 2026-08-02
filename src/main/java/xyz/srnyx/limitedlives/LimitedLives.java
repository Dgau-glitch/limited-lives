package xyz.srnyx.limitedlives;

import org.bukkit.Bukkit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import xyz.srnyx.annoyingapi.AnnoyingPlugin;
import xyz.srnyx.annoyingapi.PluginPlatform;

import xyz.srnyx.limitedlives.config.CraftingTrigger;
import xyz.srnyx.limitedlives.config.LimitedConfig;
import xyz.srnyx.limitedlives.listeners.CraftListener;
import xyz.srnyx.limitedlives.listeners.PlayerInteractListener;
import xyz.srnyx.limitedlives.listeners.PlayerItemConsumeListener;
import xyz.srnyx.limitedlives.listeners.PlayerListener;
import xyz.srnyx.limitedlives.managers.PlaceholderManager;
import xyz.srnyx.limitedlives.managers.WorldGuardManager;
import xyz.srnyx.limitedlives.managers.player.PlayerManager;
import xyz.srnyx.limitedlives.services.execution.FoliaExecutionService;
import xyz.srnyx.limitedlives.services.player.LifeStore;
import xyz.srnyx.limitedlives.services.player.LifeItemUseService;
import xyz.srnyx.limitedlives.services.player.OnlinePlayerDirectory;
import xyz.srnyx.limitedlives.services.player.LifeTransferService;
import xyz.srnyx.limitedlives.services.execution.CommandFeedbackService;
import xyz.srnyx.limitedlives.services.execution.RecipeRegistrationService;
import xyz.srnyx.limitedlives.services.execution.CommandExecutionService;
import xyz.srnyx.limitedlives.services.execution.GameRuleService;
import xyz.srnyx.limitedlives.services.player.PlaceholderSnapshotService;
import xyz.srnyx.annoyingapi.libs.javautilities.MiscUtility;

import java.io.File;
import java.util.logging.Level;


public class LimitedLives extends AnnoyingPlugin {
    public volatile LimitedConfig config;
    @NotNull public final FoliaExecutionService execution = new FoliaExecutionService(this);
    @NotNull public final LifeStore lifeStore = new LifeStore(this);
    @NotNull public final CommandFeedbackService feedback = new CommandFeedbackService(this);
    @NotNull public final LifeItemUseService lifeItemUseService = new LifeItemUseService(this);
    @NotNull public final OnlinePlayerDirectory onlinePlayers = new OnlinePlayerDirectory();
    @NotNull public final LifeTransferService lifeTransferService = new LifeTransferService(this);
    @NotNull public final RecipeRegistrationService recipes = new RecipeRegistrationService(this);
    @NotNull public final CommandExecutionService commands = new CommandExecutionService(this);
    @NotNull public final GameRuleService gameRules = new GameRuleService(this);
    @NotNull public final PlaceholderSnapshotService placeholders = new PlaceholderSnapshotService(this);
    @NotNull public final PlayerItemConsumeListener playerItemConsumeListener = new PlayerItemConsumeListener(this);
    @NotNull public final PlayerInteractListener playerInteractListener = new PlayerInteractListener(this);
    @NotNull public final CraftListener craftListener = new CraftListener(this);
    @Nullable public final WorldGuardManager worldGuard;

    public LimitedLives() {
        options
                // AnnoyingAPI 5.2.1 enables its optional metrics bridge through a
                // boolean resource key. Point it at a deliberately absent internal
                // key so the bridge is never loaded; its class is excluded from the JAR.
                .bStatsOptions(bStatsOptions -> bStatsOptions
                        .fileName("config.yml")
                        .toggleKey("__limitedlives_internal_metrics_disabled"))
                .pluginOptions(pluginOptions -> pluginOptions.updatePlatforms(new PluginPlatform.Multi(
                        PluginPlatform.modrinth("LvTKDASD"),
                        PluginPlatform.hangar(this),
                        PluginPlatform.spigot("109078"))))
                .dataOptions(dataOptions -> dataOptions
                        .enabled(true)
                        .useCacheDefault(false)
                        .entityDataColumns(
                                PlayerManager.LIVES_KEY,
                                PlayerManager.DEAD_KEY,
                                PlayerManager.GRACE_START_KEY))
                .registrationOptions
                .toRegister(new PlayerListener(this))
                .papiExpansionToRegister(() -> new PlaceholderManager(this))
                .automaticRegistration.packages("xyz.srnyx.limitedlives.commands");

        // Register WorldGuardManager (needs to happen on load before WorldGuard enables)
        WorldGuardManager worldGuardManager = null;
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) try {
            worldGuardManager = new WorldGuardManager();
        } catch (final Exception e) {
            AnnoyingPlugin.log(Level.WARNING, "&cFailed to register WorldGuard flag!", e);
        }
        worldGuard = worldGuardManager;
    }

    @Override
    public void enable() {
        // Force shaded executors to be created during RUNNING, never lazily from disable().
        MiscUtility.CPU_SCHEDULER.isShutdown();
        MiscUtility.IO_SCHEDULER.isShutdown();
        execution.start();
        disableIntervalCacheTask();
        Bukkit.getOnlinePlayers().forEach(player -> execution.runForEntityOrNow(player, () -> {
            onlinePlayers.joined(player);
            placeholders.capture(player);
        }, () -> {}));
        reload();
    }

    @Override
    public void disable() {
        // AnnoyingPlugin's final onDisable() synchronously flushes its cache and closes
        // SQL before invoking this extension point. No work is submitted from here.
        execution.stop();
        lifeStore.close();
        MiscUtility.CPU_SCHEDULER.shutdownNow();
        MiscUtility.IO_SCHEDULER.shutdownNow();
    }

    @Override
    public void reload() {
        disableIntervalCacheTask();
        // Phase 1: fully parse and validate a detached candidate.
        final LimitedConfig next = new LimitedConfig(this);
        // Phase 2: publish the complete immutable snapshot in one volatile write.
        config = next;
        // Phase 3: apply Bukkit effects from the global region context.
        gameRules.apply(next);
        recipes.replace(next.obtaining.crafting.recipe);
        // Store WorldGuard RegionContainer (needs to happen on enable after WorldGuard enables)
        if (worldGuard != null) worldGuard.storeRegionContainer();
        // Detect very old data (data/data.yml, 2.0.1 and lower)
        final File oldDataFile = new File(getDataFolder(), "data/data.yml");
        if (oldDataFile.exists()) log(Level.SEVERE, "&c&lOld data detected!&c To keep your old data, please update to &43.0.1&c FIRST and then to &4" + getPluginMeta().getVersion() + "&c! &oIf this is incorrect, delete &4&o" + oldDataFile.getPath());

        // Register appropriate listeners
        playerItemConsumeListener.setRegistered(next.obtaining.crafting.triggers.contains(CraftingTrigger.CONSUME));
        playerInteractListener.setRegistered(next.obtaining.crafting.triggers.contains(CraftingTrigger.LEFT_CLICK) || next.obtaining.crafting.triggers.contains(CraftingTrigger.RIGHT_CLICK));
        craftListener.setRegistered(next.obtaining.crafting.recipe != null);
    }

    private void disableIntervalCacheTask() {
        if (dataManager == null || dataManager.cacheSavingTask == null) return;
        dataManager.cacheSavingTask.cancel();
        dataManager.cacheSavingTask = null;
    }
}
