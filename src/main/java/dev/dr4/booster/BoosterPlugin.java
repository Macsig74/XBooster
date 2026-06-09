package dev.dr4.booster;

import dev.dr4.booster.commands.BoosterCommand;
import dev.dr4.booster.gui.BoosterGUI;
import dev.dr4.booster.listeners.GUIListener;
import dev.dr4.booster.managers.BoostManager;
import dev.dr4.booster.managers.ConfigManager;
import dev.dr4.booster.managers.EconomyManager;
import dev.dr4.booster.managers.LicenseManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class BoosterPlugin extends JavaPlugin {

    private ConfigManager  configManager;
    private BoostManager   boostManager;
    private LicenseManager licenseManager;
    private EconomyManager economyManager;
    private BoosterGUI     boosterGUI;

    private String licenseKey = "";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadLicenseKey();

        configManager = new ConfigManager(this);
        configManager.reload();

        licenseManager = new LicenseManager(this);
        if (!licenseManager.authenticate()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        economyManager = new EconomyManager(this);
        economyManager.hook(); // soft — plugin continues even if Vault absent

        boostManager = new BoostManager(this);
        boostManager.load();

        boosterGUI = new BoosterGUI(this);

        BoosterCommand executor = new BoosterCommand(this);
        var cmd = getCommand("booster");
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
    }

    @Override
    public void onDisable() {
        if (boostManager != null) {
            boostManager.save();
        }
    }

    private void loadLicenseKey() {
        File licenseFile = new File(getDataFolder(), "license.yml");
        if (!licenseFile.exists()) {
            saveResource("license.yml", false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(licenseFile);
        licenseKey = cfg.getString("key", "XXXXX-XXXXX-XXXXX-XXXXX");
    }

    public String          getLicenseKey()     { return licenseKey; }
    public ConfigManager   getConfigManager()  { return configManager; }
    public BoostManager    getBoostManager()   { return boostManager; }
    public LicenseManager  getLicenseManager() { return licenseManager; }
    public EconomyManager  getEconomyManager() { return economyManager; }
    public BoosterGUI      getBoosterGUI()     { return boosterGUI; }
}
