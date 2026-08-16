package com.google.gmail.philbgarner.oathbound.villager;

import org.bukkit.entity.Villager;

/** The vanilla {@link Villager.Profession} values that actually trade, each backing its own
 * {@code /oathbound-&lt;role&gt;} install command and its own fixed buy/sell list in config.yml.
 * {@code NONE} and {@code NITWIT} are deliberately excluded - neither trades in vanilla either. */
public enum NpcRole {
    ARMORER("armorer", Villager.Profession.ARMORER, "Armorer"),
    BUTCHER("butcher", Villager.Profession.BUTCHER, "Butcher"),
    CARTOGRAPHER("cartographer", Villager.Profession.CARTOGRAPHER, "Cartographer"),
    CLERIC("cleric", Villager.Profession.CLERIC, "Cleric"),
    FARMER("farmer", Villager.Profession.FARMER, "Farmer"),
    FISHERMAN("fisherman", Villager.Profession.FISHERMAN, "Fisherman"),
    FLETCHER("fletcher", Villager.Profession.FLETCHER, "Fletcher"),
    LEATHERWORKER("leatherworker", Villager.Profession.LEATHERWORKER, "Leatherworker"),
    LIBRARIAN("librarian", Villager.Profession.LIBRARIAN, "Librarian"),
    MASON("mason", Villager.Profession.MASON, "Mason"),
    SHEPHERD("shepherd", Villager.Profession.SHEPHERD, "Shepherd"),
    TOOLSMITH("toolsmith", Villager.Profession.TOOLSMITH, "Toolsmith"),
    WEAPONSMITH("weaponsmith", Villager.Profession.WEAPONSMITH, "Weaponsmith");

    private final String configKey;
    private final Villager.Profession profession;
    private final String displayName;

    NpcRole(String configKey, Villager.Profession profession, String displayName) {
        this.configKey = configKey;
        this.profession = profession;
        this.displayName = displayName;
    }

    public String configKey() {
        return configKey;
    }

    public Villager.Profession profession() {
        return profession;
    }

    public String displayName() {
        return displayName;
    }

    public String commandName() {
        return "oathbound-" + configKey;
    }
}
