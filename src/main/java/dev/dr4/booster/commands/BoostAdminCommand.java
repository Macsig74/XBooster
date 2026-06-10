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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BoostAdminCommand implements CommandExecutor, TabCompleter {

    private final BoosterPlugin plugin;
    private static final String PREFIX = "&#00C1FF[BoostAdmin] &f";

    public BoostAdminCommand(BoosterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("booster.admin")) {
            sender.sendMessage(ColorUtils.colorize(PREFIX + "&#FF0000Permission refusee."));
            return true;
        }

        if (args.length == 0) { sendHelp(sender); return true; }

        return switch (args[0].toLowerCase()) {
            case "list"         -> handleList(sender);
            case "cutall"       -> handleCutAll(sender);
            case "lockdown"     -> handleLockdown(sender);
            case "placestand"   -> handlePlaceStand(sender, args);
            case "removestand"  -> handleRemoveStand(sender, args);
            default             -> { sendHelp(sender); yield true; }
        };
    }

    // ── /boostadmin list ─────────────────────────────────────────────────────

    private boolean handleList(CommandSender sender) {
        Map<UUID, String> active = plugin.getBoostManager().getActiveBoostIds();

        if (active.isEmpty()) {
            sender.sendMessage(ColorUtils.colorize(PREFIX + "&7Aucun boost actif en ce moment."));
            return true;
        }

        sender.sendMessage(ColorUtils.colorize(PREFIX + "&7Boosts actifs (" + active.size() + ") :"));
        for (Map.Entry<UUID, String> entry : active.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            String playerName = p != null ? p.getName() : entry.getKey().toString();
            long remaining = plugin.getBoostManager().getRemainingActiveBoost(entry.getKey());
            String boostId = entry.getValue();
            sender.sendMessage(ColorUtils.colorize(
                "  &8▸ &f" + playerName + " &8— &b" + boostId
                + " &8(&7" + ColorUtils.formatTime(remaining) + " restant&8)"
            ));
        }
        return true;
    }

    // ── /boostadmin cutall ───────────────────────────────────────────────────

    private boolean handleCutAll(CommandSender sender) {
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.getBoostManager().hasActiveBoost(p)) {
                plugin.getBoostManager().removeActiveBoostEffects(p);
                p.sendMessage(ColorUtils.colorize(
                    plugin.getConfigManager().getMsgPrefix()
                    + "&#FF9900Ton boost a ete supprime par un administrateur."
                ));
                count++;
            }
        }
        sender.sendMessage(ColorUtils.colorize(
            PREFIX + "&#00FF00" + count + " boost(s) supprime(s)."
        ));
        plugin.getLogger().info("[BoostAdmin] cutall execute par " + sender.getName()
                + " — " + count + " boost(s) coupe(s).");
        return true;
    }

    // ── /boostadmin lockdown ─────────────────────────────────────────────────

    private boolean handleLockdown(CommandSender sender) {
        boolean newState = !plugin.isLockdown();
        plugin.setLockdown(newState);

        if (newState) {
            sender.sendMessage(ColorUtils.colorize(
                PREFIX + "&#FF0000LOCKDOWN ACTIVE — les boosts sont desactives pour tous les joueurs."
            ));
            // Prévenir les joueurs en ligne
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.hasPermission("booster.admin")) {
                    p.sendMessage(ColorUtils.colorize(
                        plugin.getConfigManager().getMsgPrefix()
                        + "&#FF9900Le systeme de boosts est temporairement verrouille."
                    ));
                }
            }
        } else {
            sender.sendMessage(ColorUtils.colorize(
                PREFIX + "&#00FF00LOCKDOWN DESACTIVE — les boosts sont de nouveau disponibles."
            ));
        }

        plugin.getLogger().info("[BoostAdmin] lockdown " + (newState ? "ON" : "OFF")
                + " par " + sender.getName());
        return true;
    }

    // ── /boostadmin placestand <id> ──────────────────────────────────────────

    private boolean handlePlaceStand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorize(PREFIX + "&#FF0000Commande reservee aux joueurs."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ColorUtils.colorize(PREFIX + "&7Usage: /boostadmin placestand <boostId>"));
            return true;
        }
        plugin.getBoostStandManager().placeStand(player, args[1].toLowerCase());
        return true;
    }

    // ── /boostadmin removestand <id> ─────────────────────────────────────────

    private boolean handleRemoveStand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtils.colorize(PREFIX + "&7Usage: /boostadmin removestand <boostId>"));
            return true;
        }
        String boostId = args[1].toLowerCase();
        if (!plugin.getBoostStandManager().getStands().containsKey(boostId)) {
            sender.sendMessage(ColorUtils.colorize(PREFIX + "&#FF0000Aucun stand actif pour: " + boostId));
            return true;
        }
        plugin.getBoostStandManager().removeStand(boostId);
        sender.sendMessage(ColorUtils.colorize(PREFIX + "&#00FF00Stand &#FFE89D" + boostId + " &#00FF00supprime."));
        return true;
    }

    // ── Help ─────────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtils.colorize(PREFIX + "&7Commandes :"));
        sender.sendMessage(ColorUtils.colorize("  &f/boostadmin list                 &8— &7Voir les boosts actifs"));
        sender.sendMessage(ColorUtils.colorize("  &f/boostadmin cutall               &8— &7Couper tous les boosts actifs"));
        sender.sendMessage(ColorUtils.colorize("  &f/boostadmin lockdown             &8— &7Activer / desactiver le lockdown"));
        sender.sendMessage(ColorUtils.colorize("  &f/boostadmin placestand <id>      &8— &7Placer un stand de boost ici"));
        sender.sendMessage(ColorUtils.colorize("  &f/boostadmin removestand <id>     &8— &7Supprimer un stand de boost"));
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("booster.admin")) return List.of();
        if (args.length == 1) {
            return Arrays.asList("list", "cutall", "lockdown", "placestand", "removestand").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        // Second arg: boost ID for placestand / removestand
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("placestand") || sub.equals("removestand")) {
                return plugin.getConfigManager().getBoosts().keySet().stream()
                        .filter(id -> id.startsWith(args[1].toLowerCase()))
                        .toList();
            }
        }
        return List.of();
    }
}
