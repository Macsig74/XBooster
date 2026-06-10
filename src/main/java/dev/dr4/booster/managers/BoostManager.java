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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BoostManager {

    private final BoosterPlugin plugin;

    // UUID -> (boostId -> cooldown expiry timestamp ms)
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    // ── Active boost tracking (anti-cumul) ───────────────────────────────────
    // Tracks which boost is currently active and when it expires.
    // Only ONE boost can be active per player at a time.
    private final Map<UUID, String> activeBoostId     = new ConcurrentHashMap<>();
    private final Map<UUID, Long>   activeBoostExpiry = new ConcurrentHashMap<>();

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
                .computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(boostId, System.currentTimeMillis() + durationMs);
    }

    public void resetAllCooldowns(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    public void resetCooldown(Player player, String boostId) {
        Map<String, Long> map = cooldowns.get(player.getUniqueId());
        if (map != null) map.remove(boostId);
    }

    // ── Active boost (anti-cumul) ─────────────────────────────────────────────

    /**
     * Returns true if the player currently has an active boost from this plugin.
     * Automatically cleans up expired entries.
     */
    public boolean hasActiveBoost(Player player) {
        UUID uuid = player.getUniqueId();
        Long expiry = activeBoostExpiry.get(uuid);
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            activeBoostId.remove(uuid);
            activeBoostExpiry.remove(uuid);
            return false;
        }
        return true;
    }

    /** Registers which boost is currently active for this player. */
    private void registerActiveBoost(Player player, String boostId, long durationSeconds) {
        UUID uuid = player.getUniqueId();
        activeBoostId.put(uuid, boostId);
        activeBoostExpiry.put(uuid, System.currentTimeMillis() + durationSeconds * 1000L);
    }

    /** Clears the active boost marker (used by cutall / logout). */
    public void clearActiveBoost(UUID uuid) {
        activeBoostId.remove(uuid);
        activeBoostExpiry.remove(uuid);
    }

    /**
     * Removes all active boost potion effects from the player and clears the tracker.
     * Used by /boostadmin cutall.
     */
    public void removeActiveBoostEffects(Player player) {
        clearActiveBoost(player.getUniqueId());
        for (ConfigManager.BoostConfig boost : plugin.getConfigManager().getBoosts().values()) {
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(boost.effect.toLowerCase()));
            if (type != null) player.removePotionEffect(type);
        }
    }

    /**
     * Returns remaining active boost time in seconds (0 if none).
     */
    public long getRemainingActiveBoost(UUID uuid) {
        Long expiry = activeBoostExpiry.get(uuid);
        if (expiry == null) return 0;
        long remaining = expiry - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000L : 0;
    }

    /** Active boost ID → player UUID map (read-only view, for /boostadmin list). */
    public Map<UUID, String> getActiveBoostIds() {
        // Clean expired first
        long now = System.currentTimeMillis();
        activeBoostExpiry.entrySet().removeIf(e -> {
            if (e.getValue() <= now) {
                activeBoostId.remove(e.getKey());
                return true;
            }
            return false;
        });
        return Collections.unmodifiableMap(activeBoostId);
    }

    // ── Unified activation (used by GUI and stands) ───────────────────────────

    public enum ActivationResult { SUCCESS, NO_PERMISSION, LOCKDOWN, ON_COOLDOWN, ALREADY_ACTIVE, EFFECT_ERROR }

    /**
     * Performs all checks and applies the boost if allowed.
     * Returns SUCCESS if the boost was applied, or an error code otherwise.
     */
    public ActivationResult tryActivate(Player player, ConfigManager.BoostConfig boost) {
        if (!player.hasPermission("booster.use"))              return ActivationResult.NO_PERMISSION;
        if (plugin.isLockdown())                               return ActivationResult.LOCKDOWN;
        if (!player.hasPermission("booster.bypass") && hasCooldown(player, boost.id))
                                                               return ActivationResult.ON_COOLDOWN;
        if (!player.hasPermission("booster.bypass") && hasActiveBoost(player))
                                                               return ActivationResult.ALREADY_ACTIVE;
        if (!applyBoost(player, boost))                        return ActivationResult.EFFECT_ERROR;
        setCooldown(player, boost.id);
        return ActivationResult.SUCCESS;
    }

    // ── Effect application ────────────────────────────────────────────────────

    public boolean applyBoost(Player player, ConfigManager.BoostConfig cfg) {
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(cfg.effect.toLowerCase()));
        if (type == null) {
            plugin.getLogger().warning("Effet inconnu: " + cfg.effect);
            return false;
        }
        int durationTicks = (int) (cfg.durationSeconds * 20L);
        // ambient=false, particles=false, icon=true — effet visible dans l'inventaire
        // mais SANS particules autour du joueur
        player.addPotionEffect(new PotionEffect(type, durationTicks, cfg.amplifier, false, false, true));

        registerActiveBoost(player, cfg.id, cfg.durationSeconds);
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
                Map<String, Long> map = new ConcurrentHashMap<>();
                for (String boostId : yml.getConfigurationSection(uuidStr).getKeys(false)) {
                    long expiry = yml.getLong(uuidStr + "." + boostId);
                    if (expiry > now) map.put(boostId, expiry);
                }
                if (!map.isEmpty()) cooldowns.put(uuid, map);
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
