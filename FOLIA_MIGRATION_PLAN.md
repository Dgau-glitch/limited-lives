# План полного перехода LimitedLives на Folia 1.21.11

## Цель и границы миграции

Цель — собирать и запускать плагин непосредственно против Folia API 1.21.11, удалить зависимость бизнес-кода от универсального Bukkit scheduler и гарантировать, что все обращения к миру, игрокам и сущностям выполняются в контексте владеющего ими региона. Миграция не должна менять правила подсчёта, передачи, потери, получения и восстановления жизней.

Целевая compile-only зависимость:

```kotlin
compileOnly("dev.folia:folia-api:1.21.11-R0.1-SNAPSHOT")
```

Каждый нумерованный этап ниже — **отдельная задача, рассчитанная на одно следующее сообщение пользователя**. Этапы выполняются по порядку. В рамках одного этапа необходимо внести изменения, запустить указанные проверки и зафиксировать результат; нельзя незаметно переносить невыполненную часть в следующий этап.

## Результат первичного аудита

### Сборка и декларация платформы

- Сейчас проект компилируется против Spigot API 1.8.8 через `spigotAPI(...)`, хотя в `plugin.yml` уже без доказательства заявлено `folia-supported: true`.
- Folia API напрямую отсутствует, поэтому компилятор не проверяет использование нативных `RegionScheduler`, `EntityScheduler`, `GlobalRegionScheduler` и `AsyncScheduler`.
- Плагин наследуется от `AnnoyingPlugin` 5.2.1 и использует его регистрацию, сообщения, хранилище и scheduler. Совместимость этой зависимости с Folia 1.21.11 является частью миграции, а не предположением.

### Планировщики и выключение

- Единственный явный запуск задачи в собственном исходном коде находится в `PlayerManager.dispatchCommands(...)`: `plugin.scheduler.runSync(...)` оборачивает отправку консольных команд.
- Собственного `onDisable()`, shutdown hook или executor в проекте нет. Следовательно, по имеющемуся коду нельзя утверждать, что задача создаётся непосредственно из `onDisable()`; приведённый в запросе стек-трейс также не приложен.
- Тем не менее `runSync(...)` создаёт отложенную работу, способную попасть в гонку с выключением. Этот scheduler нужно удалить, а команды маршрутизировать по Folia-владению без попыток планирования после начала disable.
- Требуется также проверить транзитивный lifecycle `AnnoyingPlugin`/AnnoyingAPI: его `disable`, хранилище, динамическую регистрацию listeners, сообщения и scheduler. Заявление `folia-supported: true` допустимо оставить только после такой проверки.

### Потокобезопасность игрового состояния

- Обработчики событий игрока обычно уже приходят на поток региона этого игрока, но команды могут запускаться из консоли, command block или региона другого игрока.
- `LivesCmd` изменяет нескольких `OfflinePlayer`/`Player`, выдаёт предметы, отправляет сообщения и выполняет передачу жизней. Один региональный поток не имеет права напрямую изменять сущность в другом регионе; операция `give` дополнительно требует сохранения существующей бизнес-семантики списания и начисления.
- В обработке смерти есть обращения и к жертве, и к убийце. Убийца потенциально уже принадлежит другому региону, поэтому начисление жизни и сообщение ему должны быть перенесены на его `EntityScheduler`.
- `PlayerManager.withdrawLives(...)` изменяет инвентарь и потому обязан выполняться на scheduler конкретного игрока. Получение `Player` из `OfflinePlayer`, permission, имени, мира и отправка сообщений также должны быть классифицированы как snapshot/data-only либо entity-bound операции.
- `LimitedConfig.KeepInventory` обходит все миры и меняет gamerule во время enable/reload. Это работа с состоянием мира; её нельзя считать безопасной на произвольном региональном/командном потоке.
- `PlaceholderManager` может вызываться сторонним плагином с произвольного потока. Чтение entity-bound состояния игрока внутри placeholder callback нельзя автоматически считать безопасным.
- `WorldGuardManager.test(...)` читает локацию игрока и делает region query. Вызов должен происходить в контексте игрока; совместимость используемой версии WorldGuard с Folia должна быть подтверждена отдельно.
- `StringData`/`EntityData` используются из событий, команд и placeholder callback. Необходимо подтвердить потокобезопасность backend-а, исключить конкурентные read-modify-write для одной UUID и определить явную модель сериализации данных.

### Нативные категории Folia

- **EntityScheduler** — любые действия, следующие за игроком/сущностью: инвентарь, permission, сообщения, получение актуального мира/локации и команды, изменяющие конкретного онлайн-игрока.
- **RegionScheduler** — работа с фиксированной локацией/чанком, если она появится или действительно нужна после классификации.
- **GlobalRegionScheduler** — глобальные серверные операции, не принадлежащие конкретной сущности/региону: например, безопасная точка оркестрации консольной команды или глобального реестра рецептов, если контракт API требует её.
- **AsyncScheduler** — только I/O и вычисления без обращения к Bukkit world/entity API.
- Наличие `isOwnedByCurrentRegion(...)` можно использовать для проверки инвариантов и выбора немедленного исполнения, но не как способ обойти scheduler contract.

## Этапы реализации

> Статус: этапы 1–9 реализованы. Сборочный снимок и аудит AnnoyingAPI сохранены в `docs/FOLIA_BASELINE.md`; миграция завершена; дальнейшие изменения проходят через regression matrix.

### 1. Перевести build на Folia API 1.21.11 и зафиксировать baseline

Заменить Spigot 1.8.8 API на указанную `compileOnly("dev.folia:folia-api:1.21.11-R0.1-SNAPSHOT")`, подключив официальный PaperMC Maven repository через существующий Gradle DSL либо обычный `repositories`. Установить совместимую Java toolchain (для Minecraft/Folia 1.21.x — Java 21), не затенять Folia API в итоговый JAR. Проверить разрешение `AnnoyingAPI`, PlaceholderAPI и WorldGuard и сохранить отчёт `dependencies` как baseline. До изменения Java-кода выполнить чистую компиляцию, чтобы получить полный перечень несовместимых/устаревших вызовов.

**Готово, когда:** `./gradlew clean build` использует Java 21 и Folia API 1.21.11; в runtime JAR нет классов Folia/Paper API; все ошибки baseline либо исправлены в рамках механического API-перехода, либо явно перечислены для следующих этапов.

### 2. Ввести единый Folia execution/lifecycle слой

Создать небольшой сервис с чёткой ответственностью за `EntityScheduler`, `RegionScheduler`, `GlobalRegionScheduler` и `AsyncScheduler`; бизнес-классы не должны самостоятельно выбирать scheduler. Сервис должен принимать plugin instance, проверять lifecycle до постановки работы, поддерживать отмену отслеживаемых задач и различать `RUNNING`/`STOPPING`/`STOPPED`. Для entity-задач обязательно обработать retired callback без обращения к retired entity. Не добавлять fallback на `BukkitScheduler`, FoliaLib/FoliaScheduler и не скрывать ошибки `try/catch`.

В lifecycle плагина установить `STOPPING` **в самом начале синхронного disable callback**, затем отменить ранее созданные задачи и выполнить обязательный flush данных напрямую, без создания новых scheduler-задач. Если AnnoyingAPI не позволяет гарантировать этот порядок, заменить только его lifecycle/scheduler часть локальным нативным решением, не дублируя бизнес-логику.

**Готово, когда:** в проекте существует одна точка планирования; поиск не находит `BukkitScheduler`, `BukkitRunnable`, `plugin.scheduler`, FoliaLib/FoliaScheduler и прямые scheduler-вызовы вне execution-сервиса; disable не ставит ни одной новой задачи.

### 3. Отделить хранилище жизней от Bukkit-сущностей и обеспечить сериализацию по UUID

Разделить `PlayerManager` на data/service часть (UUID, lives, dead marker, grace timestamps) и entity-facing действия. Убрать необходимость хранить `OfflinePlayer` для чистых операций над данными. Проверить реализацию `StringData`/`EntityData` в AnnoyingAPI 5.2.1: backend, connection lifecycle, thread-safety, shutdown flush и наличие внутренних scheduler-вызовов. Для read-modify-write (`addLives`, `removeLives`, `give`) ввести один механизм последовательного выполнения/блокировки на UUID без доступа к Bukkit API из async потока.

Сохранение во время disable должно иметь отдельный синхронный `flush/close` путь, вызываемый после запрета новых операций и до завершения disable. Никаких задач, futures, callbacks или shutdown hooks из этого пути не создавать.

**Готово, когда:** данные одного UUID не теряют обновления при параллельных командах/событиях; чистые data-методы не обращаются к `Player`, `OfflinePlayer`, миру или scheduler; тесты покрывают границы min/max, revive/kill/grace и конкурентное обновление.

### 4. Перевести команды и tab-completion на модель владельца сущности

Оставить parsing, permission checks и формирование плана операции в command-слое, а действия с каждым онлайн-игроком направлять через execution-сервис на его `EntityScheduler`. Консольные/offline data-only операции выполнять через data service без получения живой сущности. Выдачу предметов и любые player messages выполнять только на scheduler адресата.

Операцию `give` оформить как отдельный сервис передачи: валидировать всех участников, сериализовать изменения UUID в стабильном порядке, сохранить нынешние clamp/min/max правила и затем отдельно доставить сообщения онлайн-участникам на их entity schedulers. Не удерживать блокировки во время scheduler callbacks и не обращаться с потока отправителя к чужому `Player`.

Проверить tab-completion: permission проверяется до построения каждого списка; недоступные действия/аргументы не показываются; получение имён онлайн-игроков не нарушает Folia threading contract. При необходимости использовать поддерживаемый API snapshot, а не обход живых сущностей.

**Готово, когда:** все ветки `/lives` и `/lifereload` безопасны из player, console и command block contexts; multi-player selectors не вызывают cross-region access; tab-completion быстрый, контекстный и не раскрывает команды без permission.

### 5. Перевести события смерти, респавна, входа и урона

Сохранить действия над жертвой внутри её event/region context. Действия над убийцей (проверки entity-bound данных, начисление украденной жизни, сообщение) направить на `killer.getScheduler()` через execution-сервис; перед передачей между контекстами сохранять только UUID/имя/неизменяемые значения, а не использовать чужую entity напрямую. На respawn выполнять наказание в корректном контексте возрождённого игрока.

Удалить scheduler из `PlayerManager.dispatchCommands(...)`. Разделить подготовку placeholder-ов команды и её исполнение. Если команда является глобальной консольной операцией, исполнить её через `GlobalRegionScheduler` во время RUNNING; если она требует entity context, спланировать относительно соответствующего игрока согласно контракту Folia. Не планировать команды при STOPPING и не переносить их в disable.

**Готово, когда:** смерть в PvP между разными регионами не вызывает `AsyncCatcher`/ownership exception; punishment/revive/respawn commands выполняются один раз; выключение сразу после события не создаёт новую задачу после disable.

### 6. Перевести crafting, consume, interact и работу с инвентарём

Проверить каждый listener на фактический event context Folia и оставить event mutation (`setCancelled`, result, keep-inventory) только там, где это разрешено контрактом события. Вынести общую логику использования life item из consume/interact listeners в один сервис, сохранив trigger, cooldown, amount, max-life и сообщения. `withdrawLives` разделить на изменение данных и выдачу предмета; выдачу выполнять исключительно на `EntityScheduler` получателя, предусмотрев retired callback без компенсации, меняющей исходную бизнес-логику.

Проверить регистрацию/перерегистрацию listeners при reload: она должна быть синхронной lifecycle-операцией и не опираться на scheduler AnnoyingAPI. Проверить recipe registration на нативном Folia/Paper API 1.21.11.

**Готово, когда:** item crafting/use/withdraw работают без cross-region доступа и дублирования общей логики; reload не оставляет двойных listeners/recipes; stop во время ожидающей entity-задачи корректно отклоняет её без новой задачи.

### 7. Сделать reload, gamerules, WorldGuard и PlaceholderAPI безопасными

Разделить reload на: (1) чтение/валидацию файлов без Bukkit state; (2) применение immutable config snapshot; (3) игровые эффекты в правильных Folia contexts. Изменение gamerule каждого мира выполнять по документированному Folia 1.21.11 пути владельца/глобального региона; не обходить миры и не менять их с произвольного command thread. Устранить состояние, при котором частично применённый reload виден разным регионам.

Для WorldGuard подтвердить официально совместимую версию и thread contract query; передавать в adapter локацию, снятую в entity context. Для PlaceholderAPI считать callback потенциально внешним: не читать `Player`/permissions/location с неизвестного потока. Отдавать data-only значения из thread-safe snapshot/cache либо документированно переключать контекст без блокирующего ожидания region thread. Не блокировать один регион в ожидании другого.

**Готово, когда:** параллельные placeholder requests и reload не дают гонок; gamerules применяются ко всем мирам корректно; WorldGuard query выполняется в допустимом контексте; отсутствуют синхронные ожидания scheduler futures.

### 8. Провести полный shutdown-аудит собственного и библиотечного кода

Проверить все совпадения `BukkitScheduler`, `BukkitRunnable`, `EntityScheduler`, `RegionScheduler`, `GlobalRegionScheduler`, `AsyncScheduler`, FoliaLib, FoliaScheduler, executors, futures, timers и shutdown hooks как в проекте, так и в реально упакованных классах AnnoyingAPI. Для каждой периодической/отложенной задачи документировать владельца, момент отмены и поведение retired callback.

Смоделировать три гонки: (1) disable сразу после команды; (2) disable сразу после death/respawn; (3) disable во время reload/data write. После перехода в STOPPING новые задачи должны отклоняться; уже запущенная data-операция должна быть завершена/сброшена синхронно до закрытия backend-а; код disable не должен вызывать ни один scheduler API. Не применять `try/catch` как подавление Folia exception.

**Готово, когда:** статический поиск и runtime instrumentation показывают ноль schedule attempts после начала disable; повторные enable/disable (если test harness это поддерживает) не оставляют задачи/потоки/соединения; данные после рестарта консистентны.

### 9. Интеграционные тесты на настоящем Folia 1.21.11 и финализация метаданных

Добавить unit-тесты сервисов и автоматизируемый smoke/integration сценарий на Folia 1.21.11. Проверить как минимум: join/grace, PvE death, cross-region PvP death/steal, respawn commands, get/set/add/remove/withdraw/give с online/offline и selectors, crafting/consume/interact, keep-inventory, reload, PlaceholderAPI, WorldGuard и остановку во время активных операций. Запускать сервер с несколькими region threads и разводить игроков по независимым регионам.

Проверить логи на ownership/thread violations, rejected tasks, обращения к retired entity и schedule-after-disable. Только после успешного прогона подтвердить `folia-supported: true`, обновить README с минимальными версиями Folia/Java и известными требованиями optional integrations. Не заявлять поддержку обычного Paper, если итоговый код и зависимости намеренно Folia-only.

**Готово, когда:** clean build и тесты проходят; реальный Folia smoke test не содержит thread/lifecycle ошибок; итоговый JAR содержит корректный `plugin.yml`, не включает server API и запускается с чистым data folder и после обновления существующего data folder.

## Обязательная проверка после каждого этапа

Минимальный набор команд уточняется по мере появления тестов, но каждый этап должен завершаться следующими проверками:

```bash
./gradlew clean build
./gradlew test
rg -n "BukkitScheduler|BukkitRunnable|Bukkit\.getScheduler|getRegionScheduler|getGlobalRegionScheduler|getAsyncScheduler|getScheduler\(\)|plugin\.scheduler|FoliaLib|FoliaScheduler|onDisable|disable\(|shutdownHook|addShutdownHook" src build.gradle.kts
git diff --check
```

Для этапов 5–9 дополнительно обязателен запуск на настоящем Folia 1.21.11; mock-only тест не доказывает region-thread совместимость.

## Документация, являющаяся источником истины

- Folia 1.21.11 Javadocs: <https://jd.papermc.io/folia/1.21.11/>
- `Server` (доступ к нативным scheduler API): <https://jd.papermc.io/folia/1.21.11/org/bukkit/Server.html>
- `EntityScheduler`: <https://jd.papermc.io/folia/1.21.11/io/papermc/paper/threadedregions/scheduler/EntityScheduler.html>
- `RegionScheduler`: <https://jd.papermc.io/folia/1.21.11/io/papermc/paper/threadedregions/scheduler/RegionScheduler.html>
- `GlobalRegionScheduler`: <https://jd.papermc.io/folia/1.21.11/io/papermc/paper/threadedregions/scheduler/GlobalRegionScheduler.html>
- `AsyncScheduler`: <https://jd.papermc.io/folia/1.21.11/io/papermc/paper/threadedregions/scheduler/AsyncScheduler.html>
- Paper development documentation: <https://docs.papermc.io/paper/dev/>
- Bukkit 1.21.11 API: <https://hub.spigotmc.org/javadocs/bukkit/>
- PlaceholderAPI developer documentation: <https://wiki.placeholderapi.com/developers/using-placeholderapi/>
- WorldGuard API: <https://worldguard.enginehub.org/en/latest/developer/>

Если документация и текущее поведение зависимости расходятся, реализацию нельзя строить на догадке: сначала нужен воспроизводимый тест на Folia 1.21.11 или проверка исходного кода конкретной версии зависимости.
