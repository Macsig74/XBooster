package dev.dr4.booster.items;

import dev.dr4.booster.managers.ConfigManager;
import dev.dr4.booster.utils.ColorUtils;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fabrique des FIREWORK_STAR colorés représentant chaque boost.
 * Clic droit → active le boost (géré par FireballListener).
 */
public class FireballItem {

    public static final String PDC_KEY = "boost_id";

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9A-Fa-f]{6})");

    private FireballItem() {}

    public static ItemStack build(Plugin plugin, ConfigManager.BoostConfig boost) {
        ItemStack item = new ItemStack(Material.FIREWORK_STAR);
        FireworkEffectMeta meta = (FireworkEffectMeta) item.getItemMeta();
        if (meta == null) return item;

        // Couleur tirée du display-name (premier &#RRGGBB trouvé)
        Color color = extractColor(boost.displayName);

        meta.setEffect(FireworkEffect.builder()
                .withColor(color)
                .with(FireworkEffect.Type.BALL)
                .build());

        meta.setDisplayName(boost.colorizedDisplayName);
        meta.setLore(buildLore(boost));

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, PDC_KEY),
                PersistentDataType.STRING,
                boost.id
        );

        item.setItemMeta(meta);
        return item;
    }

    private static List<String> buildLore(ConfigManager.BoostConfig boost) {
        String effect   = formatEffect(boost.effect);
        String level    = boost.amplifier > 0 ? " " + toRoman(boost.amplifier + 1) : "";
        String duration = formatDuration(boost.durationSeconds);

        return List.of(
                ColorUtils.colorize("&8⌁⌁⌁⌁⌁⌁⌁⌁⌁⌁⌁⌁⌁⌁⌁"),
                ColorUtils.colorize("&7⚡ &#FFE89D" + effect + level),
                ColorUtils.colorize("&7⌛ &#FFE89D" + duration),
                ColorUtils.colorize("&8⌁⌁⌁⌁⌁⌁⌁⌁⌁⌁⌁⌁⌁⌁⌁"),
                ColorUtils.colorize("&#FBE401▶ &#FBE401&l&nCLIC DROIT&#FBE401 pour activer")
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Color extractColor(String displayName) {
        Matcher m = HEX_PATTERN.matcher(displayName);
        if (m.find()) {
            String hex = m.group(1);
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return Color.fromRGB(r, g, b);
        }
        return Color.WHITE;
    }

    private static String formatEffect(String effect) {
        String[] words = effect.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty())
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
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
}
