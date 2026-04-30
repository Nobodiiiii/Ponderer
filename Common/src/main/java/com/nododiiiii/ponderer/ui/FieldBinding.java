package com.nododiiiii.ponderer.ui;

import javax.annotation.Nullable;
import java.util.Map;

public interface FieldBinding<T> extends SnapshotParticipant {

    T get();

    void set(@Nullable T value);

    @Nullable
    default String snapshotKey() {
        return null;
    }

    @Nullable
    T deserialize(String raw);

    @Nullable
    default String serialize(@Nullable T value) {
        return value == null ? null : String.valueOf(value);
    }

    default boolean supportsSnapshot() {
        return snapshotKey() != null;
    }

    @Override
    default void snapshot(Map<String, String> snapshot) {
        String key = snapshotKey();
        if (key == null) {
            return;
        }
        String serialized = serialize(get());
        if (serialized != null) {
            snapshot.put(key, serialized);
        }
    }

    @Override
    default void restore(Map<String, String> snapshot) {
        String key = snapshotKey();
        if (key != null && snapshot.containsKey(key)) {
            set(deserialize(snapshot.get(key)));
        }
    }
}
