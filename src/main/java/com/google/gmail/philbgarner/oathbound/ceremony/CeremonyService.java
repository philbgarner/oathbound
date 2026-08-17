package com.google.gmail.philbgarner.oathbound.ceremony;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import com.google.gmail.philbgarner.oathbound.oath.Clause;
import com.google.gmail.philbgarner.oathbound.oath.Condition;
import com.google.gmail.philbgarner.oathbound.oath.SerializedItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Bukkit-free orchestration for the Ceremony Designer: resolving the target's own territory group and
 * turning a {@link CeremonyTemplateDefinition}'s clause specs into real
 * {@code com.google.gmail.philbgarner.oathbound.oath.Clause}s. Deliberately doesn't touch inventories or
 * currency balances directly - a ceremony's Bukkit-side glue (the chat-confirm listener) validates and
 * withdraws tribute items first, then calls {@link #materialize} with the results, mirroring how
 * {@code ConditionEngine} keeps Bukkit concerns out of the domain layer.
 */
public final class CeremonyService {

    private final Supplier<Collection<ProtectionGroup>> allGroups;

    public CeremonyService(Supplier<Collection<ProtectionGroup>> allGroups) {
        this.allGroups = Objects.requireNonNull(allGroups, "allGroups");
    }

    /** The sole {@link ProtectionGroup} {@code target} personally owns directly (not via an owner chain,
     * and not merely a group they hold transfer permission on) - the "their territory" a
     * {@link CeremonyClauseSpec.TransferSpec} escheats away from. Throws
     * {@link CeremonyValidationException} if that's zero or more than one group. */
    public ProtectionGroupRef resolveSubjectGroup(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        List<ProtectionGroup> owned = allGroups.get().stream()
                .filter(group -> group.owner() instanceof PlayerRef owner && owner.equals(target))
                .toList();
        if (owned.isEmpty()) {
            throw new CeremonyValidationException("They don't personally own a territory group to bind.");
        }
        if (owned.size() > 1) {
            throw new CeremonyValidationException("They personally own more than one territory group - ambiguous.");
        }
        return new ProtectionGroupRef(owned.get(0).id());
    }

    /** Turns a template's clause specs into real clauses. {@code tributeItems} must already hold a
     * validated, withdrawn {@link SerializedItemStack} per {@link CeremonyClauseSpec.TributeSpec}
     * instance (by identity, since a template can list more than one), and {@code recipients} must
     * already resolve every {@link CeremonyClauseSpec.TributeSpec#recipientPlayerName()} to a
     * {@link PlayerRef} - both are Bukkit-side lookups the caller performs before calling this. */
    public List<Clause> materialize(CeremonyTemplateDefinition template, PlayerRef target,
                                     ProtectionGroupRef liegeGroup, ProtectionGroupRef subjectGroup,
                                     Map<CeremonyClauseSpec.TributeSpec, SerializedItemStack> tributeItems,
                                     Function<String, PlayerRef> recipients) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(liegeGroup, "liegeGroup");
        Objects.requireNonNull(tributeItems, "tributeItems");
        Objects.requireNonNull(recipients, "recipients");

        List<Clause> clauses = new ArrayList<>();
        for (CeremonyClauseSpec spec : template.clauses()) {
            clauses.add(switch (spec) {
                case CeremonyClauseSpec.TransferSpec transfer -> {
                    if (subjectGroup == null) {
                        throw new CeremonyValidationException("No territory group resolved for a transfer clause.");
                    }
                    yield new Clause.TransferClause(liegeGroup, subjectGroup,
                            new Condition.PvpDeathCount(target, transfer.pvpDeathCount()));
                }
                case CeremonyClauseSpec.TributeSpec tribute -> {
                    SerializedItemStack item = tributeItems.get(tribute);
                    if (item == null) {
                        throw new CeremonyValidationException("No tribute item resolved for " + tribute.materialName() + ".");
                    }
                    PlayerRef recipient = recipients.apply(tribute.recipientPlayerName());
                    if (recipient == null) {
                        throw new CeremonyValidationException("Unknown tribute recipient: " + tribute.recipientPlayerName());
                    }
                    yield new Clause.EscrowClause(target, recipient, List.of(item), Map.of(),
                            List.of(new Clause.ReleaseStep(1.0, new Condition.Immediate())));
                }
                case CeremonyClauseSpec.MobKillSpec mobKill ->
                        new Clause.MobKillClause(target, mobKill.mobTypeName(), mobKill.quantity());
                case CeremonyClauseSpec.CustomFlagSpec customFlag -> new Clause.CustomFlagClause(customFlag.text());
            });
        }
        return clauses;
    }
}
