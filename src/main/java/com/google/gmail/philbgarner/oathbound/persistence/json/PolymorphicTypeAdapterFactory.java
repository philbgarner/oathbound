package com.google.gmail.philbgarner.oathbound.persistence.json;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Gson has no built-in support for polymorphic sealed interfaces/records: without this, a field
 * declared as the interface type serializes as {@code {}} since the interface itself has no fields.
 * Wraps each value as {"type": "<tag>", "data": {...}} and dispatches on the tag to reconstruct the
 * correct concrete record.
 */
public final class PolymorphicTypeAdapterFactory<T> implements TypeAdapterFactory {
    private final Class<T> baseType;
    private final Map<String, Class<? extends T>> typesByTag;

    public PolymorphicTypeAdapterFactory(Class<T> baseType, Map<String, Class<? extends T>> typesByTag) {
        this.baseType = Objects.requireNonNull(baseType, "baseType");
        this.typesByTag = Map.copyOf(typesByTag);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> TypeAdapter<R> create(Gson gson, TypeToken<R> type) {
        if (!baseType.equals(type.getRawType())) {
            return null;
        }

        Map<String, TypeAdapter<?>> delegatesByTag = new HashMap<>();
        Map<Class<?>, String> tagsByType = new HashMap<>();
        typesByTag.forEach((tag, concreteType) -> {
            delegatesByTag.put(tag, gson.getAdapter(TypeToken.get(concreteType)));
            tagsByType.put(concreteType, tag);
        });
        TypeAdapter<JsonElement> jsonElementAdapter = gson.getAdapter(JsonElement.class);

        TypeAdapter<T> adapter = new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                if (value == null) {
                    out.nullValue();
                    return;
                }
                String tag = tagsByType.get(value.getClass());
                if (tag == null) {
                    throw new IllegalArgumentException("No type tag registered for " + value.getClass());
                }
                @SuppressWarnings("unchecked")
                TypeAdapter<T> delegate = (TypeAdapter<T>) delegatesByTag.get(tag);
                JsonObject wrapper = new JsonObject();
                wrapper.addProperty("type", tag);
                wrapper.add("data", delegate.toJsonTree(value));
                jsonElementAdapter.write(out, wrapper);
            }

            @Override
            public T read(JsonReader in) throws IOException {
                JsonElement element = jsonElementAdapter.read(in);
                JsonObject wrapper = element.getAsJsonObject();
                String tag = wrapper.get("type").getAsString();
                TypeAdapter<?> delegate = delegatesByTag.get(tag);
                if (delegate == null) {
                    throw new JsonParseException("Unknown type tag for " + baseType.getSimpleName() + ": " + tag);
                }
                return baseType.cast(delegate.fromJsonTree(wrapper.get("data")));
            }
        };

        return (TypeAdapter<R>) adapter;
    }
}
