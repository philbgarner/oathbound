package com.google.gmail.philbgarner.oathbound.bounty;

import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.economy.EconomyService;
import com.google.gmail.philbgarner.oathbound.economy.InsufficientFundsException;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.oath.Oath;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Places, fulfills, and cancels {@link Bounty} kill contracts.
 *
 * <p>Deliberately not built on {@code Oath}/{@code OathService}/{@code ConditionEngine}: a bounty's
 * "other party" (the victim) never consents, so it can't go through the PROPOSED-&gt;SEALED handshake
 * every Oath requires; its reward recipient is whoever turns a head in, not a party named at draft time
 * the way {@code Clause.EscrowClause.recipient()} is fixed; and its per-head installment payout is
 * fundamentally incremental, which {@code ConditionEngine}'s release-schedule model deliberately doesn't
 * support (its own javadoc calls this out as future work). What <em>is</em> reused directly is
 * {@link EconomyService} for holding/paying/refunding currency - the same primitive Escrow itself is
 * built on.
 */
public final class BountyService {

    private final EconomyService economyService;
    private final Function<UUID, Optional<ProtectionGroup>> groupLookup;
    private final Supplier<Collection<Bounty>> allBounties;
    private final Supplier<Collection<Oath>> allOaths;
    private final long feeBase;
    private final double heatFeeMultiplier;
    private final Duration heatDecayWindow;
    private final int maxPlacementsPer24h;
    private final Duration breachDiscountWindow;
    private final double breachDiscountFraction;

    public BountyService(EconomyService economyService, Function<UUID, Optional<ProtectionGroup>> groupLookup,
                          Supplier<Collection<Bounty>> allBounties, Supplier<Collection<Oath>> allOaths,
                          long feeBase, double heatFeeMultiplier, Duration heatDecayWindow,
                          int maxPlacementsPer24h, Duration breachDiscountWindow, double breachDiscountFraction) {
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.groupLookup = Objects.requireNonNull(groupLookup, "groupLookup");
        this.allBounties = Objects.requireNonNull(allBounties, "allBounties");
        this.allOaths = Objects.requireNonNull(allOaths, "allOaths");
        this.feeBase = feeBase;
        this.heatFeeMultiplier = heatFeeMultiplier;
        this.heatDecayWindow = Objects.requireNonNull(heatDecayWindow, "heatDecayWindow");
        this.maxPlacementsPer24h = maxPlacementsPer24h;
        this.breachDiscountWindow = Objects.requireNonNull(breachDiscountWindow, "breachDiscountWindow");
        this.breachDiscountFraction = breachDiscountFraction;
    }

    public record PlacementQuote(double heat, long fee, boolean discounted) {
    }

    /** The fee a placement would cost right now, without actually placing anything - used by the GUI to
     * show a cost preview before the player confirms. */
    public PlacementQuote quote(PlayerRef placer, BountyTarget target, Instant now) {
        double heat = HeatCalculator.heatOf(target, allBounties.get(), groupLookup, now, heatDecayWindow);
        long fee = HeatCalculator.feeFor(heat, feeBase, heatFeeMultiplier);
        boolean discounted = HeatCalculator.recentBreachBetween(
                placer, target, allOaths.get(), groupLookup, now, breachDiscountWindow);
        if (discounted) {
            fee = Math.round(fee * (1.0 - breachDiscountFraction));
        }
        return new PlacementQuote(heat, fee, discounted);
    }

    /** Validates targeting, computes the heat-scaled fee (with breach discount if applicable), and
     * withdraws fee + reward from the placer atomically. Throws {@link BountyPlacementLimitException} if
     * the placer has hit the 24h placement cap, or {@link InsufficientFundsException} if they can't
     * afford fee + reward - in either case nothing is withdrawn. */
    public Bounty place(PlayerRef placer, BountyTarget target, int requestedQuantity,
                         Map<Currency, Long> totalReward, Currency feeCurrency, Instant now) {
        Objects.requireNonNull(placer, "placer");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(totalReward, "totalReward");
        Objects.requireNonNull(feeCurrency, "feeCurrency");

        int quantity = resolveQuantity(target, requestedQuantity);
        if (totalReward.isEmpty() || totalReward.values().stream().anyMatch(v -> v <= 0)) {
            throw new IllegalArgumentException("Reward must include at least one positive currency amount");
        }

        if (HeatCalculator.placementsInLast24h(placer, allBounties.get(), now) >= maxPlacementsPer24h) {
            throw new BountyPlacementLimitException(
                    "You've already placed " + maxPlacementsPer24h + " bounties in the last 24 hours");
        }

        PlacementQuote quote = quote(placer, target, now);

        Map<Currency, Long> totalCost = new HashMap<>(totalReward);
        if (quote.fee() > 0) {
            totalCost.merge(feeCurrency, quote.fee(), Long::sum);
        }
        for (Map.Entry<Currency, Long> entry : totalCost.entrySet()) {
            long balance = economyService.balance(placer, entry.getKey());
            if (balance < entry.getValue()) {
                throw new InsufficientFundsException(placer, entry.getKey(), entry.getValue(), balance);
            }
        }
        totalCost.forEach((currency, amount) -> economyService.withdraw(placer, currency, amount));

        return new Bounty(UUID.randomUUID(), placer, target, quantity, quantity, totalReward, quote.fee(), now,
                BountyStatus.ACTIVE);
    }

    private int resolveQuantity(BountyTarget target, int requestedQuantity) {
        return switch (target) {
            case BountyTarget.Solo ignored -> 1;
            case BountyTarget.Group group -> {
                ProtectionGroup protectionGroup = groupLookup.apply(group.group().groupId())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown group target"));
                int memberCount = protectionGroup.members().size();
                if (requestedQuantity < 1 || requestedQuantity > memberCount) {
                    throw new IllegalArgumentException(
                            "Quantity must be between 1 and the group's current member count (" + memberCount + ")");
                }
                yield requestedQuantity;
            }
        };
    }

    /** What turning in the next head would pay right now, without actually doing so - used to size a
     * banishment sentence at the moment of a qualifying kill (before anyone has necessarily turned the
     * head in yet, possibly ever). */
    public Map<Currency, Long> previewNextHeadReward(Bounty bounty) {
        return bounty.rewardForNextHead();
    }

    /** Pays the finder their share and decrements the remaining count, flipping to FULFILLED once the
     * last head is turned in. Returns the amount actually paid this turn-in. */
    public Map<Currency, Long> turnInHead(Bounty bounty, PlayerRef finder, Instant now) {
        Objects.requireNonNull(bounty, "bounty");
        Objects.requireNonNull(finder, "finder");
        if (bounty.status() != BountyStatus.ACTIVE || bounty.remainingQuantity() <= 0) {
            throw new IllegalStateException("Bounty " + bounty.id() + " is not active");
        }
        Map<Currency, Long> reward = bounty.rewardForNextHead();
        reward.forEach((currency, amount) -> {
            if (amount > 0) {
                economyService.deposit(finder, currency, amount);
            }
        });
        bounty.decrementRemaining();
        if (bounty.remainingQuantity() <= 0) {
            bounty.setStatus(BountyStatus.FULFILLED);
        }
        return reward;
    }

    /** Refunds the unpaid reward entitlement to the placer. The fee is not refunded - a sunk placement
     * cost, same as it always was even for a bounty that's never fulfilled at all. */
    public void cancel(Bounty bounty) {
        Objects.requireNonNull(bounty, "bounty");
        if (bounty.status() != BountyStatus.ACTIVE) {
            throw new IllegalStateException("Bounty " + bounty.id() + " is not active");
        }
        Map<Currency, Long> refund = bounty.remainingReward();
        refund.forEach((currency, amount) -> {
            if (amount > 0) {
                economyService.deposit(bounty.placer(), currency, amount);
            }
        });
        bounty.setStatus(BountyStatus.CANCELLED);
    }

    /** Marks a victim as having received their one-time login notice for this bounty - see
     * {@code BountyLoginNoticeListener}. */
    public void markNotified(Bounty bounty, PlayerRef victim) {
        Objects.requireNonNull(bounty, "bounty");
        Objects.requireNonNull(victim, "victim");
        bounty.markNotified(victim);
    }

    /** Group-target auto-decrement when a targeted member is flagged abandoned - no head, no payout, see
     * {@link BountyAbandonmentSweepService}. */
    public void decrementForAbandonment(Bounty bounty, PlayerRef member) {
        Objects.requireNonNull(bounty, "bounty");
        Objects.requireNonNull(member, "member");
        if (bounty.status() != BountyStatus.ACTIVE || bounty.isAbandonmentCounted(member)) {
            return;
        }
        bounty.markAbandonmentCounted(member);
        bounty.decrementRemaining();
        if (bounty.remainingQuantity() <= 0) {
            bounty.setStatus(BountyStatus.FULFILLED);
        }
    }
}
