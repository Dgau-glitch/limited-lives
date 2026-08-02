# Folia shutdown and scheduler audit

Date: 2026-08-01. Target: Folia 1.21.11 build 14.

## Application-owned work

| Source | Owner | Lifetime | Shutdown/retired behavior |
|---|---|---|---|
| `FoliaExecutionService.runForEntity*` | target entity | one shot | tracked `ScheduledTask` is cancelled by `stop`; retired callback contains no entity access |
| `runForRegion` | location region | one shot | tracked and cancelled by `stop` |
| `runGlobal*` | global region | one shot | tracked and cancelled by `stop` |
| `runAsync*` | Folia async scheduler | one shot; delayed API currently has no caller | tracked and cancelled by `stop` |

`LifecycleGate` rejects submissions unless both the plugin and lifecycle are `RUNNING`. Every rejection is counted by `getRejectedSubmissions()`. `stop()` changes the gate to `STOPPING` before cancelling tracked handles and does not call a scheduler getter or submit work.

## Shaded AnnoyingAPI 5.2.1

The built JAR was unpacked and scanned for scheduler, executor, future, timer and shutdown-hook references. Relevant classes:

- `AnnoyingScheduler`: Folia-aware abstraction used internally by AnnoyingAPI. LimitedLives business code never calls it.
- `DataManager.toggleIntervalCacheSaving`: may create an async interval cache task. LimitedLives explicitly cancels/nulls this library task during enable and every reload; persistence is coordinated by `LifeStore` and the final synchronous disable flush instead.
- `LifeStore`: the AnnoyingAPI database is read once on Folia's async scheduler during enable and copied into its concurrent dialect cache. Tick-thread reads and mutations only access that cache; no `StringData` call can fall through to JDBC. Dirty values are synchronously flushed by AnnoyingAPI before its SQL connection is closed. Placeholder callbacks read independent immutable snapshots and therefore remain safe even when an external plugin invokes PAPI during server shutdown.
- `LifeJournal`: every set/remove is mirrored to an atomic write-ahead snapshot by a dedicated non-Bukkit executor. Writes use a temporary file, `FileChannel.force(true)` and atomic replace; the snapshot is replayed after the database preload, so a JVM/server crash or failed final database flush cannot restore an older value. Rapid mutations are debounced by `persistence.journal-flush-delay-ms` (50 ms by default), and `close()` drains the executor without scheduling Folia work.
- `/lifereload` reloads messages asynchronously and applies configuration on the global region without calling `AnnoyingPlugin.reloadPlugin()`. Storage configuration and `DataManager` are intentionally restart-only: replacing them during a hot reload would discard the live cache and could restore stale life values.
- `AnnoyingDownload`: contains a legacy `Bukkit.getScheduler().callSyncMethod` fallback and async dependency-download path. LimitedLives declares no automatically downloaded plugin dependencies, so this path is unreachable in its configured lifecycle. It must not be reused for future dependencies.
- `MiscUtility.CPU_SCHEDULER` and `IO_SCHEDULER`: static Java scheduled executors. They are synchronously stopped with `shutdownNow()` from `disable()` and do not schedule Bukkit work.
- AnnoyingAPI's optional bStats bridge is permanently disabled through its options before enable. The bridge class, bStats packages and `bstats.yml` are excluded from the runtime JAR, and no metrics dependency is declared or downloaded by the smoke harness.

No shutdown hook (`Runtime.addShutdownHook`) was found in the application or shaded AnnoyingAPI classes. `AnnoyingPlugin.onDisable()` is final: it synchronously saves its cache and closes SQL before invoking LimitedLives `disable()`. Because JavaPlugin is already disabled, `LifecycleGate` rejects submissions for the whole callback; LimitedLives then closes its store, cancels tracked task handles and stops the two shaded Java executors without submitting any task.

## Reproducible checks

```bash
./gradlew clean test shadowJar
rg -n "BukkitScheduler|BukkitRunnable|Bukkit.getScheduler|plugin.scheduler|FoliaLib|FoliaScheduler" src build.gradle.kts
rg -n "getRegionScheduler|getGlobalRegionScheduler|getAsyncScheduler|getScheduler\\(" src/main/java
./scripts/folia-smoke.sh
```

The smoke harness verifies the Folia and Mojang server downloads by checksum, preloads pinned runtime libraries for network-isolated CI, starts Folia twice against the same plugin/data directory, forces four region scheduler threads for the second run, executes reload, performs a clean stop, and rejects ownership, retired-entity, schedule-after-disable and LimitedLives error signatures in both logs.
