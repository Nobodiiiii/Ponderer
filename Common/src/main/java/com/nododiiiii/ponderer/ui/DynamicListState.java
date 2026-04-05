package com.nododiiiii.ponderer.ui;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class DynamicListState<T> implements SnapshotParticipant {

    public interface ItemCodec<T> {
        void snapshot(String prefix, T item, Map<String, String> snapshot);

        @Nullable
        T restore(String prefix, Map<String, String> snapshot);
    }

    private final String snapshotPrefix;
    private final ItemCodec<T> codec;
    private final Supplier<T> emptyItemFactory;
    private final int minimumSize;
    private final List<T> items = new ArrayList<>();

    public DynamicListState(String snapshotPrefix, ItemCodec<T> codec, Supplier<T> emptyItemFactory, int minimumSize) {
        this.snapshotPrefix = snapshotPrefix;
        this.codec = codec;
        this.emptyItemFactory = emptyItemFactory;
        this.minimumSize = Math.max(0, minimumSize);
        ensureMinimumSize();
    }

    public List<T> items() {
        return Collections.unmodifiableList(items);
    }

    public int size() {
        return items.size();
    }

    public T get(int index) {
        return items.get(index);
    }

    public void set(int index, T value) {
        if (index >= 0 && index < items.size()) {
            items.set(index, value);
        }
    }

    public void add(T value) {
        items.add(value);
    }

    public void addEmpty() {
        items.add(emptyItemFactory.get());
    }

    public void remove(int index) {
        if (index >= 0 && index < items.size()) {
            items.remove(index);
        }
        ensureMinimumSize();
    }

    public void clear() {
        items.clear();
        ensureMinimumSize();
    }

    public void replaceAll(Collection<T> values) {
        items.clear();
        if (values != null) {
            items.addAll(values);
        }
        ensureMinimumSize();
    }

    @Override
    public void snapshot(Map<String, String> snapshot) {
        snapshot.put(snapshotPrefix + "_count", String.valueOf(items.size()));
        for (int i = 0; i < items.size(); i++) {
            codec.snapshot(snapshotPrefix + "_" + i, items.get(i), snapshot);
        }
    }

    @Override
    public void restore(Map<String, String> snapshot) {
        String rawCount = snapshot.get(snapshotPrefix + "_count");
        if (rawCount == null) {
            return;
        }
        int count;
        try {
            count = Integer.parseInt(rawCount);
        } catch (NumberFormatException ignored) {
            return;
        }
        items.clear();
        for (int i = 0; i < count; i++) {
            T value = codec.restore(snapshotPrefix + "_" + i, snapshot);
            if (value != null) {
                items.add(value);
            }
        }
        ensureMinimumSize();
    }

    protected void ensureMinimumSize() {
        while (items.size() < minimumSize) {
            items.add(emptyItemFactory.get());
        }
    }
}
