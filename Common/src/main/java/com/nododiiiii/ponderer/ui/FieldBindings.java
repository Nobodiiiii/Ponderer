package com.nododiiiii.ponderer.ui;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class FieldBindings {

    private FieldBindings() {
    }

    public static FieldBinding<String> string(String snapshotKey, Supplier<String> getter, Consumer<String> setter) {
        return new SimpleFieldBinding<>(snapshotKey, getter, setter, Function.identity(), value -> value == null ? "" : value);
    }

    public static FieldBinding<String> transientString(Supplier<String> getter, Consumer<String> setter) {
        return new SimpleFieldBinding<>(null, getter, setter, Function.identity(), value -> value == null ? "" : value);
    }

    public static FieldBinding<Boolean> bool(String snapshotKey, BooleanSupplier getter, Consumer<Boolean> setter) {
        return new SimpleFieldBinding<>(
            snapshotKey,
            getter::getAsBoolean,
            setter,
            Boolean::parseBoolean,
            value -> String.valueOf(Boolean.TRUE.equals(value)));
    }

    public static FieldBinding<Boolean> transientBool(BooleanSupplier getter, Consumer<Boolean> setter) {
        return new SimpleFieldBinding<>(
            null,
            getter::getAsBoolean,
            setter,
            Boolean::parseBoolean,
            value -> String.valueOf(Boolean.TRUE.equals(value)));
    }

    public static FieldBinding<Integer> integer(String snapshotKey, IntSupplier getter, IntConsumer setter) {
        return new SimpleFieldBinding<>(
            snapshotKey,
            getter::getAsInt,
            value -> setter.accept(value == null ? 0 : value),
            raw -> {
                try {
                    return Integer.parseInt(raw);
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            },
            value -> String.valueOf(value == null ? 0 : value));
    }

    public static FieldBinding<Integer> transientInteger(IntSupplier getter, IntConsumer setter) {
        return new SimpleFieldBinding<>(
            null,
            getter::getAsInt,
            value -> setter.accept(value == null ? 0 : value),
            raw -> {
                try {
                    return Integer.parseInt(raw);
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            },
            value -> String.valueOf(value == null ? 0 : value));
    }

    private record SimpleFieldBinding<T>(
        @Nullable String snapshotKey,
        Supplier<T> getter,
        Consumer<T> setter,
        Function<String, T> deserializer,
        Function<T, String> serializer
    ) implements FieldBinding<T> {

        @Override
        public T get() {
            return getter.get();
        }

        @Override
        public void set(@Nullable T value) {
            setter.accept(value);
        }

        @Override
        public T deserialize(String raw) {
            return deserializer.apply(raw);
        }

        @Override
        public String serialize(@Nullable T value) {
            return serializer.apply(value);
        }
    }
}
