# Limited Lives

## Requirements

- **Folia 1.21.11** (this build is Folia-only and is not supported on ordinary Paper/Spigot servers)
- **Java 21**
- Optional: PlaceholderAPI 2.12.2 or newer
- Optional: WorldGuard 7.0.15 with its matching WorldEdit dependency

All player/entity work is dispatched through Folia ownership schedulers. Placeholder values use non-blocking snapshots, so permission-derived values are refreshed from player-owned event/command contexts rather than blocking a region thread.

Every player has a limited amount of lives. When a player loses all of their lives, they are punished (according to the config). Almost everything is configurable. *Originally made for [Mickaboo](https://youtube.com/@Mickabo)*

**🐛 Bugs / 💡 Suggestions:** Please [open an issue](https://github.com/srnyx/limited-lives/issues/new/choose) to report a bug or suggest an idea

**🆘 Support:** Please [join the Discord](https://srnyx.com/discord) to get support

## Download

**✅ Stable:** You can download the latest **stable** version at [Modrinth](https://modrinth.com/plugin/limitedlives), [Hangar](https://hangar.papermc.io/srnyx/LimitedLives), [Spigot](https://spigotmc.org/resources/109078), [Bukkit](https://dev.bukkit.org/projects/limited-lives), or [GitHub](https://github.com/srnyx/limited-lives/releases)

**🚧 Snapshot:** You can download the latest **snapshot** version at [actions/workflows/build.yml](https://github.com/srnyx/limited-lives/actions/workflows/build.yml)

# Wiki

For all information about the plugin (commands, permissions, etc...) please see the wiki at [github.com/srnyx/limited-lives/wiki](https://github.com/srnyx/limited-lives/wiki)
