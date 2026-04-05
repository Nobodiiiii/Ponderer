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

    private void restoreStateWithRebuild(Map<String, String> snapshot) {
        prepareSnapshotForBuild(snapshot);
        rebuildEntries(currentListScroll());
        restoreSnapshot(snapshot);
        afterSnapshotRestored(snapshot);
    }
}
