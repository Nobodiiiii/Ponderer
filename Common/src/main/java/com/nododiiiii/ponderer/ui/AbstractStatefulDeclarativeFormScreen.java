package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeFormScreen;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Declarative form screen with reusable snapshot/baseline management.
 * Screens with dynamic entries can restore state before rebuild via
 * {@link #prepareSnapshotForBuild(Map)} and then push values back into widgets via
 * {@link #restoreSnapshot(Map)} after the rebuild completes.
 */
public abstract class AbstractStatefulDeclarativeFormScreen extends AbstractDeclarativeFormScreen {

    private static final String BASELINE_COUNT_KEY = "__ponderer_baseline_count";
    private static final String BASELINE_KEY_PREFIX = "__ponderer_baseline_key_";
    private static final String BASELINE_VALUE_PREFIX = "__ponderer_baseline_value_";

    private final FormState formState = new FormState(this::snapshotState, this::restoreSnapshot);
    private boolean baselineCaptured = false;

    protected AbstractStatefulDeclarativeFormScreen(@Nullable Screen parent, String scopeKey, String titleKey) {
        super(parent, scopeKey, titleKey);
    }

    protected AbstractStatefulDeclarativeFormScreen(@Nullable Screen parent, String scopeKey, String titleKey,
                                                    int preferredListWidth) {
        super(parent, scopeKey, titleKey, preferredListWidth);
    }

    @Override
    protected void init() {
        super.init();
        if (shouldAutoCaptureBaselineOnInit() && !baselineCaptured) {
            captureBaselineState();
        }
    }

    @Override
    protected final boolean hasUnsavedChanges() {
        return baselineCaptured && formState.hasUnsavedChanges();
    }

    @Override
    protected final int getUnsavedChangeCount() {
        return baselineCaptured ? formState.dirtyCount() : 0;
    }

    @Override
    protected void discardEdits() {
        clearStatusMessages();
        if (!baselineCaptured) {
            return;
        }
        restoreStateWithRebuild(formState.baselineSnapshot());
    }

    protected boolean shouldAutoCaptureBaselineOnInit() {
        return true;
    }

    protected final void captureBaselineState() {
        formState.captureBaseline();
        baselineCaptured = true;
    }

    protected final void markStateSaved() {
        captureBaselineState();
    }

    protected final void restoreBaselineState(Map<String, String> snapshot) {
        formState.setBaselineSnapshot(snapshot);
        baselineCaptured = true;
    }

    protected final boolean isBaselineCaptured() {
        return baselineCaptured;
    }

    protected final Map<String, String> baselineStateSnapshot() {
        return formState.baselineSnapshot();
    }

    protected final void rebuildFormPreservingState() {
        restoreStateWithRebuild(snapshotState());
    }

    protected void prepareSnapshotForBuild(Map<String, String> snapshot) {
    }

    protected void afterSnapshotRestored(Map<String, String> snapshot) {
    }

    protected abstract Map<String, String> snapshotState();

    protected abstract void restoreSnapshot(Map<String, String> snapshot);

    protected final void appendBaselineSnapshotMetadata(Map<String, String> snapshot) {
        if (!baselineCaptured) {
            return;
        }

        Map<String, String> baseline = baselineStateSnapshot();
        snapshot.put(BASELINE_COUNT_KEY, String.valueOf(baseline.size()));

        int index = 0;
        for (Map.Entry<String, String> entry : baseline.entrySet()) {
            snapshot.put(BASELINE_KEY_PREFIX + index, entry.getKey());
            snapshot.put(BASELINE_VALUE_PREFIX + index, entry.getValue());
            index++;
        }
    }

    @Nullable
    protected final Map<String, String> extractBaselineSnapshotMetadata(Map<String, String> snapshot) {
        String rawCount = snapshot.remove(BASELINE_COUNT_KEY);
        if (rawCount == null) {
            return null;
        }

        int count;
        try {
            count = Integer.parseInt(rawCount);
        } catch (NumberFormatException ignored) {
            return null;
        }

        Map<String, String> baseline = new java.util.LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String key = snapshot.remove(BASELINE_KEY_PREFIX + i);
            String value = snapshot.remove(BASELINE_VALUE_PREFIX + i);
            if (key != null && value != null) {
                baseline.put(key, value);
            }
        }
        return baseline;
    }

    private void restoreStateWithRebuild(Map<String, String> snapshot) {
        prepareSnapshotForBuild(snapshot);
        rebuildListPreservingScroll();
        restoreSnapshot(snapshot);
        afterSnapshotRestored(snapshot);
    }
}
