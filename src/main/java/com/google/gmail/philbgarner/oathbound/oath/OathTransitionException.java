package com.google.gmail.philbgarner.oathbound.oath;

import java.util.UUID;

public final class OathTransitionException extends RuntimeException {
    public OathTransitionException(UUID oathId, OathState from, OathState to) {
        super("Illegal Oath transition for " + oathId + ": " + from + " -> " + to);
    }
}
