package com.google.gmail.philbgarner.oathbound.group;

/** Either a specific player or a {@link ProtectionGroup}, wherever the design allows either to hold or receive something. */
public sealed interface EntityRef permits PlayerRef, ProtectionGroupRef {
}
