package com.nododiiiii.ponderer.ui;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KeyValueListState extends DynamicListState<KeyValueListState.KeyValue> {

    public record KeyValue(String key, String value) {
    }

    public KeyValueListState(String snapshotPrefix, int minimumSize) {
        super(snapshotPrefix, new ItemCodec<>() {
            @Override
            public void snapshot(String prefix, KeyValue item, Map<String, String> snapshot) {
                snapshot.put(prefix + "_key", item.key());
                snapshot.put(prefix + "_val", item.value());
            }

            @Override
            public KeyValue restore(String prefix, Map<String, String> snapshot) {
                return new KeyValue(
                    snapshot.getOrDefault(prefix + "_key", ""),
                    snapshot.getOrDefault(prefix + "_val", ""));
            }
        }, () -> new KeyValue("", ""), minimumSize);
    }

    public void updateKey(int index, String key) {
        if (index >= 0 && index < size()) {
            KeyValue current = get(index);
            set(index, new KeyValue(key == null ? "" : key, current.value()));
        }
    }

    public void updateValue(int index, String value) {
        if (index >= 0 && index < size()) {
            KeyValue current = get(index);
            set(index, new KeyValue(current.key(), value == null ? "" : value));
        }
    }

    public void replaceFromMap(@Nullable Map<String, String> map) {
        List<KeyValue> values = new ArrayList<>();
        if (map != null) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                values.add(new KeyValue(entry.getKey(), entry.getValue()));
            }
        }
        replaceAll(values);
    }

    @Nullable
    public Map<String, String> toFilteredMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (KeyValue item : items()) {
            String key = item.key().trim();
            String value = item.value().trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                map.put(key, value);
            }
        }
        return map.isEmpty() ? null : map;
    }
}
