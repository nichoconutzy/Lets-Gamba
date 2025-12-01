package me.choconutzy.letsGamba;

import me.choconutzy.letsGamba.Commands.GSitListener;
import me.choconutzy.letsGamba.Commands.gambaCommand;
import me.choconutzy.letsGamba.Economy.EconomyProvider;
import me.choconutzy.letsGamba.Economy.TippingListener;
import me.choconutzy.letsGamba.Economy.VaultEconomyProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class LetsGambaPlugin extends JavaPlugin {

    private static LetsGambaPlugin instance;

    private EconomyProvider economyProvider;

    @Override
    public void onEnable() {
        instance = this;

        // Economy setup
        this.economyProvider = new VaultEconomyProvider(this);
        getServer().getPluginManager().registerEvents(new TippingListener(), this);
        economy = new VaultEconomyProvider(this);
        getLogger().info("Poker economy provider: " + economy.getProviderName());
        // Plugin startup logic
        getCommand("poker").setExecutor(new gambaCommand());

        if (getServer().getPluginManager().getPlugin("GSit") != null) {
            getLogger().info("GSit detected! Enabling auto-join on sit.");
            getServer().getPluginManager().registerEvents(new GSitListener(), this);
        }
        getLogger().info("LetsGamba has been enabled! Poker time!");


    }

    @Override
    public void onDisable() {
        me.choconutzy.letsGamba.pokerLogic.PokerManager.shutdownAll();
        getLogger().info("LetsGamba - PokerTexasHoldem disabled!");
    }
    public static LetsGambaPlugin getInstance() {
    return instance;
    }

    private static EconomyProvider economy;

    public EconomyProvider getEconomyProvider(){
        return economyProvider;
    }

    public static EconomyProvider getEconomy() {
        return economy;
    }

}
