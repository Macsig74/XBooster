package dev.dr4.booster.managers;

import dev.dr4.booster.BoosterPlugin;
import dev.dr4.booster.utils.ColorUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class ConfigManager {

    private final BoosterPlugin plugin;

    private String guiTitle;
    private int guiSize;
    private long cooldownSeconds;
    private boolean saveCooldowns;
    // permission -> cooldown seconds (sorted: shortest first)
    private final java.util.LinkedHashMap<String, Long> cooldownTiers = new java.util.LinkedHashMap<>();

    private final Map<String, BoostConfig> boosts = new LinkedHashMap<>();
    private ResetConfig resetConfig;

    private String msgPrefix;
    private String msgBoostActivated;
    private String msgCooldownActive;
    private String msgResetSuccess;
    private String msgNoMoney;
    private String msgVaultUnavailable;
    private String msgNoPermission;
    private String msgReloadSuccess;
    private String msgPlayerNotFound;
    private String msgCooldownReady;
    private String msgCooldownFormat;

    public ConfigManager(BoosterPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        guiTitle = ColorUtils.colorize(cfg.getString("gui.title", "🌊 Booster Menu"));
        guiSize = cfg.getInt("gui.size", 36);
        cooldownSeconds = cfg.getLong("settings.cooldown", 1800);
        saveCooldowns = cfg.getBoolean("settings.save-cooldowns", true);

        cooldownTiers.clear();
        ConfigurationSection tiers = cfg.getConfigurationSection("settings.cooldown-tiers");
        if (tiers != null) {
            // Sort by value ascending so the shortest cooldown wins first
            tiers.getKeys(false).stream()
                    .sorted(java.util.Comparator.comparingLong(k -> tiers.getLong((String) k)))
                    .forEach(k -> cooldownTiers.put(k, tiers.getLong(k)));
        }

        boosts.clear();
        ConfigurationSection boostsSection = cfg.getConfigurationSection("boosts");
        if (boostsSection != null) {
            for (String key : boostsSection.getKeys(false)) {
                ConfigurationSection s = boostsSection.getConfigurationSection(key);
                if (s == null || !s.getBoolean("enabled", true)) continue;
                boosts.put(key, new BoostConfig(
                        key,
                        s.getInt("slot"),
                        s.getString("material", "STONE"),
                        s.getString("display-name", key),
                        s.getStringList("lore"),
                        s.getString("effect", "SPEED"),
                        s.getInt("amplifier", 0),
                        s.getLong("duration", 180),
                        s.getBoolean("glow", false)
                ));
            }
        }

        ConfigurationSection rc = cfg.getConfigurationSection("reset-cooldown");
        if (rc != null && rc.getBoolean("enabled", true)) {
            resetConfig = new ResetConfig(
                    rc.getInt("slot", 31),
                    rc.getString("material", "LIME_DYE"),
                    rc.getString("display-name", "&#00FF00&lRESET COOLDOWN"),
                    rc.getStringList("lore"),
                    rc.getDouble("cost", 500.0),
                    rc.getString("cost-display", "500x")
            );
        } else {
            resetConfig = null;
        }

        msgPrefix = ColorUtils.colorize(cfg.getString("messages.prefix", "[Booster] "));
        msgBoostActivated = cfg.getString("messages.boost-activated", "Boost {boost} active pour {duration}!");
        msgCooldownActive = cfg.getString("messages.cooldown-active", "Attends {remaining}.");
        msgResetSuccess = cfg.getString("messages.reset-success", "Cooldown reinitialise!");
        msgNoMoney = cfg.getString("messages.no-money", "&#FF0000Solde insuffisant. Il te faut {cost}.");
        msgVaultUnavailable = cfg.getString("messages.vault-unavailable", "&#FF0000Vault n'est pas disponible.");
        msgNoPermission = cfg.getString("messages.no-permission", "Permission refusee.");
        msgReloadSuccess = cfg.getString("messages.reload-success", "Plugin rechargé.");
        msgPlayerNotFound = cfg.getString("messages.player-not-found", "Joueur introuvable.");
        msgCooldownReady = cfg.getString("messages.cooldown-ready", "&#00FF00Ready!");
        msgCooldownFormat = cfg.getString("messages.cooldown-format", "&#FF0000{min}m {sec}s");
    }

    public String formatMessage(String raw, Map<String, String> placeholders) {
        String result = raw;
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                result = result.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return ColorUtils.colorize(result);
    }

    public String formatCooldownLore(long remainingSeconds) {
        if (remainingSeconds <= 0) return ColorUtils.colorize(msgCooldownReady);
        long min = remainingSeconds / 60;
        long sec = remainingSeconds % 60;
        return ColorUtils.colorize(msgCooldownFormat
                .replace("{min}", String.valueOf(min))
                .replace("{sec}", String.valueOf(sec)));
    }

    public java.util.Map<String, Long> getCooldownTiers() { return cooldownTiers; }
    public String getGuiTitle()        { return guiTitle; }
    public int getGuiSize()            { return guiSize; }
    public long getCooldownSeconds()   { return cooldownSeconds; }
    public boolean isSaveCooldowns()   { return saveCooldowns; }
    public Map<String, BoostConfig> getBoosts() { return boosts; }
    public ResetConfig getResetConfig(){ return resetConfig; }
    public String getMsgPrefix()       { return msgPrefix; }
    public String getMsgBoostActivated(){ return msgBoostActivated; }
    public String getMsgCooldownActive(){ return msgCooldownActive; }
    public String getMsgResetSuccess()     { return msgResetSuccess; }
    public String getMsgNoMoney()          { return msgNoMoney; }
    public String getMsgVaultUnavailable() { return msgVaultUnavailable; }
    public String getMsgNoPermission()     { return msgNoPermission; }
    public String getMsgReloadSuccess(){ return msgReloadSuccess; }
    public String getMsgPlayerNotFound(){ return msgPlayerNotFound; }

    // ── Inner config records ──────────────────────────────────────────────────

    public static class BoostConfig {
        public final String id;
        public final int slot;
        public final String material;
        public final String displayName;
        public final List<String> lore;
        public final String effect;
        public final int amplifier;
        public final long durationSeconds;
        public final boolean glow;

        public BoostConfig(String id, int slot, String material, String displayName,
                           List<String> lore, String effect, int amplifier,
                           long durationSeconds, boolean glow) {
            this.id = id;
            this.slot = slot;
            this.material = material;
            this.displayName = displayName;
            this.lore = lore;
            this.effect = effect;
            this.amplifier = amplifier;
            this.durationSeconds = durationSeconds;
            this.glow = glow;
        }
    }

    public static class ResetConfig {
        public final int slot;
        public final String material;
        public final String displayName;
        public final List<String> lore;
        public final double cost;
        public final String costDisplay;

        public ResetConfig(int slot, String material, String displayName,
                           List<String> lore, double cost, String costDisplay) {
            this.slot = slot;
            this.material = material;
            this.displayName = displayName;
            this.lore = lore;
            this.cost = cost;
            this.costDisplay = costDisplay;
        }
    }
}
