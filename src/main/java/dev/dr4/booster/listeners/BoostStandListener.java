package dev.dr4.booster.listeners;

import dev.dr4.booster.BoosterPlugin;
import dev.dr4.booster.managers.BoostManager;
import dev.dr4.booster.managers.ConfigManager;
import dev.dr4.booster.utils.ColorUtils;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;

public class BoostStandListener implements Listener {

    private final BoosterPlugin plugin;

    public BoostStandListener(BoosterPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        // Filter: right-click (hand), Interaction entity only
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Interaction)) return;

        String boostId = plugin.getBoostStandManager()
                .getBoostIdByInteraction(event.getRightClicked().getUniqueId());
        if (boostId == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        ConfigManager.BoostConfig boost = plugin.getConfigManager().getBoosts().get(boostId);
        if (boost == null) return;

        activateFromStand(player, boost);
    }

    private void activateFromStand(Player player, ConfigManager.BoostConfig boost) {
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
            }
        }
    }
}
