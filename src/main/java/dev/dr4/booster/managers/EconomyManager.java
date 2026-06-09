package dev.dr4.booster.managers;

import dev.dr4.booster.BoosterPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyManager {

    private final BoosterPlugin plugin;
    private Economy economy;

    public EconomyManager(BoosterPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Attempts to hook into a Vault Economy provider.
     *
     * @return true if a provider was found and hooked successfully.
     */
    public boolean hook() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault introuvable — le bouton Reset Cooldown sera desactive.");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);

        if (rsp == null) {
            plugin.getLogger().warning("Aucun provider Economy trouve dans Vault — le bouton Reset Cooldown sera desactive.");
            return false;
        }

        economy = rsp.getProvider();
        plugin.getLogger().info("Vault Economy hooke : " + economy.getName());
        return true;
    }

    /**
     * Returns true if Vault is available and a provider is hooked.
     */
    public boolean isHooked() {
        return economy != null;
    }

    /**
     * Returns true if the player has at least {@code amount} in their balance.
     */
    public boolean has(Player player, double amount) {
        return isHooked() && economy.has(player, amount);
    }

    /**
     * Withdraws {@code amount} from the player.
     *
     * @return the EconomyResponse — always check {@link EconomyResponse#transactionSuccess()}.
     */
    public EconomyResponse withdraw(Player player, double amount) {
        return economy.withdrawPlayer(player, amount);
    }

    /**
     * Human-readable name of the currency as provided by the Economy plugin.
     */
    public String currencyName(double amount) {
        return isHooked() ? economy.format(amount) : String.valueOf(amount);
    }
}
