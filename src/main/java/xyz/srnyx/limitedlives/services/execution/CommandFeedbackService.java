package xyz.srnyx.limitedlives.services.execution;

import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.command.BlockCommandSender;
import org.jetbrains.annotations.NotNull;
import xyz.srnyx.annoyingapi.command.AnnoyingSender;
import xyz.srnyx.limitedlives.LimitedLives;

/** Delivers command feedback in the owning context of its receiver. */
public final class CommandFeedbackService {
    @NotNull private final LimitedLives plugin;

    public CommandFeedbackService(@NotNull LimitedLives plugin) {
        this.plugin = plugin;
    }

    public boolean deliver(@NotNull AnnoyingSender sender, @NotNull Runnable feedback) {
        if (sender.isPlayer) {
            final Player player = sender.getPlayer();
            return plugin.execution.runForEntityOrNow(player, feedback, () -> {});
        }
        if (sender.cmdSender instanceof Entity entity) return plugin.execution.runForEntityOrNow(entity, feedback, () -> {});
        if (sender.cmdSender instanceof BlockCommandSender block) return plugin.execution.runForRegion(block.getBlock().getLocation(), feedback);
        return plugin.execution.runGlobalOrNow(feedback);
    }

    public boolean deliver(@NotNull Player player, @NotNull Runnable feedback) {
        return plugin.execution.runForEntityOrNow(player, feedback, () -> {});
    }
}
