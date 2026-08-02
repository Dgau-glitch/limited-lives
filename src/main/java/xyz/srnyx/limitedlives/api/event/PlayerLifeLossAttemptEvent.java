package xyz.srnyx.limitedlives.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import xyz.srnyx.limitedlives.api.LifeLossContext;

/** Called in the victim's owning entity context immediately before data mutation. */
public final class PlayerLifeLossAttemptEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    @NotNull private final Player player;
    @NotNull private final LifeLossContext context;
    private boolean cancelled;

    public PlayerLifeLossAttemptEvent(@NotNull Player player, @NotNull LifeLossContext context) {
        this.player = player;
        this.context = context;
    }

    @NotNull public Player getPlayer() { return player; }
    @NotNull public LifeLossContext getContext() { return context; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override @NotNull public HandlerList getHandlers() { return HANDLERS; }
    @NotNull public static HandlerList getHandlerList() { return HANDLERS; }
}
