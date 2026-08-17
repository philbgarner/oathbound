package com.google.gmail.philbgarner.oathbound.ceremony;

import java.util.List;
import java.util.Objects;

/** Admin-authored, declared in {@code config.yml}'s {@code ceremony-templates} list - not persisted,
 * since it's static configuration rather than runtime player data (same reasoning as
 * {@code bounty.PveContractDefinition}). {@code itemMaterialName} is a plain
 * {@code org.bukkit.Material} name and {@code CeremonyClauseSpec.MobKillSpec.mobTypeName()} a plain
 * {@code org.bukkit.entity.EntityType} name, rather than the enums themselves, since this package is
 * Bukkit-free by design; Bukkit-glue code resolves the real types at the point of use. */
public record CeremonyTemplateDefinition(String id, String displayName, String itemMaterialName,
                                          String itemDisplayName, List<String> dialogueLines,
                                          List<String> confirmPhrases, List<String> declinePhrases,
                                          int promptTimeoutSeconds, boolean bloodOath,
                                          List<CeremonyClauseSpec> clauses) {
    public CeremonyTemplateDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(itemMaterialName, "itemMaterialName");
        Objects.requireNonNull(itemDisplayName, "itemDisplayName");
        Objects.requireNonNull(dialogueLines, "dialogueLines");
        Objects.requireNonNull(confirmPhrases, "confirmPhrases");
        Objects.requireNonNull(declinePhrases, "declinePhrases");
        Objects.requireNonNull(clauses, "clauses");
        dialogueLines = List.copyOf(dialogueLines);
        confirmPhrases = List.copyOf(confirmPhrases);
        declinePhrases = List.copyOf(declinePhrases);
        clauses = List.copyOf(clauses);
        if (confirmPhrases.isEmpty()) {
            throw new IllegalArgumentException("confirmPhrases must not be empty");
        }
        if (promptTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("promptTimeoutSeconds must be positive");
        }
    }
}
