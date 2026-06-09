package dev.dr4.booster.utils;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ColorUtils() {}

    public static String colorize(String text) {
        if (text == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder magic = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                magic.append('§').append(c);
            }
            matcher.appendReplacement(sb, magic.toString());
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public static List<String> colorize(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            result.add(colorize(line));
        }
        return result;
    }

    public static String strip(String text) {
        return ChatColor.stripColor(colorize(text));
    }

    public static String formatTime(long seconds) {
        if (seconds <= 0) return "0s";
        long min = seconds / 60;
        long sec = seconds % 60;
        if (min > 0 && sec > 0) return min + "m " + sec + "s";
        if (min > 0) return min + "m";
        return sec + "s";
    }
}
