package dev.dr4.booster.managers;

import dev.dr4.booster.BoosterPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BoostManager {

    private final BoosterPlugin plugin;

    // UUID -> (boostId -> expiry timestamp in ms)
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    private File cooldownFile;

    public BoostManager(BoosterPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        cooldownFile = new File(plugin.getDataFolder(), "cooldowns.yml");
        if (plugin.getConfigManager().isSaveCooldowns()) {
            loadCooldowns();
        }
    }

    public void save() {
        if (!plugin.getConfigManager().isSaveCooldowns()) return;
        saveCooldowns();
    }

    // ── Cooldown logic ────────────────────────────────────────────────────────

    public boolean hasCooldown(Player player, String boostId) {
        Map<String, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null) return false;
        Long expiry = map.get(boostId);
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            map.remove(boostId);
            return false;
        }
        return true;
    }

    public long getRemainingCooldown(Player player, String boostId) {
        Map<String, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null) return 0;
        Long expiry = map.get(boostId);
        if (expiry == null) return 0;
        long remaining = expiry - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000L : 0;
    }

    /**
     * Returns the effective cooldown in seconds for this player,
     * picking the shortest tier the player has permission for,
     * or the default cooldown if no tier matches.
     */
    public long getCooldownFor(Player player) {
        for (Map.Entry<String, Long> tier : plugin.getConfigManager().getCooldownTiers().entrySet()) {
            if (player.hasPermission(tier.getKey())) {
                return tier.getValue();
            }
        }
        return plugin.getConfigManager().getCooldownSeconds();
    }

    public void setCooldown(Player player, String boostId) {
        long durationMs = getCooldownFor(player) * 1000L;
        cooldowns
                .computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(boostId, System.currentTimeMillis() + durationMs);
    }

    public void resetAllCooldowns(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    public void resetCooldown(Player player, String boostId) {
        Map<String, Long> map = cooldowns.get(player.getUniqueId());
        if (map != null) map.remove(boostId);
    }

    // ── Effect application ────────────────────────────────────────────────────

    public boolean applyBoost(Player player, ConfigManager.BoostConfig cfg) {
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(cfg.effect.toLowerCase()));
        if (type == null) {
            plugin.getLogger().warning("Effet inconnu: " + cfg.effect);
            return false;
        }
        int durationTicks = (int) (cfg.durationSeconds * 20L);
        player.addPotionEffect(new PotionEffect(type, durationTicks, cfg.amplifier, false, true, true));
        return true;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void saveCooldowns() {
        FileConfiguration yml = new YamlConfiguration();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<String, Long>> entry : cooldowns.entrySet()) {
            for (Map.Entry<String, Long> boost : entry.getValue().entrySet()) {
                if (boost.getValue() > now) {
                    yml.set(entry.getKey().toString() + "." + boost.getKey(), boost.getValue());
                }
            }
        }
        try {
            yml.save(cooldownFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder les cooldowns: " + e.getMessage());
        }
    }

    private void loadCooldowns() {
        if (!cooldownFile.exists()) return;
        FileConfiguration yml = YamlConfiguration.loadConfiguration(cooldownFile);
        long now = System.currentTimeMillis();
        for (String uuidStr : yml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                Map<String, Long> map = new HashMap<>();
                for (String boostId : yml.getConfigurationSection(uuidStr).getKeys(false)) {
                    long expiry = yml.getLong(uuidStr + "." + boostId);
                    if (expiry > now) {
                        map.put(boostId, expiry);
                    }
                }
                if (!map.isEmpty()) {
                    cooldowns.put(uuid, map);
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
