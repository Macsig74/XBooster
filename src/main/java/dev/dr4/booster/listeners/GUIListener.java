package dev.dr4.booster.listeners;

import dev.dr4.booster.BoosterPlugin;
import dev.dr4.booster.managers.ConfigManager;
import dev.dr4.booster.managers.EconomyManager;
import dev.dr4.booster.utils.ColorUtils;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;

public class GUIListener implements Listener {

    private final BoosterPlugin plugin;

    public GUIListener(BoosterPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTitle() == null) return;
        if (!event.getView().getTitle().equals(plugin.getConfigManager().getGuiTitle())) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;
        int slot = event.getRawSlot();

        handleClick(player, slot);
    }

    private void handleClick(Player player, int slot) {
        ConfigManager cfg = plugin.getConfigManager();

        ConfigManager.ResetConfig rc = cfg.getResetConfig();
        if (rc != null && slot == rc.slot) {
            handleReset(player, rc);
            return;
        }

        for (ConfigManager.BoostConfig boost : cfg.getBoosts().values()) {
            if (boost.slot == slot) {
                handleBoost(player, boost);
                return;
            }
        }
    }

    private void handleBoost(Player player, ConfigManager.BoostConfig boost) {
        ConfigManager cfg = plugin.getConfigManager();

        if (!player.hasPermission("booster.use")) {
            player.sendMessage(cfg.getMsgPrefix() + ColorUtils.colorize(cfg.getMsgNoPermission()));
            return;
        }

        if (!player.hasPermission("booster.bypass") && plugin.getBoostManager().hasCooldown(player, boost.id)) {
            long remaining = plugin.getBoostManager().getRemainingCooldown(player, boost.id);
            String msg = cfg.formatMessage(cfg.getMsgCooldownActive(),
                    Map.of("remaining", ColorUtils.formatTime(remaining)));
            player.sendMessage(cfg.getMsgPrefix() + msg);
            return;
        }

        if (!plugin.getBoostManager().applyBoost(player, boost)) return;

        plugin.getBoostManager().setCooldown(player, boost.id);

        String msg = cfg.formatMessage(cfg.getMsgBoostActivated(), Map.of(
                "boost", ColorUtils.strip(boost.displayName),
                "duration", ColorUtils.formatTime(boost.durationSeconds)
        ));
        player.sendMessage(cfg.getMsgPrefix() + msg);

        // Folia-safe: we are already on the player's region thread inside an event
        plugin.getBoosterGUI().open(player);
    }

    private void handleReset(Player player, ConfigManager.ResetConfig rc) {
        ConfigManager cfg = plugin.getConfigManager();
        EconomyManager eco = plugin.getEconomyManager();

        // 1. Vault disponible ?
        if (!eco.isHooked()) {
            player.sendMessage(cfg.getMsgPrefix()
                    + ColorUtils.colorize(cfg.getMsgVaultUnavailable()));
            return;
        }

        double cost = rc.cost;

        // 2. Solde suffisant ?
        if (!eco.has(player, cost)) {
            String costFormatted = eco.currencyName(cost);
            String msg = cfg.formatMessage(cfg.getMsgNoMoney(), Map.of("cost", costFormatted));
            player.sendMessage(cfg.getMsgPrefix() + msg);
            return;
        }

        // 3. Retrait — on vérifie que la transaction a réellement abouti
        EconomyResponse response = eco.withdraw(player, cost);
        if (!response.transactionSuccess()) {
            player.sendMessage(cfg.getMsgPrefix()
                    + ColorUtils.colorize("&#FF0000Transaction echouee : " + response.errorMessage));
            return;
        }

        // 4. Tout est bon — on reset
        plugin.getBoostManager().resetAllCooldowns(player);
        player.sendMessage(cfg.getMsgPrefix()
                + ColorUtils.colorize(cfg.getMsgResetSuccess()));

        plugin.getBoosterGUI().open(player);
    }
}
