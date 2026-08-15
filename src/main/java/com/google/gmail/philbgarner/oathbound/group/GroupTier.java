package com.google.gmail.philbgarner.oathbound.group;

/** Ordinal order matters: used directly for smallest-scope-first nesting/overlap resolution. */
public enum GroupTier {
    INDIVIDUAL,
    COMPANY,
    TOWN,
    REGION,
    KINGDOM
}
