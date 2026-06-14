package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.network.ProjectorFeatureConfigRequestPayload;
import com.nododiiiii.ponderer.network.ProjectorFeatureConfigResponsePayload;
import com.nododiiiii.ponderer.network.ProjectorFeatureConfigUpdatePayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProjectorFeatureConfigScreen extends AbstractStatefulDeclarativeFormScreen {

    private static final String ENABLE_PROJECTOR_KEY = "enable_projector";

    private boolean enableProjector;
    private boolean serverEnableProjector;
    private boolean serverStateRequested;
    private boolean serverStateLoaded;
    private boolean waitingForServerUpdate;
    private boolean viewerCanManage;

    public ProjectorFeatureConfigScreen(Screen parent) {
        super(parent,
            "ponderer.ui.scope.projector",
            "ponderer.ui.function_page.projector.title",
            UILayoutConstants.EDITOR_LIST_W);
        this.enableProjector = readEnableProjector();
        this.serverEnableProjector = this.enableProjector;
    }

    @Override
    protected void init() {
        super.init();
        requestServerStateIfNeeded();
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.toggle(
            "ponderer.ui.function_page.projector.enable",
            "ponderer.ui.function_page.projector.enable.tooltip",
            () -> enableProjector,
            this::toggleEnableProjector));
    }

    @Override
    protected boolean saveEdits() {
        clearStatusMessages();

        if (!isEnableProjectorDirty()) {
            return true;
        }
        if (!serverStateLoaded) {
            requestServerStateIfNeeded();
            setErrorMessage(UIText.of("ponderer.ui.function_page.projector.wait_server"));
            return false;
        }
        if (!viewerCanManage) {
            enableProjector = serverEnableProjector;
            setErrorMessage(UIText.of("ponderer.ui.function_page.projector.admin_required"));
            rebuildListPreservingScroll();
            return false;
        }

        waitingForServerUpdate = true;
        setInfoMessage(UIText.of("ponderer.ui.function_page.projector.saving"));
        PondererServices.NETWORK.sendToServer(new ProjectorFeatureConfigUpdatePayload(enableProjector));
        rebuildListPreservingScroll();
        return false;
    }

    @Override
    protected boolean isSaveButtonActive() {
        return !waitingForServerUpdate && hasUnsavedChanges();
    }

    public void receiveServerState(ProjectorFeatureConfigResponsePayload payload) {
        waitingForServerUpdate = false;
        boolean firstLoad = !serverStateLoaded;
        serverStateLoaded = true;
        viewerCanManage = payload.canManage();
        serverEnableProjector = payload.enableProjector();
        enableProjector = payload.enableProjector();
        writeLocalEnableProjector(payload.enableProjector());
        updateBaseline(payload.enableProjector());

        String messageKey = payload.messageKey();
        if (messageKey != null && !messageKey.isBlank()) {
            if (payload.error()) {
                setErrorMessage(UIText.of(messageKey));
            } else {
                setInfoMessage(UIText.of(messageKey));
            }
        } else if (firstLoad) {
            setInfoMessage(UIText.of("ponderer.ui.function_page.projector.loaded"));
        }

        rebuildListPreservingScroll();
    }

    @Override
    protected Map<String, String> snapshotState() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put(ENABLE_PROJECTOR_KEY, String.valueOf(enableProjector));
        return snapshot;
    }

    @Override
    protected void restoreSnapshot(Map<String, String> snapshot) {
        enableProjector = Boolean.parseBoolean(snapshot.getOrDefault(
            ENABLE_PROJECTOR_KEY,
            String.valueOf(readEnableProjector())));
    }

    private void toggleEnableProjector() {
        if (waitingForServerUpdate) {
            setInfoMessage(UIText.of("ponderer.ui.function_page.projector.saving"));
            return;
        }
        if (!serverStateLoaded) {
            requestServerStateIfNeeded();
            setErrorMessage(UIText.of("ponderer.ui.function_page.projector.wait_server"));
            return;
        }
        if (!viewerCanManage) {
            enableProjector = serverEnableProjector;
            setErrorMessage(UIText.of("ponderer.ui.function_page.projector.admin_required"));
            rebuildListPreservingScroll();
            return;
        }

        enableProjector = !enableProjector;
        clearStatusMessages();
    }

    private void requestServerStateIfNeeded() {
        if (serverStateRequested || serverStateLoaded) {
            return;
        }
        serverStateRequested = true;
        setInfoMessage(UIText.of("ponderer.ui.function_page.projector.loading"));
        PondererServices.NETWORK.sendToServer(new ProjectorFeatureConfigRequestPayload());
    }

    private boolean isEnableProjectorDirty() {
        String baselineValue = isBaselineCaptured() ? baselineStateSnapshot().get(ENABLE_PROJECTOR_KEY) : null;
        boolean baseline = baselineValue == null ? serverEnableProjector : Boolean.parseBoolean(baselineValue);
        return enableProjector != baseline;
    }

    private void updateBaseline(@Nullable Boolean enableProjector) {
        Map<String, String> baseline = new LinkedHashMap<>();
        if (isBaselineCaptured()) {
            baseline.putAll(baselineStateSnapshot());
        } else {
            baseline.putAll(snapshotState());
        }
        if (enableProjector != null) {
            baseline.put(ENABLE_PROJECTOR_KEY, String.valueOf(enableProjector));
        }
        restoreBaselineState(baseline);
    }

    private static void writeLocalEnableProjector(boolean value) {
        try {
            Config.ENABLE_PROJECTOR.set(value);
        } catch (Exception ignored) {
        }
    }

    private static boolean readEnableProjector() {
        try {
            return Config.ENABLE_PROJECTOR.get();
        } catch (Exception e) {
            return false;
        }
    }
}
