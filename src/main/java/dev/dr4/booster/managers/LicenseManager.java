package dev.dr4.booster.managers;

import dev.dr4.booster.BoosterPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.logging.Logger;

public class LicenseManager {

    // URL and HMAC secret are XOR-encoded so they don't appear as plain
    // strings in the decompiled jar — defeats `strings`, Google indexing,
    // and casual decompiler search. Decoded at class-load time below.
    private static final String LICENSE_SERVER = dec("322f282d64704f6269726f6c715774686b676d6f506a743d2d3770163f29353b27");
    private static final String HMAC_SECRET    = dec("6a6a6c64676b513f6838386b67556c3a3e3c6b6f06693f3a6d3a3a556a6a3e693a6f59636f386d6d69533b6c683f6b6b556a6c3a3b3a3a056b3f6d6f6a665062");
    private static final String PLUGIN_NAME    = "Booster";
    private static final String DISCORD_LINK   = "https://discord.gg/qhu3n47vaA";
    private static final int    BOX_WIDTH      = 64;

    // ── ANSI colors (rendered by Pterodactyl console + any ANSI terminal) ──
    private static final String RESET   = "\033[0m";
    private static final String BOLD    = "\033[1m";
    private static final String DIM     = "\033[2m";
    private static final String UNDER   = "\033[4m";
    private static final String GRAY    = "\033[90m";
    private static final String WHITE   = "\033[97m";
    private static final String BCYAN   = "\033[96m";
    private static final String BGREEN  = "\033[92m";
    private static final String BRED    = "\033[91m";
    private static final String BYELLOW = "\033[93m";
    private static final String BBLUE   = "\033[94m";

    private final BoosterPlugin plugin;

    private boolean valid     = false;
    private String  ownerName = "";

    public LicenseManager(BoosterPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean authenticate() {
        String licenseKey = plugin.getLicenseKey();

        if (licenseKey.isEmpty() || licenseKey.equals("XXXXX-XXXXX-XXXXX-XXXXX")) {
            try {
                String serverIP   = getServerIP();
                int    serverPort = plugin.getServer().getPort();
                String urlString  = buildSignedUrl("NO_LICENSE", serverIP, serverPort);
                HttpURLConnection c = (HttpURLConnection) URI.create(urlString).toURL().openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                c.getResponseCode();
            } catch (Exception ignored) {}

            printBanner();
            printBox("NO LICENSE KEY SET", BYELLOW, new String[]{
                    kv("Plugin", PLUGIN_NAME + " v" + plugin.getDescription().getVersion()),
                    "",
                    BYELLOW + "Claim a FREE key on our Discord :" + RESET,
                    "  " + link(DISCORD_LINK),
                    "",
                    DIM + "Then paste it in plugins/" + PLUGIN_NAME + "/license.yml" + RESET
            });
            return false;
        }

        try {
            String serverIP   = getServerIP();
            int    serverPort = plugin.getServer().getPort();
            String urlString  = buildSignedUrl(licenseKey, serverIP, serverPort);

            HttpURLConnection connection = (HttpURLConnection) URI.create(urlString).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();

            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                String json = response.toString();

                if (json.contains("\"valid\":true")) {
                    valid = true;
                    if (json.contains("\"owner\":\"")) {
                        int start = json.indexOf("\"owner\":\"") + 9;
                        int end   = json.indexOf("\"", start);
                        ownerName = json.substring(start, end);
                    }
                    printBanner();
                    printBox("LICENSE KEY VALID", BGREEN, new String[]{
                            kv("Plugin", PLUGIN_NAME + " v" + plugin.getDescription().getVersion()),
                            kv("Owner",  ownerName),
                            kv("Key",    licenseKey),
                            kv("IP",     serverIP + ":" + serverPort),
                            "",
                            BGREEN + "Thanks for using GlowStudios plugins!" + RESET
                    });
                    return true;

                } else {
                    String error = "Invalid license";
                    if (json.contains("\"error\":\"")) {
                        int start = json.indexOf("\"error\":\"") + 9;
                        int end   = json.indexOf("\"", start);
                        error = json.substring(start, end);
                    }
                    printBanner();
                    printBox("INVALID LICENSE", BRED, new String[]{
                            kv("Plugin", PLUGIN_NAME + " v" + plugin.getDescription().getVersion()),
                            kv("Error",  error),
                            kv("IP",     serverIP + ":" + serverPort),
                            "",
                            BYELLOW + "Claim a FREE key on our Discord :" + RESET,
                            "  " + link(DISCORD_LINK)
                    });
                    return false;
                }

            } else {
                printBanner();
                printBox("LICENSE SERVER ERROR", BRED, new String[]{
                        kv("Plugin", PLUGIN_NAME + " v" + plugin.getDescription().getVersion()),
                        kv("HTTP",   String.valueOf(responseCode)),
                        "",
                        DIM + "Plugin will now disable itself." + RESET
                });
                return false;
            }

        } catch (Exception e) {
            printBanner();
            printBox("LICENSE SERVER UNREACHABLE", BRED, new String[]{
                    kv("Plugin", PLUGIN_NAME + " v" + plugin.getDescription().getVersion()),
                    kv("Error",  e.getMessage()),
                    "",
                    DIM + "Plugin will now disable itself." + RESET
            });
            return false;
        }
    }

    // ── Banner ──────────────────────────────────────────────────────────────

    private void printBanner() {
        Logger log = plugin.getLogger();
        log.info("");
        log.info(BCYAN + " ██████╗ ██╗      ██████╗ ██╗    ██╗███████╗████████╗██╗   ██╗██████╗ ██╗ ██████╗ ███████╗" + RESET);
        log.info(BCYAN + "██╔════╝ ██║     ██╔═══██╗██║    ██║██╔════╝╚══██╔══╝██║   ██║██╔══██╗██║██╔═══██╗██╔════╝" + RESET);
        log.info(BCYAN + "██║  ███╗██║     ██║   ██║██║ █╗ ██║███████╗   ██║   ██║   ██║██║  ██║██║██║   ██║███████╗" + RESET);
        log.info(BCYAN + "██║   ██║██║     ██║   ██║██║███╗██║╚════██║   ██║   ██║   ██║██║  ██║██║██║   ██║╚════██║" + RESET);
        log.info(BCYAN + "╚██████╔╝███████╗╚██████╔╝╚███╔███╔╝███████║   ██║   ╚██████╔╝██████╔╝██║╚██████╔╝███████║" + RESET);
        log.info(BCYAN + " ╚═════╝ ╚══════╝ ╚═════╝  ╚══╝╚══╝ ╚══════╝   ╚═╝    ╚═════╝ ╚═════╝ ╚═╝ ╚═════╝ ╚══════╝" + RESET);
        log.info("");
    }

    private void printBox(String title, String titleColor, String[] lines) {
        Logger log   = plugin.getLogger();
        String horiz = "═".repeat(BOX_WIDTH);
        log.info(GRAY + "╔" + horiz + "╗" + RESET);
        log.info(centerLine(BOLD + titleColor + title + RESET));
        log.info(GRAY + "╠" + horiz + "╣" + RESET);
        for (String l : lines) log.info(leftLine(l));
        log.info(GRAY + "╚" + horiz + "╝" + RESET);
        log.info("");
    }

    private String centerLine(String text) {
        int visible = visibleLen(text);
        if (visible > BOX_WIDTH) {
            text    = text.substring(0, BOX_WIDTH);
            visible = BOX_WIDTH;
        }
        int pad   = BOX_WIDTH - visible;
        int left  = pad / 2;
        int right = pad - left;
        return GRAY + "║" + RESET + " ".repeat(left) + text + " ".repeat(right) + GRAY + "║" + RESET;
    }

    private String leftLine(String text) {
        int visible = visibleLen(text);
        if (visible > BOX_WIDTH - 2) {
            text    = text.substring(0, Math.min(text.length(), BOX_WIDTH - 2));
            visible = visibleLen(text);
        }
        return GRAY + "║" + RESET + " " + text
                + " ".repeat(Math.max(0, BOX_WIDTH - visible - 1))
                + GRAY + "║" + RESET;
    }

    // ── ANSI helpers ─────────────────────────────────────────────────────────

    private static String kv(String key, String value) {
        return DIM + GRAY + key + " :" + RESET + "  " + WHITE + value + RESET;
    }

    private static String link(String url) {
        return UNDER + BBLUE + url + RESET;
    }

    private static int visibleLen(String s) {
        return s.replaceAll("\033\\[[0-9;]*m", "").length();
    }

    // ── HMAC + URL signing ───────────────────────────────────────────────────

    private String buildSignedUrl(String key, String serverIP, int serverPort) {
        long   ts      = System.currentTimeMillis() / 1000L;
        String payload = key + "|" + PLUGIN_NAME + "|" + serverIP + "|" + serverPort + "|" + ts;
        String sig     = hmacSha256(payload, HMAC_SECRET);
        return LICENSE_SERVER
                + "?key="    + java.net.URLEncoder.encode(key,         java.nio.charset.StandardCharsets.UTF_8)
                + "&plugin=" + java.net.URLEncoder.encode(PLUGIN_NAME, java.nio.charset.StandardCharsets.UTF_8)
                + "&ip="     + java.net.URLEncoder.encode(serverIP,    java.nio.charset.StandardCharsets.UTF_8)
                + "&port="   + serverPort
                + "&ts="     + ts
                + "&sig="    + sig;
    }

    /** Decode an XOR-encoded hex string back to plaintext. */
    private static String dec(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        StringBuilder sb = new StringBuilder(bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            sb.append((char) ((bytes[i] & 0xFF) ^ (0x5A + i % 7)));
        }
        return sb.toString();
    }

    /** HMAC-SHA256 of {@code message} with {@code key}, hex-encoded. */
    private static String hmacSha256(String message, String key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(message.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String getServerIP() {
        try {
            var url = URI.create("https://api.ipify.org").toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
            String ip = reader.readLine();
            reader.close();
            return ip;
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    public boolean isValid()      { return valid; }
    public String  getOwnerName() { return ownerName; }
}
