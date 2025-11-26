package me.choconutzy.letsGamba.Economy;

import java.math.BigDecimal;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultEconomyProvider implements EconomyProvider {
    private Economy economy;
    private boolean enabled = false;

    public VaultEconomyProvider(Plugin plugin) {
        setupEconomy(plugin);
    }

    private void setupEconomy(Plugin plugin) {
        // Check Vault plugin exists
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            enabled = false;
            return;
        }

        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);

        if (rsp == null) {
            enabled = false;
            return;
        }

        economy = rsp.getProvider();
        enabled = (economy != null);
    }

    @Override
    public boolean hasEnough(UUID playerUuid, BigDecimal amount) {
        if (!enabled || economy == null) return false;
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return true;

        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        return economy.has(player, amount.doubleValue());
    }

    @Override
    public boolean add(UUID playerUuid, BigDecimal amount) {
        if (!enabled || economy == null) return false;
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return true;

        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        return economy.depositPlayer(player, amount.doubleValue()).transactionSuccess();
    }

    @Override
    public boolean subtract(UUID playerUuid, BigDecimal amount) {
        if (!enabled || economy == null) return false;
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return true;

        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        return economy.withdrawPlayer(player, amount.doubleValue()).transactionSuccess();
    }

    @Override
    public BigDecimal getBalance(UUID playerUuid) {
        if (!enabled || economy == null) return BigDecimal.ZERO;

        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            return BigDecimal.valueOf(economy.getBalance(player));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    @Override
    public String getProviderName() {
        return enabled && economy != null ? economy.getName() : "None";
    }
}
