# Folia 1.21.11 baseline

Дата фиксации: 2026-08-01.

## Сборка

- Compile API: `dev.folia:folia-api:1.21.11-R0.1-SNAPSHOT` из официального PaperMC Maven repository.
- Java toolchain и bytecode: Java 21 (class major version 65).
- Wrapper зафиксирован на Gradle 8.14.3 с официальной SHA-256 проверкой дистрибутива. Эта стабильная версия совместима с Java 21 и IDE Gradle integrations и не зависит от повреждённого/несовместимого Gradle 9 worker bootstrap.
- Старые транзитивные `org.bukkit:bukkit` от AnnoyingAPI и WorldGuard исключены: Bukkit capability предоставляет Folia API.
- Folia API подключён как `compileOnly` и не должен попадать в shadow JAR.
- Полный снимок `compileClasspath` сохранён в [`baseline/compile-classpath.txt`](baseline/compile-classpath.txt). В нём успешно разрешены Folia API 1.21.11, AnnoyingAPI 5.2.1, PlaceholderAPI 2.12.2 и WorldGuard 7.0.15.

Исходный проект успешно компилировался против Spigot 1.8.8, но не проверял нативные Folia API. После первой замены зависимости Gradle обнаружил конфликт capability с транзитивными Bukkit 1.13/1.13.2; конфликт устранён исключением старого Bukkit, а не возвратом к Spigot API.

## Аудит AnnoyingAPI 5.2.1

Проверка bytecode фактически используемого `annoying-api-5.2.1.jar` показала:

- `AnnoyingPlugin.onDisable()` объявлен `final`;
- он синхронно вызывает `Dialect.saveCache()`, затем закрывает SQL connection и только после этого вызывает расширяемый `disable()`;
- `StringData` при выключенном cache синхронно вызывает dialect database methods; LimitedLives устанавливает `useCacheDefault(false)`;
- при явно включённом interval cache `DataManager.toggleIntervalCacheSaving()` использует внутренний Folia-aware scheduler AnnoyingAPI;
- собственный бизнес-код больше не использует `plugin.scheduler`; все новые scheduler submissions сосредоточены в `FoliaExecutionService`.

Из-за `final onDisable()` точка расширения `disable()` вызывается после встроенного синхронного flush/close. Безопасность обеспечивается двумя инвариантами: JavaPlugin уже имеет `isEnabled() == false`, поэтому execution service отвергает новые задачи с самого начала server disable, а `disable()` только отменяет задачи и ничего не планирует. Собственные записи LimitedLives синхронны и по умолчанию не требуют отложенного flush; встроенный cache AnnoyingAPI, если администратор включил его отдельно, сохраняется самим final `onDisable()`.

## Зафиксированные предупреждения

Чистая компиляция не выявила несовместимых или устаревших вызовов Folia API. Для всех `JavaCompile` задач включён `-Xlint:deprecation`, поэтому будущая регрессия покажет точный файл и вызов вместо общего сообщения компилятора.

## Integrations

- WorldGuard был обновлён до 7.0.15 — последней проверенной стабильной версии с Java 21 bytecode. Запрос получает уже снятую в entity-owned event context `Location`; callback WorldGuard не планируется и не переносит живую entity между регионами.
- PlaceholderAPI callback не ждёт scheduler future и не читает permission/location с неизвестного потока. UUID разрешается через identity/name directory, значения permission/max берутся из concurrent snapshot, а lives/grace — из UUID-only data service.
- Полный lifecycle и shaded-library аудит находится в [`FOLIA_SHUTDOWN_AUDIT.md`](FOLIA_SHUTDOWN_AUDIT.md).

## Команды воспроизведения

```bash
bash ./gradlew clean build
bash ./gradlew dependencies --configuration compileClasspath --console=plain
javap -verbose build/classes/java/main/xyz/srnyx/limitedlives/LimitedLives.class
jar tf build/libs/LimitedLives-*.jar
```
