package xyz.srnyx.limitedlives.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import xyz.srnyx.limitedlives.api.LifeLossContext;

/** Fired only after a life was applied to the cache and queued for crash-journal persistence. */
public final class PlayerLifeLostEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    @NotNull private final Player player;
    @NotNull private final LifeLossContext context;
    private final int oldLives;
    private final int newLives;

    public PlayerLifeLostEvent(@NotNull Player player, @NotNull LifeLossContext context, int oldLives, int newLives) {
        this.player = player;
        this.context = context;
        this.oldLives = oldLives;
        this.newLives = newLives;
    }

    @NotNull public Player getPlayer() { return player; }
    @NotNull public LifeLossContext getContext() { return context; }
    public int getOldLives() { return oldLives; }
    public int getNewLives() { return newLives; }
    public int getAmountLost() { return oldLives - newLives; }
    @Override @NotNull public HandlerList getHandlers() { return HANDLERS; }
    @NotNull public static HandlerList getHandlerList() { return HANDLERS; }
}
