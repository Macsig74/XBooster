package dev.dr4.booster.listeners;

import dev.dr4.booster.BoosterPlugin;
import dev.dr4.booster.managers.BoostManager;
import dev.dr4.booster.managers.ConfigManager;
import dev.dr4.booster.managers.EconomyManager;
import dev.dr4.booster.utils.ColorUtils;
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
        handleClick(player, event.getRawSlot());
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
        BoostManager  bm  = plugin.getBoostManager();

        BoostManager.ActivationResult result = bm.tryActivate(player, boost);

        switch (result) {
            case NO_PERMISSION ->
                player.sendMessage(cfg.getMsgPrefix() + ColorUtils.colorize(cfg.getMsgNoPermission()));

            case LOCKDOWN ->
                player.sendMessage(cfg.getMsgPrefix() + ColorUtils.colorize(cfg.getMsgLockdown()));

            case ON_COOLDOWN -> {
                long remaining = bm.getRemainingCooldown(player, boost.id);
                String msg = cfg.formatMessage(cfg.getMsgCooldownActive(),
                        Map.of("remaining", ColorUtils.formatTime(remaining)));
                player.sendMessage(cfg.getMsgPrefix() + msg);
            }

            case ALREADY_ACTIVE ->
                player.sendMessage(cfg.getMsgPrefix() + ColorUtils.colorize(cfg.getMsgBoostAlreadyActive()));

            case EFFECT_ERROR -> {} // logged in BoostManager

            case SUCCESS -> {
                String msg = cfg.formatMessage(cfg.getMsgBoostActivated(), Map.of(
                        "boost",    ColorUtils.strip(boost.displayName),
                        "duration", ColorUtils.formatTime(boost.durationSeconds)
                ));
                player.sendMessage(cfg.getMsgPrefix() + msg);
                // Folia-safe: already on the player's region thread inside an event
                plugin.getBoosterGUI().open(player);
            }
        }
    }

    private void handleReset(Player player, ConfigManager.ResetConfig rc) {
        ConfigManager cfg = plugin.getConfigManager();
        EconomyManager eco = plugin.getEconomyManager();

        // 1. DonutShards disponible ?
        if (!eco.isHooked()) {
            player.sendMessage(cfg.getMsgPrefix() + ColorUtils.colorize(cfg.getMsgShardsUnavailable()));
            return;
        }

        long cost = rc.cost;

        // 2. Solde suffisant ?
        if (!eco.has(player, cost)) {
            String msg = cfg.formatMessage(cfg.getMsgNoMoney(), Map.of("cost", String.valueOf(cost)));
            player.sendMessage(cfg.getMsgPrefix() + msg);
            return;
        }

        // 3. Retrait
        if (!eco.withdraw(player, cost)) {
            player.sendMessage(cfg.getMsgPrefix()
                    + ColorUtils.colorize("&#FF0000Transaction echouee — solde insuffisant."));
            return;
        }

        // 4. Reset
        plugin.getBoostManager().resetAllCooldowns(player);
        player.sendMessage(cfg.getMsgPrefix() + ColorUtils.colorize(cfg.getMsgResetSuccess()));

        plugin.getBoosterGUI().open(player);
    }
}
