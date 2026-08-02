package xyz.srnyx.limitedlives.managers;

import org.bukkit.entity.Player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import xyz.srnyx.annoyingapi.AnnoyingPAPIExpansion;

import xyz.srnyx.limitedlives.LimitedLives;
import xyz.srnyx.limitedlives.managers.player.PlayerManager;
import xyz.srnyx.limitedlives.services.player.PlaceholderSnapshotService;

import java.util.UUID;


public class PlaceholderManager extends AnnoyingPAPIExpansion {
    @NotNull private final LimitedLives plugin;

    public PlaceholderManager(@NotNull LimitedLives plugin) {
        this.plugin = plugin;
    }

    @Override @NotNull
    public LimitedLives getAnnoyingPlugin() {
        return plugin;
    }

    @Override @NotNull
    public String getIdentifier() {
        return "lives";
    }

    @Override @Nullable
    public String onPlaceholderRequest(@Nullable Player player, @NotNull String identifier) {
        // Non-player placeholders
        switch (identifier) {
            // default
            case "default": return String.valueOf(plugin.config.lives.def);
            // max
            case "max": return String.valueOf(plugin.config.lives.max);
            // min
            case "min": return String.valueOf(plugin.config.lives.min);
        }

        String explicitName = "";
        if (player == null) {
            final int underscoreIndex = identifier.indexOf('_');
            if (underscoreIndex == -1) return null;
            explicitName = identifier.substring(underscoreIndex + 1);
            identifier = identifier.substring(0, underscoreIndex).toLowerCase(); // Needs to be set after player
        }
        final UUID uuid = plugin.placeholders.resolve(player, explicitName);
        if (uuid == null) return "N/A";
        final PlaceholderSnapshotService.PlayerSnapshot snapshot = plugin.placeholders.get(uuid);
        // Player placeholders
        switch (identifier) {
            // lives
            case "lives": return String.valueOf(snapshot.lives());
            // max
            case "max": return String.valueOf(snapshot.maxLives());
            // grace-active
            case "grace-active": return String.valueOf(snapshot.graceActive());
            // grace-left
            case "grace-left": return String.valueOf(snapshot.graceLeft());
            // bypass
            case "bypass": return String.valueOf(snapshot.bypass());
        }

        // Unknown placeholder
        return null;
    }
}
