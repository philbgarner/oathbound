package com.google.gmail.philbgarner.oathbound.ceremony;

import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.OwnershipResolver;
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
import java.util.Optional;
import java.util.UUID;
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
    private final OwnershipResolver ownershipResolver;

    public CeremonyService(Supplier<Collection<ProtectionGroup>> allGroups, OwnershipResolver ownershipResolver) {
        this.allGroups = Objects.requireNonNull(allGroups, "allGroups");
        this.ownershipResolver = Objects.requireNonNull(ownershipResolver, "ownershipResolver");
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
                case CeremonyClauseSpec.DiplomacySpec diplomacy -> {
                    if (subjectGroup == null) {
                        throw new CeremonyValidationException("No territory group resolved for a diplomacy clause.");
                    }
                    requireDiplomaticTier(liegeGroup, "The liege group");
                    requireDiplomaticTier(subjectGroup, "Their own territory group");
                    yield new Clause.DiplomacyClause(liegeGroup, subjectGroup, diplomacy.newState(),
                            new Condition.Immediate());
                }
            });
        }
        return clauses;
    }

    /** Mirrors the same REGION/KINGDOM-only restriction {@code OathboundDebugCommand}'s diplomacy
     * commands enforce - diplomatic relations only make sense between senior-most groups, and a
     * ceremony's target-resolved territory group is often a minor, Individual-tier vassal rather than
     * a Kingdom itself. Checked against the *root* of each chain, same as {@code DiplomacyService}
     * itself resolves at execution time - a minor group nested under a Kingdom is fine, an unowned
     * standalone Individual group is not. */
    private void requireDiplomaticTier(ProtectionGroupRef groupRef, String label) {
        ProtectionGroupRef rootRef = ownershipResolver.resolveRootGroup(groupRef);
        ProtectionGroup root = findGroup(rootRef.groupId())
                .orElseThrow(() -> new CeremonyValidationException("Could not resolve a root group for diplomacy."));
        if (root.tier() != GroupTier.REGION && root.tier() != GroupTier.KINGDOM) {
            throw new CeremonyValidationException(label + "'s senior-most group (" + root.name() + ") is only "
                    + root.tier() + " tier - only REGION/KINGDOM-tier groups can participate in diplomacy.");
        }
    }

    private Optional<ProtectionGroup> findGroup(UUID groupId) {
        return allGroups.get().stream().filter(group -> group.id().equals(groupId)).findFirst();
    }
}
