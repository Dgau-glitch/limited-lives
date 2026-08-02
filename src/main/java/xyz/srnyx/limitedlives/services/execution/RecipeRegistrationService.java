package xyz.srnyx.limitedlives.services.execution;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;
import org.jetbrains.annotations.Nullable;
import xyz.srnyx.limitedlives.LimitedLives;

import java.util.logging.Level;

/** Owns the one globally registered life recipe and prevents duplicates on reload. */
public final class RecipeRegistrationService {
    private final LimitedLives plugin;
    private NamespacedKey registeredKey;

    public RecipeRegistrationService(LimitedLives plugin) {
        this.plugin = plugin;
    }

    public void replace(@Nullable Recipe recipe) {
        if (registeredKey != null) {
            plugin.getServer().removeRecipe(registeredKey);
            registeredKey = null;
        }
        if (recipe == null) return;
        if (!(recipe instanceof Keyed keyed)) {
            plugin.log(Level.WARNING, "Configured life recipe is not keyed and cannot be safely reloaded");
            return;
        }
        if (plugin.getServer().addRecipe(recipe)) registeredKey = keyed.getKey();
    }
}
