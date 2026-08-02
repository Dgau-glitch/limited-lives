package xyz.srnyx.limitedlives.commands;

import org.jetbrains.annotations.NotNull;

import xyz.srnyx.annoyingapi.command.AnnoyingCommand;
import xyz.srnyx.annoyingapi.command.AnnoyingSender;
import xyz.srnyx.annoyingapi.message.AnnoyingMessage;

import xyz.srnyx.limitedlives.LimitedLives;


public class ReloadCmd extends AnnoyingCommand {
    @NotNull private final LimitedLives plugin;

    public ReloadCmd(@NotNull LimitedLives plugin) {
        this.plugin = plugin;
    }

    @Override @NotNull
    public LimitedLives getAnnoyingPlugin() {
        return plugin;
    }

    @Override @NotNull
    public String getName() {
        return "lifereload";
    }

    @Override @NotNull
    public String getPermission() {
        return "limitedlives.reload";
    }

    @Override
    public void onCommand(@NotNull AnnoyingSender sender) {
        // AnnoyingPlugin.reloadPlugin() replaces DataManager and its in-memory cache.
        // LimitedLives persistence configuration is restart-only, so a hot reload must
        // reload messages/configuration without reopening or replacing the database.
        plugin.execution.runAsync(() -> {
            plugin.loadMessages();
            plugin.execution.runGlobal(() -> {
                plugin.reload();
                plugin.feedback.deliver(sender, () -> new AnnoyingMessage(plugin, "reload").send(sender));
            });
        });
    }

    @Override
    public java.util.Collection<String> onTabComplete(@NotNull AnnoyingSender sender) {
        return java.util.Collections.emptyList();
    }
}
