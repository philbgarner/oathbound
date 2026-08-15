package com.google.gmail.philbgarner.oathbound.economy;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Fully native, plugin-owned per-player balances. Multi-currency capable; single default currency is a fine MVP default. */
public final class EconomyService {
    private final Map<PlayerRef, Map<Currency, Long>> balances = new ConcurrentHashMap<>();
    private final Set<Currency> currencies;
    private final Currency defaultCurrency;

    public EconomyService(List<Currency> configuredCurrencies) {
        Objects.requireNonNull(configuredCurrencies, "configuredCurrencies");
        if (configuredCurrencies.isEmpty()) {
            throw new IllegalArgumentException("At least one currency must be configured");
        }
        this.currencies = Set.copyOf(configuredCurrencies);
        this.defaultCurrency = configuredCurrencies.get(0);
    }

    public Currency defaultCurrency() {
        return defaultCurrency;
    }

    public Set<Currency> currencies() {
        return currencies;
    }

    public long balance(PlayerRef player, Currency currency) {
        requireKnownCurrency(currency);
        return balances.getOrDefault(player, Map.of()).getOrDefault(currency, 0L);
    }

    public synchronized void deposit(PlayerRef player, Currency currency, long amount) {
        requireKnownCurrency(currency);
        requirePositive(amount);
        balances.computeIfAbsent(player, p -> new ConcurrentHashMap<>()).merge(currency, amount, Long::sum);
    }

    public synchronized void withdraw(PlayerRef player, Currency currency, long amount) {
        requireKnownCurrency(currency);
        requirePositive(amount);
        long current = balance(player, currency);
        if (current < amount) {
            throw new InsufficientFundsException(player, currency, amount, current);
        }
        balances.get(player).merge(currency, -amount, Long::sum);
    }

    public synchronized void transfer(PlayerRef from, PlayerRef to, Currency currency, long amount) {
        withdraw(from, currency, amount);
        deposit(to, currency, amount);
    }

    public void loadBalance(PlayerBalance balance) {
        requireKnownCurrency(balance.currency());
        balances.computeIfAbsent(balance.player(), p -> new ConcurrentHashMap<>())
                .put(balance.currency(), balance.amount());
    }

    public List<PlayerBalance> allBalances() {
        List<PlayerBalance> result = new ArrayList<>();
        balances.forEach((player, byCurrency) ->
                byCurrency.forEach((currency, amount) -> result.add(new PlayerBalance(player, currency, amount))));
        return result;
    }

    private void requireKnownCurrency(Currency currency) {
        Objects.requireNonNull(currency, "currency");
        if (!currencies.contains(currency)) {
            throw new IllegalArgumentException("Unknown currency: " + currency.id());
        }
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive: " + amount);
        }
    }
}
