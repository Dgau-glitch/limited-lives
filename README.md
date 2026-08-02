# Limited Lives

## Requirements

- **Folia 1.21.11** (this build is Folia-only and is not supported on ordinary Paper/Spigot servers)
- **Java 21**
- Optional: PlaceholderAPI 2.12.2 or newer
- Optional: WorldGuard 7.0.15 with its matching WorldEdit dependency

All player/entity work is dispatched through Folia ownership schedulers. Placeholder values use non-blocking snapshots, so permission-derived values are refreshed from player-owned event/command contexts rather than blocking a region thread.

## Building

Use the checked-in wrapper rather than an IDE-bundled or system Gradle:

```bash
./gradlew clean build
```

The wrapper uses checksum-verified Gradle 8.14.3 and the build selects a Java 21 toolchain. In IntelliJ IDEA, set **Gradle distribution** to `Wrapper` and **Gradle JVM** to Java 21. This prevents IDE/Gradle worker bootstrap mismatches and keeps local and CI builds identical.

## Integration API

Add the LimitedLives JAR as a `compileOnly` dependency and `LimitedLives` as a `softdepend`. Resolve the API through Bukkit's `ServicesManager`:

```java
LimitedLivesApi api = Bukkit.getServicesManager().load(LimitedLivesApi.class);
```

For a simple toggle use `disableLifeLoss(uuid)` / `enableLifeLoss(uuid)`. For production integrations prefer a scoped handle, because multiple plugins can protect the same player safely:

```java
LifeLossProtection protection = api.protect(playerId, morphPlugin, "hostile-morph");
// Store the handle with the morph session.
protection.close(); // when the player becomes human
```

`PlayerLifeLossAttemptEvent` is cancellable and runs in the victim's owning entity context immediately before mutation. `PlayerLifeLostEvent` fires there only after a life was actually removed. For PvP integrations, `PlayerStoleLifeEvent` is then delivered in the killer's owning entity context, so a morph plugin can safely count two successful life steals and transform `event.getKiller()` back without cross-region access. Its immutable context identifies the victim without exposing the victim entity.

See [`docs/INTEGRATION_API.md`](docs/INTEGRATION_API.md) for lifecycle, cancellation and hostile-morph examples.

Every player has a limited amount of lives. When a player loses all of their lives, they are punished (according to the config). Almost everything is configurable. *Originally made for [Mickaboo](https://youtube.com/@Mickabo)*

**🐛 Bugs / 💡 Suggestions:** Please [open an issue](https://github.com/srnyx/limited-lives/issues/new/choose) to report a bug or suggest an idea

**🆘 Support:** Please [join the Discord](https://srnyx.com/discord) to get support

## Download

**✅ Stable:** You can download the latest **stable** version at [Modrinth](https://modrinth.com/plugin/limitedlives), [Hangar](https://hangar.papermc.io/srnyx/LimitedLives), [Spigot](https://spigotmc.org/resources/109078), [Bukkit](https://dev.bukkit.org/projects/limited-lives), or [GitHub](https://github.com/srnyx/limited-lives/releases)

**🚧 Snapshot:** You can download the latest **snapshot** version at [actions/workflows/build.yml](https://github.com/srnyx/limited-lives/actions/workflows/build.yml)

# Wiki

For all information about the plugin (commands, permissions, etc...) please see the wiki at [github.com/srnyx/limited-lives/wiki](https://github.com/srnyx/limited-lives/wiki)
