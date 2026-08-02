package xyz.srnyx.limitedlives.api;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import xyz.srnyx.limitedlives.api.internal.DefaultLimitedLivesApi;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultLimitedLivesApiTest {
    @Test
    void manualAndScopedProtectionsDoNotOverrideEachOther() {
        final DefaultLimitedLivesApi api = new DefaultLimitedLivesApi();
        final UUID playerId = UUID.randomUUID();
        final Plugin owner = pluginProxy("MorphMob");

        api.disableLifeLoss(playerId);
        final LifeLossProtection protection = api.protect(playerId, owner, "hostile-morph");
        api.enableLifeLoss(playerId);
        assertFalse(api.isLifeLossEnabled(playerId));

        protection.close();
        assertTrue(api.isLifeLossEnabled(playerId));
        protection.close();
        assertTrue(api.isLifeLossEnabled(playerId));
    }

    @Test
    void clearingOwnerDoesNotRemoveAnotherPluginsProtection() {
        final DefaultLimitedLivesApi api = new DefaultLimitedLivesApi();
        final UUID playerId = UUID.randomUUID();
        final Plugin first = pluginProxy("First");
        final Plugin second = pluginProxy("Second");
        api.protect(playerId, first, "first");
        final LifeLossProtection remaining = api.protect(playerId, second, "second");

        api.clearProtections(first);
        assertFalse(api.isLifeLossEnabled(playerId));
        remaining.close();
        assertTrue(api.isLifeLossEnabled(playerId));
    }

    private static Plugin pluginProxy(String name) {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getName", "toString" -> name;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }
}
