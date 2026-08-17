package com.google.gmail.philbgarner.oathbound.diplomacy;

/** {@code NEUTRAL} is never explicitly stored - it's what {@link DiplomacyService#currentState} returns
 * for a group pair with no recorded relation, the same way Honor defaults to 0 for an unrecorded player.
 * This is what distinguishes a "first contact" war declaration (no prior relation) from a "backstab" one
 * (breaking a recorded {@code PEACE} or {@code ALLIANCE}). */
public enum DiplomaticState {
    NEUTRAL,
    WAR,
    PEACE,
    ALLIANCE
}
