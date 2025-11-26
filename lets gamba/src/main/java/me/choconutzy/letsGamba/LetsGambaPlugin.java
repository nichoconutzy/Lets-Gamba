package me.choconutzy.letsGamba;

import me.choconutzy.letsGamba.Economy.EconomyProvider;
import me.choconutzy.letsGamba.Economy.VaultEconomyProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class LetsGambaPlugin extends JavaPlugin {

    private static LetsGambaPlugin instance;

    @Override
    public void onEnable() {
        instance = this;

        // Economy setup
        economy = new VaultEconomyProvider(this);
        getLogger().info("Poker economy provider: " + economy.getProviderName());
        // Plugin startup logic
        getCommand("poker").setExecutor(new gambaCommand());
        getLogger().info("PokerUltimateTexasHoldem enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("PokerUltimateTexasHoldem disabled!");
        // Plugin shutdown logic
    }
    public static LetsGambaPlugin getInstance() {
    return instance;
    }

    private static EconomyProvider economy;

    public static EconomyProvider getEconomy() {
        return economy;
    }

}
