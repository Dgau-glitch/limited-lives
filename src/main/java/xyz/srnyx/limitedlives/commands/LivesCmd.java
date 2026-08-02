package xyz.srnyx.limitedlives.commands;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import xyz.srnyx.annoyingapi.AnnoyingPlugin;
import xyz.srnyx.annoyingapi.command.AnnoyingCommand;
import xyz.srnyx.annoyingapi.command.AnnoyingSender;
import xyz.srnyx.annoyingapi.libs.javautilities.FileUtility;
import xyz.srnyx.annoyingapi.libs.javautilities.manipulation.Mapper;
import xyz.srnyx.annoyingapi.message.AnnoyingMessage;
import xyz.srnyx.annoyingapi.utility.BukkitUtility;

import xyz.srnyx.limitedlives.LimitedLives;
import xyz.srnyx.limitedlives.config.Feature;
import xyz.srnyx.limitedlives.managers.player.PlayerManager;
import xyz.srnyx.limitedlives.managers.player.exception.*;
import xyz.srnyx.limitedlives.services.player.LifeTransferService;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;
import java.util.logging.Level;


public class LivesCmd extends AnnoyingCommand {
    @NotNull private static final Gson GSON = new Gson();

    @NotNull private final LimitedLives plugin;

    public LivesCmd(@NotNull LimitedLives plugin) {
        this.plugin = plugin;
    }

    @Override @NotNull
    public LimitedLives getAnnoyingPlugin() {
        return plugin;
    }

    @Override
    public void onCommand(@NotNull AnnoyingSender sender) {
        if (!plugin.commands.isOwnedContext(sender)) {
            plugin.commands.schedule(sender, () -> onCommand(sender));
            return;
        }
        // Check if commands enabled
        if (sender.isPlayer) {
            final World world = sender.getPlayer().getWorld();
            if (!plugin.config.worldsBlacklist.isWorldEnabled(world, Feature.COMMANDS)) {
                new AnnoyingMessage(plugin, "feature-disabled")
                        .replace("%feature%", Feature.COMMANDS)
                        .replace("%world%", world.getName())
                        .send(sender);
                return;
            }
        }
        final int length = sender.args.length;

        // No arguments, get
        if (length == 0 || (length == 1 && sender.argEquals(0, "get"))) {
            if (sender.checkPlayer() && sender.checkPermission("limitedlives.get.self")) new AnnoyingMessage(plugin, "get.self")
                    .replace("%lives%", new PlayerManager(plugin, sender.getPlayer()).getLives())
                    .send(sender);
            return;
        }

        // Check args length
        if (length < 2) {
            sender.invalidArguments();
            return;
        }

        // convert hardcorelivesplugin
        if (sender.argEquals(0, "convert")) {
            if (!sender.checkPermission("limitedlives.convert")) return;
            if (!sender.argEquals(1, "hardcorelivesplugin")) {
                sender.invalidArgumentByIndex(1);
                return;
            }

            // File: plugins/Hardcorelivesplugin/players/UUID.json
            // Structure: {"uuid":"e907083e-5db6-41fc-9e32-5c4d99a08712","username":"srnyx","lives":3,"bypassLives":false,"maxLives":5}
            // Converting: "uuid" and "lives"
            plugin.execution.runAsync(() -> {
                int succeeded = 0;
                int failed = 0;
                final File playersFolder = new File(plugin.getDataFolder().getParentFile(), "Hardcorelivesplugin/players");
                for (final String uuidString : FileUtility.getFileNames(playersFolder, "json")) {
                    // Parse file as JSON
                    final JsonObject json;
                    try {
                        json = GSON.fromJson(new FileReader(new File(playersFolder, uuidString + ".json")), JsonObject.class);
                    } catch (final FileNotFoundException e) {
                        AnnoyingPlugin.log(Level.WARNING, "Failed to convert Hardcore Lives Plugin data for " + uuidString + ", file not found", e);
                        failed++;
                        continue;
                    }

                    // Get lives
                    final JsonElement livesElement = json.get("lives");
                    if (livesElement == null) {
                        AnnoyingPlugin.log(Level.WARNING, "Failed to convert Hardcore Lives Plugin data for " + uuidString + ", lives not found");
                        failed++;
                        continue;
                    }
                    final int lives;
                    try {
                        lives = livesElement.getAsInt();
                    } catch (final ClassCastException e) {
                        AnnoyingPlugin.log(Level.WARNING, "Failed to convert Hardcore Lives Plugin data for " + uuidString + ", lives not an integer", e);
                        failed++;
                        continue;
                    }

                    // Save lives to Limited Lives
                    final UUID uuid;
                    try {
                        uuid = UUID.fromString(uuidString);
                    } catch (final IllegalArgumentException exception) {
                        AnnoyingPlugin.log(Level.WARNING, "Failed to convert Hardcore Lives Plugin data for " + uuidString + ", invalid UUID");
                        failed++;
                        continue;
                    }
                    if (!plugin.lifeStore.set(uuid, PlayerManager.LIVES_KEY, lives)) {
                        AnnoyingPlugin.log(Level.WARNING, "Failed to convert Hardcore Lives Plugin data for " + uuidString + ", failed to save");
                        failed++;
                        continue;
                    }

                    AnnoyingPlugin.log(Level.INFO, "Converted Hardcore Lives Plugin data for " + uuidString + " with " + lives + " lives");
                    succeeded++;
                }

                final int succeededResult = succeeded;
                final int failedResult = failed;
                plugin.feedback.deliver(sender, () -> new AnnoyingMessage(plugin, "convert")
                        .replace("%source%", "HardcoreLivesPlugin")
                        .replace("%succeeded%", succeededResult)
                        .replace("%failed%", failedResult)
                        .send(sender));
            });
            return;
        }

        // get <player>
        if (sender.argEquals(0, "get")) {
            if (!sender.checkPermission("limitedlives.get.other")) return;
            final List<OfflinePlayer> players = sender.getSelector(1, OfflinePlayer.class)
                    .orElseFlatSingle(BukkitUtility::getOfflinePlayer);
            if (players != null) for (final OfflinePlayer player : players) executeForTarget(player, () -> {
                final String targetName = player.getName();
                final int targetLives = new PlayerManager(plugin, player).getLives();
                plugin.feedback.deliver(sender, () -> new AnnoyingMessage(plugin, "get.other")
                        .replace("%target%", targetName)
                        .replace("%lives%", targetLives)
                        .send(sender));
            });
            return;
        }

        // Get lives
        Integer lives = sender.getArgumentOptionalFlat(1, Mapper::toInt).orElse(null);
        if (lives == null) return;

        if (length == 2) {
            if (!sender.checkPlayer()) return;
            final String action = sender.getArgument(0, String::toLowerCase);
            if (action == null || !sender.checkPermission("limitedlives." + action + ".self")) return;
            final Player player = sender.getPlayer();
            final String playerName = player.getName();

            // Get new lives after action
            final int newLives;
            final PlayerManager manager = new PlayerManager(plugin, player);
            try {
                switch (action) {
                    // set <lives>
                    case "set":
                        newLives = manager.setLives(lives);
                        break;
                    // add <lives>
                    case "add":
                        newLives = manager.addLives(lives);
                        break;
                    // remove <lives>
                    case "remove":
                        newLives = manager.removeLives(lives, null);
                        break;
                    // withdraw <lives>
                    case "withdraw":
                        if (lives <= 0) {
                            new AnnoyingMessage(plugin, "withdraw.negative").send(sender);
                            return;
                        }
                        final int currentLives = manager.getLives();
                        if (currentLives <= lives) lives = currentLives - 1; // Withdraw as many possible
                        if (lives <= plugin.config.lives.min) throw new LessThanMinLives();
                        newLives = manager.withdrawLives(player, lives);
                        break;
                    default:
                        sender.invalidArgumentByIndex(0);
                        return;
                }
            } catch (final ActionException e) {
                new AnnoyingMessage(plugin, action + "." + e.getMessageKey())
                        .replace("%amount%", lives)
                        .replace("%target%", playerName)
                        .replace("%min%", plugin.config.lives.min)
                        .replace("%max%", manager.getMaxLives())
                        .send(sender);
                return;
            }

            // Send message
            new AnnoyingMessage(plugin, action + ".self")
                    .replace("%amount%", lives)
                    .replace("%lives%", newLives)
                    .send(sender);
            return;
        }

        if (length != 3) {
            sender.invalidArguments();
            return;
        }

        // give <lives> <player>
        if (sender.argEquals(0, "give")) {
            // Check if player and has permission
            if (!sender.checkPlayer() || !sender.checkPermission("limitedlives.give")) return;
            // Inputted negative number
            if (lives <= 0) {
                new AnnoyingMessage(plugin, "give.negative").send(sender);
                return;
            }

            // Get target and player
            final List<OfflinePlayer> selectorTargets = sender.getSelector(2, OfflinePlayer.class)
                    .orElseFlatSingle(BukkitUtility::getOfflinePlayer);
            if (selectorTargets == null) return;
            final Player player = sender.getPlayer();
            final String playerName = player.getName();
            plugin.lifeTransferService.transfer(player, selectorTargets, lives, outcome -> handleTransferOutcome(sender, playerName, outcome));
            return;
        }

        // Get action
        final String action = sender.getArgument(0, String::toLowerCase);
        if (action == null || !sender.checkPermission("limitedlives." + action + ".other")) return;

        // Get targets and loop through
        final List<OfflinePlayer> targets = sender.getSelector(2, OfflinePlayer.class)
                .orElseFlatSingle(BukkitUtility::getOfflinePlayer);
        final int requestedAmount = lives;
        if (targets != null) for (final OfflinePlayer target : targets) executeForTarget(target, () -> executeOtherAction(sender, action, requestedAmount, target));
    }

    private void handleTransferOutcome(@NotNull AnnoyingSender sender, @NotNull String playerName, @NotNull LifeTransferService.Outcome outcome) {
        if (sender.isPlayer) plugin.placeholders.capture(sender.getPlayer());
        switch (outcome.status()) {
            case SELF_ONLY:
                new AnnoyingMessage(plugin, "give.self").send(sender);
                return;
            case LAST_LIFE:
                new AnnoyingMessage(plugin, "give.last-life").send(sender);
                return;
            case FAILED:
                sender.invalidArguments();
                return;
            case SOURCE_RETIRED:
                return;
            case SUCCESS:
                break;
        }
        for (final LifeTransferService.Transfer transfer : outcome.transfers()) {
            new AnnoyingMessage(plugin, "give.player")
                    .replace("%player%", playerName)
                    .replace("%target%", transfer.targetName())
                    .replace("%playerlives%", transfer.sourceLives())
                    .replace("%targetlives%", transfer.targetLives())
                    .replace("%amount%", transfer.amount())
                    .send(sender);
            final Player target = Bukkit.getPlayer(transfer.targetUuid());
            if (target != null) plugin.feedback.deliver(target, () -> {
                plugin.placeholders.capture(target);
                new AnnoyingMessage(plugin, "give.target")
                        .replace("%player%", playerName)
                        .replace("%target%", transfer.targetName())
                        .replace("%playerlives%", transfer.sourceLives())
                        .replace("%targetlives%", transfer.targetLives())
                        .replace("%amount%", transfer.amount())
                        .send(target);
            });
        }
    }

    private void executeOtherAction(@NotNull AnnoyingSender sender, @NotNull String action, int requestedAmount, @NotNull OfflinePlayer target) {
        final String targetName = target.getName();
        final PlayerManager manager = new PlayerManager(plugin, target);
        int amount = requestedAmount;
        final int newLives;
        org.bukkit.inventory.ItemStack withdrawItem = null;
        try {
            switch (action) {
                case "set": newLives = manager.setLives(amount); break;
                case "add": newLives = manager.addLives(amount); break;
                case "remove": newLives = manager.removeLives(amount, null); break;
                case "withdraw":
                    if (!sender.isPlayer) return;
                    if (amount <= 0) {
                        plugin.feedback.deliver(sender, () -> new AnnoyingMessage(plugin, "withdraw.negative").send(sender));
                        return;
                    }
                    final int currentLives = manager.getLives();
                    if (currentLives <= amount) amount = currentLives - 1;
                    if (amount <= plugin.config.lives.min) throw new LessThanMinLives();
                    withdrawItem = manager.createWithdrawItem(amount);
                    newLives = manager.withdrawLivesData(amount);
                    break;
                default: return;
            }
        } catch (final ActionException exception) {
            final int failedAmount = amount;
            final int maxLives = manager.getMaxLives();
            plugin.feedback.deliver(sender, () -> new AnnoyingMessage(plugin, action + "." + exception.getMessageKey())
                    .replace("%amount%", failedAmount)
                    .replace("%target%", targetName)
                    .replace("%min%", plugin.config.lives.min)
                    .replace("%max%", maxLives)
                    .send(sender));
            return;
        }

        final int appliedAmount = amount;
        final org.bukkit.inventory.ItemStack item = withdrawItem;
        plugin.feedback.deliver(sender, () -> {
            if (item != null) sender.getPlayer().getInventory().addItem(item);
            new AnnoyingMessage(plugin, action + ".other")
                    .replace("%amount%", appliedAmount)
                    .replace("%target%", targetName)
                    .replace("%lives%", newLives)
                    .send(sender);
        });
    }

    private void executeForTarget(@NotNull OfflinePlayer target, @NotNull Runnable action) {
        final Player online = Bukkit.getPlayer(target.getUniqueId());
        if (online == null) action.run();
        else plugin.execution.runForEntityOrNow(online, action, () -> {});
    }

    @Override @Nullable
    public Collection<String> onTabComplete(@NotNull AnnoyingSender sender) {
        // Check if commands enabled
        final Location location = sender.getLocationOfSender();
        if (location != null && !plugin.config.worldsBlacklist.isWorldEnabled(location.getWorld(), Feature.COMMANDS)) return null;
        final String[] args = sender.args;
        final int length = args.length;

        final CommandSender cmdSender = sender.cmdSender;

        // No arguments: permission checks happen before constructing the visible list.
        if (length == 1) {
            final List<String> available = new ArrayList<>();
            if ((sender.isPlayer && cmdSender.hasPermission("limitedlives.get.self")) || cmdSender.hasPermission("limitedlives.get.other")) available.add("get");
            for (final String action : Arrays.asList("set", "add", "remove", "withdraw")) {
                if ((action.equals("withdraw") && !sender.isPlayer)) continue;
                if ((sender.isPlayer && cmdSender.hasPermission("limitedlives." + action + ".self")) || cmdSender.hasPermission("limitedlives." + action + ".other")) available.add(action);
            }
            if (sender.isPlayer && cmdSender.hasPermission("limitedlives.give")) available.add("give");
            if (cmdSender.hasPermission("limitedlives.convert")) available.add("convert");
            return available;
        }

        if (length == 2) {
            // convert
            if (sender.argEquals(0, "convert")) {
                if (cmdSender.hasPermission("limitedlives.convert")) return Collections.singleton("hardcorelivesplugin");
                return null;
            }
            // get
            if (sender.argEquals(0, "get")) {
                if (cmdSender.hasPermission("limitedlives.get.other")) return sender.withSelectorKeys(plugin.onlinePlayers.names(), OfflinePlayer.class);
                if (sender.isPlayer && cmdSender.hasPermission("limitedlives.get.self")) return Collections.singleton(cmdSender.getName());
                return null;
            }
            // <action>
            if (sender.argEquals(0, "give")) return sender.isPlayer && cmdSender.hasPermission("limitedlives.give") ? Collections.singleton("[<lives>]") : null;
            if (sender.argEquals(0, "set", "add", "remove", "withdraw")) {
                final String action = args[0].toLowerCase(Locale.ROOT);
                if (action.equals("withdraw") && !sender.isPlayer) return null;
                return (sender.isPlayer && cmdSender.hasPermission("limitedlives." + action + ".self")) || cmdSender.hasPermission("limitedlives." + action + ".other") ? Collections.singleton("[<lives>]") : null;
            }
            return null;
        }

        // <action>
        if (length == 3) {
            final String actionLower = sender.getArgumentOptional(0).map(String::toLowerCase).orElse(null);
            if (actionLower == null || actionLower.equals("get")) return null;
            if (actionLower.equals("give")) return sender.isPlayer && cmdSender.hasPermission("limitedlives.give") ? sender.withSelectorKeys(plugin.onlinePlayers.names(), OfflinePlayer.class) : null;
            if (actionLower.equals("withdraw") && !sender.isPlayer) return null;
            if (cmdSender.hasPermission("limitedlives." + actionLower + ".other")) return sender.withSelectorKeys(plugin.onlinePlayers.names(), OfflinePlayer.class);
            if (sender.isPlayer && cmdSender.hasPermission("limitedlives." + actionLower + ".self")) return Collections.singleton(cmdSender.getName());
        }

        return null;
    }
}
