package com.google.gmail.philbgarner.oathbound.persistence.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gmail.philbgarner.oathbound.bounty.BountyTarget;
import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import com.google.gmail.philbgarner.oathbound.oath.Clause;
import com.google.gmail.philbgarner.oathbound.oath.Condition;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class GsonFactory {
    private GsonFactory() {
    }

    public static Gson create() {
        return new GsonBuilder()
                .registerTypeAdapter(UUID.class, SimpleStringTypeAdapters.UUID_ADAPTER)
                .registerTypeAdapter(Instant.class, SimpleStringTypeAdapters.INSTANT_ADAPTER)
                .registerTypeAdapter(Duration.class, SimpleStringTypeAdapters.DURATION_ADAPTER)
                .registerTypeAdapter(Currency.class, SimpleStringTypeAdapters.CURRENCY_ADAPTER)
                .registerTypeAdapterFactory(new PolymorphicTypeAdapterFactory<>(EntityRef.class, Map.of(
                        "player", PlayerRef.class,
                        "group", ProtectionGroupRef.class
                )))
                .registerTypeAdapterFactory(new PolymorphicTypeAdapterFactory<>(Condition.class, Map.ofEntries(
                        Map.entry("immediate", Condition.Immediate.class),
                        Map.entry("time_elapsed", Condition.TimeElapsed.class),
                        Map.entry("death_count", Condition.DeathCount.class),
                        Map.entry("pvp_death_count", Condition.PvpDeathCount.class),
                        Map.entry("mob_kill_count", Condition.MobKillCount.class),
                        Map.entry("payment_received", Condition.PaymentReceived.class),
                        Map.entry("vote_tally", Condition.VoteTally.class),
                        Map.entry("manual_confirm", Condition.ManualConfirm.class),
                        Map.entry("compound", Condition.Compound.class)
                )))
                .registerTypeAdapterFactory(new PolymorphicTypeAdapterFactory<>(Clause.class, Map.of(
                        "transfer", Clause.TransferClause.class,
                        "escrow", Clause.EscrowClause.class,
                        "custom_flag", Clause.CustomFlagClause.class,
                        "kill_count", Clause.KillCountClause.class,
                        "mob_kill", Clause.MobKillClause.class,
                        "diplomacy", Clause.DiplomacyClause.class
                )))
                .registerTypeAdapterFactory(new PolymorphicTypeAdapterFactory<>(BountyTarget.class, Map.of(
                        "solo", BountyTarget.Solo.class,
                        "group", BountyTarget.Group.class
                )))
                .create();
    }
}
