package dev.dr4.booster.listeners;

import dev.dr4.booster.BoosterPlugin;
import dev.dr4.booster.items.FireballItem;
import dev.dr4.booster.managers.BoostManager;
import dev.dr4.booster.managers.ConfigManager;
import dev.dr4.booster.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public class FireballListener implements Listener {

    private final BoosterPlugin plugin;
    private final NamespacedKey pdcKey;

    public FireballListener(BoosterPlugin plugin) {
        this.plugin = plugin;
        this.pdcKey = new NamespacedKey(plugin, FireballItem.PDC_KEY);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Clic droit uniquement (air ou bloc), main principale seulement
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FIREWORK_STAR) return;

        var meta = item.getItemMeta();
        if (meta == null) return;

        String boostId = meta.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
        if (boostId == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        ConfigManager.BoostConfig boost = plugin.getConfigManager().getBoosts().get(boostId);
        if (boost == null) return;

        activateBoost(player, boost, item);
    }

    private void activateBoost(Player player, ConfigManager.BoostConfig boost, ItemStack item) {
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

            case EFFECT_ERROR -> {}

            case SUCCESS -> {
                // Consomme l'item (1 use only)
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
                String msg = cfg.formatMessage(cfg.getMsgBoostActivated(), Map.of(
                        "boost",    ColorUtils.strip(boost.displayName),
                        "duration", ColorUtils.formatTime(boost.durationSeconds)
                ));
                player.sendMessage(cfg.getMsgPrefix() + msg);
            }
        }
    }
}
