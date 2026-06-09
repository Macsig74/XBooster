package dev.dr4.booster.managers;

import dev.dr4.booster.BoosterPlugin;
import dev.dr4.booster.utils.ShardsAPI;
import org.bukkit.entity.Player;

/**
 * Thin wrapper around DonutShards — replaces the previous Vault integration.
 * Uses the reflection bridge (ShardsAPI) so DonutShards is a soft-dependency
 * with zero compile-time coupling.
 */
public class EconomyManager {

    private final BoosterPlugin plugin;

    public EconomyManager(BoosterPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Attempts to connect to DonutShards.
     *
     * @return true if DonutShards is present and hooked successfully.
     */
    public boolean hook() {
        ShardsAPI.reset(); // force fresh lookup after (re)load
        if (ShardsAPI.available()) {
            plugin.getLogger().info("DonutShards hooke avec succes.");
            return true;
        } else {
            plugin.getLogger().warning("DonutShards introuvable — le bouton Reset Cooldown sera desactive.");
            return false;
        }
    }

    /** True if DonutShards is available. */
    public boolean isHooked() {
        return ShardsAPI.available();
    }

    /** True if the player has at least {@code amount} shards. */
    public boolean has(Player player, long amount) {
        return ShardsAPI.has(player.getUniqueId(), amount);
    }

    /**
     * Withdraws {@code amount} shards from the player.
     *
     * @return true if the transaction succeeded (player had enough shards).
     */
    public boolean withdraw(Player player, long amount) {
        return ShardsAPI.take(player.getUniqueId(), amount);
    }
}
