package com.google.gmail.philbgarner.oathbound.ceremony;

import java.util.List;
import java.util.Objects;

/** Admin-authored, declared in {@code config.yml}'s {@code ceremony-templates} list - not persisted,
 * since it's static configuration rather than runtime player data (same reasoning as
 * {@code bounty.PveContractDefinition}). {@code itemMaterialName} is a plain
 * {@code org.bukkit.Material} name and {@code CeremonyClauseSpec.MobKillSpec.mobTypeName()} a plain
 * {@code org.bukkit.entity.EntityType} name, rather than the enums themselves, since this package is
 * Bukkit-free by design; Bukkit-glue code resolves the real types at the point of use.
 *
 * <p>Confirmation is a clickable [Accept]/[Decline] chat prompt (see
 * {@code listener.CeremonyChatListener}), not free-text phrase matching - an earlier design matched the
 * target's next chat message against configured phrases, which meant an unrelated message that happened
 * to equal one (e.g. a bare "yes" answering someone else's question) could accidentally seal a real,
 * possibly high-stakes oath. There is deliberately no phrase configuration left to author. */
public record CeremonyTemplateDefinition(String id, String displayName, String itemMaterialName,
                                          String itemDisplayName, List<String> dialogueLines,
                                          int promptTimeoutSeconds, boolean bloodOath,
                                          List<CeremonyClauseSpec> clauses) {
    public CeremonyTemplateDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(itemMaterialName, "itemMaterialName");
        Objects.requireNonNull(itemDisplayName, "itemDisplayName");
        Objects.requireNonNull(dialogueLines, "dialogueLines");
        Objects.requireNonNull(clauses, "clauses");
        dialogueLines = List.copyOf(dialogueLines);
        clauses = List.copyOf(clauses);
        if (promptTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("promptTimeoutSeconds must be positive");
        }
    }

    /** {@code true} if sealing this ceremony does anything beyond an RP-only {@code CustomFlagSpec} -
     * a transfer, tribute, mob-kill obligation, or diplomacy change. Drives the item glint and bound
     * trigger-block particle aura ({@code bukkit.CeremonyItems}, {@code OathboundPlugin}) that warn a
     * player this isn't a zero-stakes pledge like {@code welcome-pact} before they commit to it. */
    public boolean hasRealStakes() {
        return clauses.stream().anyMatch(spec -> !(spec instanceof CeremonyClauseSpec.CustomFlagSpec));
    }
}
