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

        if (getServer().getPluginManager().getPlugin("GSit") != null) {
            getLogger().info("GSit detected! Enabling auto-join on sit.");
            getServer().getPluginManager().registerEvents(new GSitListener(), this);
        }
        getLogger().info("PokerUltimateTexasHoldem enabled! Gamba time!");
    }

    @Override
    public void onDisable() {
        PokerManager.shutdownAll();
        getLogger().info("PokerUltimateTexasHoldem disabled!");
    }
    public static LetsGambaPlugin getInstance() {
    return instance;
    }

    private static EconomyProvider economy;

    public static EconomyProvider getEconomy() {
        return economy;
    }

}
