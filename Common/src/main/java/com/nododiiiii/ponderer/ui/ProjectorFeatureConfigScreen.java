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
    private static final String MINIATURE_TEXT_SCALE_KEY = "miniature_text_scale";
    private static final String LIFE_SIZE_TEXT_SCALE_KEY = "life_size_text_scale";
    private static final double MIN_TEXT_SCALE = 0.5D;
    private static final double MAX_TEXT_SCALE = 5.0D;

    private boolean enableProjector;
    private boolean serverEnableProjector;
    private boolean serverStateRequested;
    private boolean serverStateLoaded;
    private boolean waitingForServerUpdate;
    private boolean viewerCanManage;
    private String miniatureTextScale;
    private String lifeSizeTextScale;

    public ProjectorFeatureConfigScreen(Screen parent) {
        super(parent,
            "ponderer.ui.scope.projector",
            "ponderer.ui.mod_config.projector.title",
            UILayoutConstants.EDITOR_LIST_W);
        this.enableProjector = readEnableProjector();
        this.serverEnableProjector = this.enableProjector;
        this.miniatureTextScale = formatScale(readMiniatureTextScale());
        this.lifeSizeTextScale = formatScale(readLifeSizeTextScale());
    }

    @Override
    protected void init() {
        super.init();
        requestServerStateIfNeeded();
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.sectionHeader(() -> UIText.of("ponderer.ui.scope.server")));
        entries.add(FieldSpecs.toggle(
            "ponderer.ui.function_page.projector.enable",
            "ponderer.ui.function_page.projector.enable.tooltip",
            () -> enableProjector,
            this::toggleEnableProjector));
        entries.add(FieldSpecs.sectionHeader(() -> UIText.of("ponderer.ui.scope.client")));
        entries.add(FieldSpecs.number(
            FieldBindings.transientString(
                () -> miniatureTextScale,
                value -> miniatureTextScale = value == null ? "" : value),
            "ponderer.ui.mod_config.projector_miniature_text_scale",
            "ponderer.ui.mod_config.projector_miniature_text_scale.tooltip",
            "2.5",
            70,
            null));
        entries.add(FieldSpecs.number(
            FieldBindings.transientString(
                () -> lifeSizeTextScale,
                value -> lifeSizeTextScale = value == null ? "" : value),
            "ponderer.ui.mod_config.projector_life_size_text_scale",
            "ponderer.ui.mod_config.projector_life_size_text_scale.tooltip",
            "1.25",
            70,
            null));
    }

    @Override
    protected boolean saveEdits() {
        clearStatusMessages();

        Double miniature = parseTextScale(miniatureTextScale, "ponderer.ui.mod_config.projector_miniature_text_scale");
        if (miniature == null) {
            return false;
        }
        Double lifeSize = parseTextScale(lifeSizeTextScale, "ponderer.ui.mod_config.projector_life_size_text_scale");
        if (lifeSize == null) {
            return false;
        }

        Config.PROJECTOR_MINIATURE_TEXT_SCALE.set(miniature);
        Config.PROJECTOR_LIFE_SIZE_TEXT_SCALE.set(lifeSize);
        miniatureTextScale = formatScale(miniature);
        lifeSizeTextScale = formatScale(lifeSize);

        if (!isEnableProjectorDirty()) {
            markStateSaved();
            setInfoMessage(UIText.of("ponderer.ui.mod_config.projector.saved"));
            return true;
        }

        updateBaseline(null);
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
        snapshot.put(MINIATURE_TEXT_SCALE_KEY, miniatureTextScale == null ? "" : miniatureTextScale);
        snapshot.put(LIFE_SIZE_TEXT_SCALE_KEY, lifeSizeTextScale == null ? "" : lifeSizeTextScale);
        return snapshot;
    }

    @Override
    protected void restoreSnapshot(Map<String, String> snapshot) {
        enableProjector = Boolean.parseBoolean(snapshot.getOrDefault(
            ENABLE_PROJECTOR_KEY,
            String.valueOf(readEnableProjector())));
        miniatureTextScale = snapshot.getOrDefault(
            MINIATURE_TEXT_SCALE_KEY,
            formatScale(readMiniatureTextScale()));
        lifeSizeTextScale = snapshot.getOrDefault(
            LIFE_SIZE_TEXT_SCALE_KEY,
            formatScale(readLifeSizeTextScale()));
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
        baseline.put(MINIATURE_TEXT_SCALE_KEY, miniatureTextScale == null ? "" : miniatureTextScale);
        baseline.put(LIFE_SIZE_TEXT_SCALE_KEY, lifeSizeTextScale == null ? "" : lifeSizeTextScale);
        if (enableProjector != null) {
            baseline.put(ENABLE_PROJECTOR_KEY, String.valueOf(enableProjector));
        }
        restoreBaselineState(baseline);
    }

    @Nullable
    private Double parseTextScale(String rawValue, String labelKey) {
        String normalized = rawValue == null ? "" : rawValue.trim();
        double value;
        try {
            value = Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            setErrorMessage(UIText.of("ponderer.ui.error.invalid_number", UIText.of(labelKey)));
            return null;
        }

        if (value < MIN_TEXT_SCALE || value > MAX_TEXT_SCALE) {
            setErrorMessage(UIText.of("ponderer.ui.mod_config.projector_text_scale.range", UIText.of(labelKey)));
            return null;
        }
        return value;
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

    private static double readMiniatureTextScale() {
        try {
            return Config.PROJECTOR_MINIATURE_TEXT_SCALE.get();
        } catch (Exception e) {
            return 2.5D;
        }
    }

    private static double readLifeSizeTextScale() {
        try {
            return Config.PROJECTOR_LIFE_SIZE_TEXT_SCALE.get();
        } catch (Exception e) {
            return 1.25D;
        }
    }

    private static String formatScale(double value) {
        return Double.toString(value);
    }
}
