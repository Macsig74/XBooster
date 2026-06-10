package dev.dr4.booster;

import dev.dr4.booster.commands.BoostAdminCommand;
import dev.dr4.booster.commands.BoosterCommand;
import dev.dr4.booster.gui.BoosterGUI;
import dev.dr4.booster.listeners.FireballListener;
import dev.dr4.booster.listeners.GUIListener;
import dev.dr4.booster.managers.BoostManager;
import dev.dr4.booster.managers.ConfigManager;
import dev.dr4.booster.managers.EconomyManager;
import dev.dr4.booster.managers.LicenseManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class BoosterPlugin extends JavaPlugin {

    private ConfigManager  configManager;
    private BoostManager   boostManager;
    private LicenseManager licenseManager;
    private EconomyManager economyManager;
    private BoosterGUI     boosterGUI;

    private String  licenseKey = "";
    private volatile boolean lockdown = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        configManager.reload();

        licenseKey = getConfig().getString("license-key", "XXXXX-XXXXX-XXXXX-XXXXX");

        licenseManager = new LicenseManager(this);
        if (!licenseManager.authenticate()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        economyManager = new EconomyManager(this);
        economyManager.hook(); // soft — plugin continues even if DonutShards absent

        boostManager = new BoostManager(this);
        boostManager.load();

        boosterGUI = new BoosterGUI(this);

        BoosterCommand executor = new BoosterCommand(this);
        var cmd = getCommand("booster");
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        var adminCmd = getCommand("boostadmin");
        if (adminCmd != null) {
            BoostAdminCommand adminExecutor = new BoostAdminCommand(this);
            adminCmd.setExecutor(adminExecutor);
            adminCmd.setTabCompleter(adminExecutor);
        }

        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new FireballListener(this), this);
    }

    @Override
    public void onDisable() {
        if (boostManager != null) boostManager.save();
    }

    public String          getLicenseKey()     { return licenseKey; }
    public ConfigManager   getConfigManager()  { return configManager; }
    public BoostManager    getBoostManager()   { return boostManager; }
    public LicenseManager  getLicenseManager() { return licenseManager; }
    public EconomyManager  getEconomyManager() { return economyManager; }
    public BoosterGUI      getBoosterGUI()     { return boosterGUI; }

    public boolean isLockdown()               { return lockdown; }
    public void    setLockdown(boolean state) { this.lockdown = state; }
}
