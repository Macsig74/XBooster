package dev.dr4.booster.commands;

import dev.dr4.booster.BoosterPlugin;
import dev.dr4.booster.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BoosterCommand implements CommandExecutor, TabCompleter {

    private final BoosterPlugin plugin;

    public BoosterCommand(BoosterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Cette commande est reservee aux joueurs.");
                return true;
            }
            if (!player.hasPermission("booster.use")) {
                player.sendMessage(plugin.getConfigManager().getMsgPrefix()
                        + ColorUtils.colorize(plugin.getConfigManager().getMsgNoPermission()));
                return true;
            }
            plugin.getBoosterGUI().open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload" -> {
                if (!sender.hasPermission("booster.reload")) {
                    sender.sendMessage(plugin.getConfigManager().getMsgPrefix()
                            + ColorUtils.colorize(plugin.getConfigManager().getMsgNoPermission()));
                    return true;
                }
                plugin.getConfigManager().reload();
                sender.sendMessage(plugin.getConfigManager().getMsgPrefix()
                        + ColorUtils.colorize(plugin.getConfigManager().getMsgReloadSuccess()));
            }

            case "reset" -> {
                if (!sender.hasPermission("booster.resetcooldown")) {
                    sender.sendMessage(plugin.getConfigManager().getMsgPrefix()
                            + ColorUtils.colorize(plugin.getConfigManager().getMsgNoPermission()));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.getConfigManager().getMsgPrefix()
                            + ColorUtils.colorize("&fUsage: /booster reset <joueur>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(plugin.getConfigManager().getMsgPrefix()
                            + ColorUtils.colorize(plugin.getConfigManager().getMsgPlayerNotFound()));
                    return true;
                }
                plugin.getBoostManager().resetAllCooldowns(target);
                sender.sendMessage(plugin.getConfigManager().getMsgPrefix()
                        + ColorUtils.colorize("&#00FF00Cooldown de &f" + target.getName() + " &#00FF00reinitialise."));
                target.sendMessage(plugin.getConfigManager().getMsgPrefix()
                        + ColorUtils.colorize(plugin.getConfigManager().getMsgResetSuccess()));
            }

            default -> {
                sender.sendMessage(plugin.getConfigManager().getMsgPrefix()
                        + ColorUtils.colorize("&fCommandes disponibles:"));
                sender.sendMessage(plugin.getConfigManager().getMsgPrefix()
                        + ColorUtils.colorize("&f/booster &8- &7Ouvre le menu"));
                sender.sendMessage(plugin.getConfigManager().getMsgPrefix()
                        + ColorUtils.colorize("&f/booster reload &8- &7Recharge la config"));
                sender.sendMessage(plugin.getConfigManager().getMsgPrefix()
                        + ColorUtils.colorize("&f/booster reset <joueur> &8- &7Reset le cooldown d'un joueur"));
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("reload", "reset").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
