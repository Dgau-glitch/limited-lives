package xyz.srnyx.limitedlives.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import xyz.srnyx.limitedlives.api.LifeLossContext;

/**
 * PvP convenience event delivered in the killer's owning entity context after
 * the victim actually lost a life. The context contains no live victim entity.
 */
public final class PlayerStoleLifeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    @NotNull private final Player killer;
    @NotNull private final LifeLossContext context;
    private final int victimOldLives;
    private final int victimNewLives;

    public PlayerStoleLifeEvent(@NotNull Player killer, @NotNull LifeLossContext context,
                                int victimOldLives, int victimNewLives) {
        this.killer = killer;
        this.context = context;
        this.victimOldLives = victimOldLives;
        this.victimNewLives = victimNewLives;
    }

    @NotNull public Player getKiller() { return killer; }
    @NotNull public LifeLossContext getContext() { return context; }
    public int getVictimOldLives() { return victimOldLives; }
    public int getVictimNewLives() { return victimNewLives; }
    @Override @NotNull public HandlerList getHandlers() { return HANDLERS; }
    @NotNull public static HandlerList getHandlerList() { return HANDLERS; }
}
