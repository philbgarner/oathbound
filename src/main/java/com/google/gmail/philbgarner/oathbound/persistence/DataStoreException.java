package com.google.gmail.philbgarner.oathbound.persistence;

public final class DataStoreException extends Exception {
    public DataStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataStoreException(String message) {
        super(message);
    }
}
