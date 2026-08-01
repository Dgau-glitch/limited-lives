# Folia 1.21.11 baseline

Дата фиксации: 2026-08-01.

## Сборка

- Compile API: `dev.folia:folia-api:1.21.11-R0.1-SNAPSHOT` из официального PaperMC Maven repository.
- Java toolchain и bytecode: Java 21 (class major version 65).
- Старые транзитивные `org.bukkit:bukkit` от AnnoyingAPI и WorldGuard исключены: Bukkit capability предоставляет Folia API.
- Folia API подключён как `compileOnly` и не должен попадать в shadow JAR.
- Полный снимок `compileClasspath` сохранён в [`baseline/compile-classpath.txt`](baseline/compile-classpath.txt). В нём успешно разрешены Folia API 1.21.11, AnnoyingAPI 5.2.1, PlaceholderAPI 2.12.2 и WorldGuard 7.0.0.

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

Чистая компиляция не выявила несовместимых вызовов или ошибок Folia API. Компилятор сообщает, что `LimitedLives.java` использует deprecated API из совместимого слоя AnnoyingAPI/Bukkit. Это не scheduler/shutdown ошибка и должно быть устранено при последующей модернизации регистрации рецептов/metadata, не меняя бизнес-логику текущего этапа.

## Команды воспроизведения

```bash
bash ./gradlew clean build
bash ./gradlew dependencies --configuration compileClasspath --console=plain
javap -verbose build/classes/java/main/xyz/srnyx/limitedlives/LimitedLives.class
jar tf build/libs/LimitedLives-*.jar
```
