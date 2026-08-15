package com.google.gmail.philbgarner.oathbound.persistence.json;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

public final class SimpleStringTypeAdapters {
    private SimpleStringTypeAdapters() {
    }

    public static final TypeAdapter<UUID> UUID_ADAPTER = of(UUID::toString, UUID::fromString);
    public static final TypeAdapter<Instant> INSTANT_ADAPTER = of(Instant::toString, Instant::parse);
    public static final TypeAdapter<Duration> DURATION_ADAPTER = of(Duration::toString, Duration::parse);

    private static <T> TypeAdapter<T> of(Function<T, String> toString, Function<String, T> fromString) {
        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                if (value == null) {
                    out.nullValue();
                } else {
                    out.value(toString.apply(value));
                }
            }

            @Override
            public T read(JsonReader in) throws IOException {
                if (in.peek() == JsonToken.NULL) {
                    in.nextNull();
                    return null;
                }
                return fromString.apply(in.nextString());
            }
        };
    }
}
