# LimitedLives integration API

The API is published through Bukkit `ServicesManager` and is safe to call from any thread. Add the LimitedLives JAR as `compileOnly` and declare `LimitedLives` under `softdepend` in the consumer plugin.

Protection controls automatic life loss caused by death. It does not cancel damage and intentionally does not block administrative `/lives set|add|remove` operations.

```java
LimitedLivesApi api = Bukkit.getServicesManager().load(LimitedLivesApi.class);
if (api == null) return; // LimitedLives is not installed/enabled
```

## Awarding lives by UUID

The production mutation method is entity-free and works for both online and offline UUIDs. It serializes the read-modify-write operation through `LifeStore`, applies the configured global `lives.min`/`lives.max`, and uses the same crash-recovery journal as native LimitedLives changes:

```java
LifeMutationResult result = api.addLives(playerId, reward, LifeOverflowPolicy.CLAMP);
if (result.appliedAmount() > 0) {
    logger.info("Awarded " + result.appliedAmount() + " lives");
}
if (result.revived()) {
    // The UUID moved out of the no-lives state.
}
```

`CLAMP` awards as much as fits below `lives.max`; `REJECT` makes no change when the full requested amount would exceed the maximum. The result reports the old/new values, requested/applied amount, maximum limiting, revival and rejection. `addLives(UUID, int)` is the shorthand for `CLAMP` and returns the new total.

The synchronous methods require `api.isDataReady()` to be true and never query Bukkit entities. During early startup, use the non-blocking variant instead of waiting on a Folia tick thread:

```java
api.addLivesAsync(playerId, reward, LifeOverflowPolicy.CLAMP)
        .thenAccept(result -> auditQueue.accept(result)); // data-only continuation
```

The returned stage does not grant ownership of a Bukkit entity. Route any subsequent player message or inventory/world change through that player's `EntityScheduler`.

## Allowing selected PvP killers

When `lives.lose-on-player-kill` is disabled globally, an integration can opt a selected killer into the normal PvP life-loss flow. Use the scoped handle while the player is in the relevant state:

```java
PlayerKillLifeLossAllowance allowance =
        api.allowPlayerKillLifeLoss(morphedPlayerId, morphPlugin, "hostile-morph");

// When the morph ends:
allowance.close();
```

Allowances are reference-safe and are automatically revoked when their owner plugin disables. They bypass **only** the global `lose-on-player-kill` toggle. Victim life-loss protection, permissions, world rules, WorldGuard, grace, configured death causes and `PlayerLifeLossAttemptEvent` cancellation remain authoritative. Consequently, `PlayerStoleLifeEvent` is emitted only when the selected killer's victim actually lost a life.

## Protecting a morphed player

The simple toggle is available for integrations that exclusively own the state:

```java
api.disableLifeLoss(playerId);
api.enableLifeLoss(playerId);
```

Scoped protection is recommended. It is reference-safe when several systems protect the same player, is idempotent, and is automatically cleared if the owner plugin disables:

```java
LifeLossProtection protection = api.protect(playerId, morphPlugin, "hostile-morph");
// Keep this handle in the morph session.
protection.close();
```

## Cancelling an individual loss

`PlayerLifeLossAttemptEvent` runs in the victim's owning entity context after LimitedLives' built-in world/cause/grace checks and immediately before mutation. Cancelling it prevents the complete flow: data mutation, punishment, LimitedLives keep-inventory handling and killer reward.

```java
@EventHandler(ignoreCancelled = true)
public void onAttempt(PlayerLifeLossAttemptEvent event) {
    if (morphService.isHostile(event.getPlayer().getUniqueId())) event.setCancelled(true);
}
```

Do not perform files, JDBC, HTTP or other blocking work in this listener.

## Turning a hostile morph human after two successful kills

`PlayerStoleLifeEvent` is emitted only after the victim actually lost a life and is delivered through the killer's `EntityScheduler`. The killer may therefore be changed safely without looking up or touching an entity owned by another region.

```java
private final ConcurrentHashMap<UUID, Integer> stolenLives = new ConcurrentHashMap<>();

@EventHandler
public void onLifeStolen(PlayerStoleLifeEvent event) {
    Player killer = event.getKiller(); // current thread owns this entity
    UUID killerId = killer.getUniqueId();
    if (!morphService.isHostile(killerId)) return;

    int total = stolenLives.merge(killerId, 1, Integer::sum);
    if (total < 2) return;

    stolenLives.remove(killerId);
    morphService.becomeHuman(killer);
}
```

`PlayerLifeLostEvent` is the authoritative victim-context notification and always contains the immutable killer UUID/name when present. It is suitable for thread-safe counters, logging and analytics that do not need the live killer entity. `PlayerStoleLifeEvent` is the entity-context convenience event and is not delivered if the killer retires or the server begins stopping before its scheduler callback.

Never retain a `Player` from an API event. The `LifeLossContext` record is immutable and is the supported value to pass between threads or regions.
