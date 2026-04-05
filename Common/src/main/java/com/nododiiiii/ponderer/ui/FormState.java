package com.nododiiiii.ponderer.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class FormState {

    private final Supplier<Map<String, String>> snapshotSupplier;
    private final Consumer<Map<String, String>> restoreConsumer;
    private Map<String, String> baselineSnapshot = new LinkedHashMap<>();

    public FormState(Supplier<Map<String, String>> snapshotSupplier, Consumer<Map<String, String>> restoreConsumer) {
        this.snapshotSupplier = snapshotSupplier;
        this.restoreConsumer = restoreConsumer;
    }

    public void captureBaseline() {
        baselineSnapshot = snapshot();
    }

    public Map<String, String> snapshot() {
        return new LinkedHashMap<>(snapshotSupplier.get());
    }

    public void restore(Map<String, String> snapshot) {
        restoreConsumer.accept(new LinkedHashMap<>(snapshot));
    }

    public void restoreBaseline() {
        restore(baselineSnapshot);
    }

    public Map<String, String> baselineSnapshot() {
        return new LinkedHashMap<>(baselineSnapshot);
    }

    public void setBaselineSnapshot(Map<String, String> snapshot) {
        baselineSnapshot = new LinkedHashMap<>(snapshot);
    }

    public int dirtyCount() {
        return diffCount(baselineSnapshot, snapshot());
    }

    public boolean hasUnsavedChanges() {
        return dirtyCount() > 0;
    }

    public static Map<String, String> snapshotOf(Iterable<? extends SnapshotParticipant> participants) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (SnapshotParticipant participant : participants) {
            if (participant != null) {
                participant.snapshot(snapshot);
            }
        }
        return snapshot;
    }

    public static void restoreInto(Map<String, String> snapshot, Iterable<? extends SnapshotParticipant> participants) {
        for (SnapshotParticipant participant : participants) {
            if (participant != null) {
                participant.restore(snapshot);
            }
        }
    }

    public static int diffCount(Map<String, String> left, Map<String, String> right) {
        Set<String> keys = new java.util.LinkedHashSet<>(left.keySet());
        keys.addAll(right.keySet());

        int dirty = 0;
        for (String key : keys) {
            if (!java.util.Objects.equals(left.get(key), right.get(key))) {
                dirty++;
            }
        }
        return dirty;
    }
}
