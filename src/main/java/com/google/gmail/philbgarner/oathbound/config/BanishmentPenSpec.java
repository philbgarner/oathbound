package com.google.gmail.philbgarner.oathbound.config;

/** Admin-configured, fixed destination for the End banishment pen. World is looked up by name at
 * teleport time (a config-time UUID would be meaningless - worlds don't have a stable UUID until they're
 * actually loaded), so this stays a plain name here rather than the {@code UUID worldId} shape used by
 * every persisted location record. */
public record BanishmentPenSpec(String worldName, double x, double y, double z, float yaw, float pitch) {
}
