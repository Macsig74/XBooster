package dev.dr4.booster.gui;

import dev.dr4.booster.BoosterPlugin;
import dev.dr4.booster.managers.ConfigManager;
import dev.dr4.booster.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class BoosterGUI {

    private final BoosterPlugin plugin;

    public BoosterGUI(BoosterPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        Inventory inv = Bukkit.createInventory(null, cfg.getGuiSize(), cfg.getGuiTitle());

        for (ConfigManager.BoostConfig boost : cfg.getBoosts().values()) {
            long remaining = plugin.getBoostManager().getRemainingCooldown(player, boost.id);
            inv.setItem(boost.slot, buildBoostItem(boost, remaining));
        }

        ConfigManager.ResetConfig rc = cfg.getResetConfig();
        if (rc != null) {
            inv.setItem(rc.slot, buildResetItem(rc));
        }

        player.openInventory(inv);
    }

    private ItemStack buildBoostItem(ConfigManager.BoostConfig boost, long remainingSeconds) {
        Material material = parseMaterial(boost.material);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(ColorUtils.colorize(boost.displayName));

        String cooldownText = plugin.getConfigManager().formatCooldownLore(remainingSeconds);
        List<String> finalLore = new ArrayList<>();
        for (String line : boost.lore) {
            if (line.contains("{cooldown}")) {
                finalLore.add(buildCooldownLine(line, cooldownText));
            } else {
                finalLore.add(ColorUtils.colorize(line));
            }
        }
        meta.setLore(finalLore);

        if (boost.glow) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        item.setItemMeta(meta);
        return item;
    }

    private String buildCooldownLine(String template, String cooldownText) {
        String[] parts = template.split("\\{cooldown\\}", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            sb.append(ColorUtils.colorize(parts[i]));
            if (i < parts.length - 1) sb.append(cooldownText);
        }
        return sb.toString();
    }

    private ItemStack buildResetItem(ConfigManager.ResetConfig rc) {
        Material material = parseMaterial(rc.material);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(ColorUtils.colorize(rc.displayName));

        List<String> lore = new ArrayList<>();
        for (String line : rc.lore) {
            lore.add(ColorUtils.colorize(line.replace("{cost}", ColorUtils.colorize(rc.costDisplay))));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        item.setItemMeta(meta);
        return item;
    }

    private Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Materiau inconnu: " + name + " — utilisation de STONE");
            return Material.STONE;
        }
    }
}
