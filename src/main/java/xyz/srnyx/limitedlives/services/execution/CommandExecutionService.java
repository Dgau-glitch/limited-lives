package xyz.srnyx.limitedlives.services.execution;

import org.bukkit.Bukkit;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import xyz.srnyx.annoyingapi.command.AnnoyingSender;
import xyz.srnyx.limitedlives.LimitedLives;

/** Ensures command parsing starts in the owning context of the command source. */
public final class CommandExecutionService {
    @NotNull private final LimitedLives plugin;

    public CommandExecutionService(@NotNull LimitedLives plugin) {
        this.plugin = plugin;
    }

    public boolean isOwnedContext(@NotNull AnnoyingSender sender) {
        if (sender.cmdSender instanceof Entity entity) return plugin.getServer().isOwnedByCurrentRegion(entity);
        if (sender.cmdSender instanceof BlockCommandSender block) return plugin.getServer().isOwnedByCurrentRegion(block.getBlock());
        return Bukkit.isGlobalTickThread();
    }

    public void schedule(@NotNull AnnoyingSender sender, @NotNull Runnable command) {
        if (sender.cmdSender instanceof Entity entity) {
            plugin.execution.runForEntity(entity, command, () -> {});
        } else if (sender.cmdSender instanceof BlockCommandSender block) {
            plugin.execution.runForRegion(block.getBlock().getLocation(), command);
        } else {
            plugin.execution.runGlobal(command);
        }
    }
}
