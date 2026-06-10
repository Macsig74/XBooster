package dev.dr4.booster.stands;

import dev.dr4.booster.BoosterPlugin;
import dev.dr4.booster.managers.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay; // NOSONAR
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BoostStandManager {

    private final BoosterPlugin plugin;
    private final NamespacedKey STAND_KEY;

    /** boostId → stand data */
    private final Map<String, BoostStand> stands = new ConcurrentHashMap<>();

    /** Interaction entity UUID → boostId (for fast lookup on right-click) */
    private final Map<UUID, String> interactionMap = new ConcurrentHashMap<>();

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public BoostStandManager(BoosterPlugin plugin) {
        this.plugin   = plugin;
        this.STAND_KEY = new NamespacedKey(plugin, "boost_stand_id");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Spawns all stands configured in config.yml (boost-stands section). */
    public void loadAll() {
        if (!plugin.getConfig().getBoolean("boost-stands.enabled", false)) return;

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("boost-stands.boosts");
        if (section == null) return;

        for (String boostId : section.getKeys(false)) {
            ConfigurationSection bs = section.getConfigurationSection(boostId);
            if (bs == null) continue;

            String worldName = bs.getString("world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("[BoostStands] Monde introuvable: " + worldName + " (boost: " + boostId + ")");
                continue;
            }

            Location loc = new Location(world,
                    bs.getDouble("x"), bs.getDouble("y"), bs.getDouble("z"));

            spawnStand(boostId, loc);
        }
    }

    /** Removes all stand entities (called from onDisable). */
    public void unloadAll() {
        for (BoostStand stand : stands.values()) {
            removeEntities(stand);
        }
        stands.clear();
        interactionMap.clear();
    }

    /** Places a stand at the player's current location and saves it to config. */
    public void placeStand(Player player, String boostId) {
        ConfigManager.BoostConfig cfg = plugin.getConfigManager().getBoosts().get(boostId);
        if (cfg == null) {
            player.sendMessage(colorize("&#FF0000Boost inconnu: <b>" + boostId + "</b>. IDs disponibles: "
                    + String.join(", ", plugin.getConfigManager().getBoosts().keySet())));
            return;
        }

        // Remove existing stand first
        if (stands.containsKey(boostId)) removeStand(boostId, false);

        Location loc = player.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);

        // Save to config
        plugin.getConfig().set("boost-stands.enabled", true);
        plugin.getConfig().set("boost-stands.boosts." + boostId + ".world", loc.getWorld().getName());
        plugin.getConfig().set("boost-stands.boosts." + boostId + ".x", loc.getX());
        plugin.getConfig().set("boost-stands.boosts." + boostId + ".y", loc.getY());
        plugin.getConfig().set("boost-stands.boosts." + boostId + ".z", loc.getZ());
        plugin.saveConfig();

        spawnStand(boostId, loc);
        player.sendMessage(colorize("&#00FF00Stand du boost &#FFE89D" + boostId
                + " &#00FF00place ici. Reload si tu veux le deplacer."));
    }

    /** Removes a stand and deletes its config entry. */
    public void removeStand(String boostId) {
        removeStand(boostId, true);
    }

    private void removeStand(String boostId, boolean saveConfig) {
        BoostStand stand = stands.remove(boostId);
        if (stand != null) {
            interactionMap.remove(stand.interactionId);
            removeEntities(stand);
        }
        if (saveConfig) {
            plugin.getConfig().set("boost-stands.boosts." + boostId, null);
            // Disable section if no more stands
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("boost-stands.boosts");
            if (sec == null || sec.getKeys(false).isEmpty()) {
                plugin.getConfig().set("boost-stands.enabled", false);
            }
            plugin.saveConfig();
        }
    }

    /** Returns the boostId linked to the given Interaction entity UUID, or null. */
    public String getBoostIdByInteraction(UUID interactionUUID) {
        return interactionMap.get(interactionUUID);
    }

    public Map<String, BoostStand> getStands() {
        return stands;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void spawnStand(String boostId, Location baseLoc) {
        ConfigManager.BoostConfig cfg = plugin.getConfigManager().getBoosts().get(boostId);
        if (cfg == null) return;

        BoostStand stand = new BoostStand(boostId, baseLoc);

        plugin.getServer().getRegionScheduler().run(plugin, baseLoc, task -> {
            World world = baseLoc.getWorld();
            if (world == null) return;

            // Remove orphaned stand entities near this spot from a previous session
            world.getNearbyEntities(baseLoc, 2, 3, 2).stream()
                    .filter(e -> e.getPersistentDataContainer().has(STAND_KEY, PersistentDataType.STRING))
                    .forEach(Entity::remove);

            // 1 — Interaction (hitbox for right-click, at base)
            Interaction interaction = world.spawn(baseLoc, Interaction.class, e -> {
                e.setInteractionWidth(1.0f);
                e.setInteractionHeight(1.8f);
                e.setResponsive(true);
                e.getPersistentDataContainer().set(STAND_KEY, PersistentDataType.STRING, boostId);
            });
            stand.interactionId = interaction.getUniqueId();
            interactionMap.put(interaction.getUniqueId(), boostId);

            // 2 — ItemDisplay (fireball, centered at Y+0.9)
            Location itemLoc = baseLoc.clone().add(0, 0.9, 0);
            ItemDisplay itemDisplay = world.spawn(itemLoc, ItemDisplay.class, e -> {
                e.setItemStack(new ItemStack(Material.FIRE_CHARGE));
                e.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new AxisAngle4f(0f, 0f, 1f, 0f),
                        new Vector3f(0.6f, 0.6f, 0.6f),
                        new AxisAngle4f(0f, 0f, 1f, 0f)
                ));
                e.setBillboard(Display.Billboard.VERTICAL);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setGlowing(true);
                e.getPersistentDataContainer().set(STAND_KEY, PersistentDataType.STRING, boostId);
            });
            stand.itemDisplayId = itemDisplay.getUniqueId();

            // 3 — TextDisplay (info label, Y+2.3)
            Location textLoc = baseLoc.clone().add(0, 2.3, 0);
            TextDisplay textDisplay = world.spawn(textLoc, TextDisplay.class, e -> {
                e.text(buildText(cfg));
                e.setAlignment(TextDisplay.TextAlignment.CENTER);
                e.setBillboard(Display.Billboard.CENTER);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setDefaultBackground(false);
                e.setSeeThrough(false);
                e.getPersistentDataContainer().set(STAND_KEY, PersistentDataType.STRING, boostId);
            });
            stand.textDisplayId = textDisplay.getUniqueId();

            stands.put(boostId, stand);
        });
    }

    private void removeEntities(BoostStand stand) {
        removeEntityById(stand.interactionId, stand.location);
        removeEntityById(stand.itemDisplayId, stand.location);
        removeEntityById(stand.textDisplayId, stand.location);
    }

    private void removeEntityById(UUID uuid, Location hint) {
        if (uuid == null) return;
        // Try direct lookup first (works during shutdown)
        Entity e = Bukkit.getEntity(uuid);
        if (e != null) {
            e.remove();
            return;
        }
        // Fallback: schedule on region thread (used during normal operation)
        if (hint != null && hint.getWorld() != null) {
            plugin.getServer().getRegionScheduler().run(plugin, hint, t -> {
                Entity entity = Bukkit.getEntity(uuid);
                if (entity != null) entity.remove();
            });
        }
    }

    // ── Text / formatting helpers ─────────────────────────────────────────────

    private Component buildText(ConfigManager.BoostConfig cfg) {
        String name     = formatId(cfg.id);
        String effect   = formatEffect(cfg.effect);
        String level    = cfg.amplifier > 0 ? " " + toRoman(cfg.amplifier + 1) : "";
        String duration = formatDuration(cfg.durationSeconds);

        String mm = "<gradient:#FF8C00:#FF4500><bold>" + name + "</bold></gradient>\n"
                  + "<dark_gray>─────────────────</dark_gray>\n"
                  + "<white>⚡ <yellow>" + effect + level + "</yellow></white>\n"
                  + "<white>⌛ <aqua>" + duration + "</aqua></white>\n"
                  + "<dark_gray>─────────────────</dark_gray>\n"
                  + "<green><bold>▶ Clic droit pour activer</bold></green>";
        return MM.deserialize(mm);
    }

    private static String formatId(String id) {
        return Arrays.stream(id.split("-"))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static String formatEffect(String effect) {
        return Arrays.stream(effect.toLowerCase().split("_"))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static String formatDuration(long seconds) {
        if (seconds >= 60) {
            long m = seconds / 60, s = seconds % 60;
            return s == 0 ? m + " min" : m + "m " + s + "s";
        }
        return seconds + "s";
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n);
        };
    }

    private static String colorize(String s) {
        return dev.dr4.booster.utils.ColorUtils.colorize(s);
    }
}
