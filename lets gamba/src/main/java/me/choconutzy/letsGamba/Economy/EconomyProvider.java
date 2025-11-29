package me.choconutzy.letsGamba.Economy;

import java.math.BigDecimal;
import java.util.UUID;

public interface EconomyProvider {

    boolean isEnabled();

    boolean hasEnough(UUID playerUuid, BigDecimal amount);

    boolean add(UUID playerUuid, BigDecimal amount);

    boolean subtract(UUID playerUuid, BigDecimal amount);

    BigDecimal getBalance(UUID playerUuid);

    String getProviderName();

}
