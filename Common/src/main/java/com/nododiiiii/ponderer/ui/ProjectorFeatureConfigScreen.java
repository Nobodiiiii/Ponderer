package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.network.ProjectorFeatureConfigRequestPayload;
import com.nododiiiii.ponderer.network.ProjectorFeatureConfigResponsePayload;
import com.nododiiiii.ponderer.network.ProjectorFeatureConfigUpdatePayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import com.nododiiiii.ponderer.ui.catnip.LocalizedDoubleConfigEntry;
import net.createmod.catnip.config.ui.ConfigHelper;
import net.createmod.catnip.config.ui.ConfigScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.common.ModConfigSpec;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProjectorFeatureConfigScreen extends AbstractStatefulDeclarativeFormScreen {

    private static final String ENABLE_PROJECTOR_KEY = "enable_projector";
    private static final String MINIATURE_TEXT_SCALE_PATH = pathOf(Config.PROJECTOR_MINIATURE_TEXT_SCALE);
    private static final String LIFE_SIZE_TEXT_SCALE_PATH = pathOf(Config.PROJECTOR_LIFE_SIZE_TEXT_SCALE);

    private boolean enableProjector;
    private boolean serverEnableProjector;
    private boolean serverStateRequested;
    private boolean serverStateLoaded;
    private boolean waitingForServerUpdate;
    private boolean viewerCanManage;

    public ProjectorFeatureConfigScreen(Screen parent) {
        super(parent,
            "ponderer.ui.scope.projector",
            "ponderer.ui.mod_config.projector.title",
            UILayoutConstants.EDITOR_LIST_W);
        ConfigScreen.modID = Ponderer.MODID;
        ConfigHelper.changes.clear();
        this.enableProjector = readEnableProjector();
        this.serverEnableProjector = this.enableProjector;
    }

    @Override
    protected void init() {
        ConfigScreen.modID = Ponderer.MODID;
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
        entries.add(doubleConfigEntry(
            "ponderer.ui.mod_config.projector_miniature_text_scale",
            "ponderer.ui.mod_config.projector_miniature_text_scale.tooltip",
            Config.PROJECTOR_MINIATURE_TEXT_SCALE));
        entries.add(doubleConfigEntry(
            "ponderer.ui.mod_config.projector_life_size_text_scale",
            "ponderer.ui.mod_config.projector_life_size_text_scale.tooltip",
            Config.PROJECTOR_LIFE_SIZE_TEXT_SCALE));
    }

    @Override
    protected boolean saveEdits() {
        clearStatusMessages();

        saveClientConfigChanges();

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
        snapshot.put(MINIATURE_TEXT_SCALE_PATH, formatScale(
            ConfigHelper.getValue(MINIATURE_TEXT_SCALE_PATH, Config.PROJECTOR_MINIATURE_TEXT_SCALE)));
        snapshot.put(LIFE_SIZE_TEXT_SCALE_PATH, formatScale(
            ConfigHelper.getValue(LIFE_SIZE_TEXT_SCALE_PATH, Config.PROJECTOR_LIFE_SIZE_TEXT_SCALE)));
        return snapshot;
    }

    @Override
    protected void prepareSnapshotForBuild(Map<String, String> snapshot) {
        restoreClientConfigSnapshot(snapshot);
    }

    @Override
    protected void restoreSnapshot(Map<String, String> snapshot) {
        enableProjector = Boolean.parseBoolean(snapshot.getOrDefault(
            ENABLE_PROJECTOR_KEY,
            String.valueOf(readEnableProjector())));
        restoreClientConfigSnapshot(snapshot);
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
        baseline.put(MINIATURE_TEXT_SCALE_PATH, formatScale(Config.PROJECTOR_MINIATURE_TEXT_SCALE.get()));
        baseline.put(LIFE_SIZE_TEXT_SCALE_PATH, formatScale(Config.PROJECTOR_LIFE_SIZE_TEXT_SCALE.get()));
        if (enableProjector != null) {
            baseline.put(ENABLE_PROJECTOR_KEY, String.valueOf(enableProjector));
        }
        restoreBaselineState(baseline);
    }

    private DeclarativeFormEntry doubleConfigEntry(String labelKey, String tooltipKey,
                                                   ModConfigSpec.DoubleValue value) {
        return screen -> screen.appendBuiltEntry(new LocalizedDoubleConfigEntry(
            labelKey,
            tooltipKey,
            value,
            value.getSpec()));
    }

    private void saveClientConfigChanges() {
        Config.PROJECTOR_MINIATURE_TEXT_SCALE.set(
            ConfigHelper.getValue(MINIATURE_TEXT_SCALE_PATH, Config.PROJECTOR_MINIATURE_TEXT_SCALE));
        Config.PROJECTOR_LIFE_SIZE_TEXT_SCALE.set(
            ConfigHelper.getValue(LIFE_SIZE_TEXT_SCALE_PATH, Config.PROJECTOR_LIFE_SIZE_TEXT_SCALE));
        ConfigHelper.changes.clear();
    }

    private void restoreClientConfigSnapshot(Map<String, String> snapshot) {
        restoreDoubleConfigValue(snapshot, MINIATURE_TEXT_SCALE_PATH, Config.PROJECTOR_MINIATURE_TEXT_SCALE);
        restoreDoubleConfigValue(snapshot, LIFE_SIZE_TEXT_SCALE_PATH, Config.PROJECTOR_LIFE_SIZE_TEXT_SCALE);
    }

    private void restoreDoubleConfigValue(Map<String, String> snapshot, String path,
                                          ModConfigSpec.DoubleValue value) {
        String rawValue = snapshot.get(path);
        if (rawValue == null) {
            ConfigHelper.changes.remove(path);
            return;
        }

        try {
            ConfigHelper.setValue(path, value, Double.parseDouble(rawValue), null);
        } catch (NumberFormatException ignored) {
            ConfigHelper.changes.remove(path);
        }
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

    private static String formatScale(double value) {
        return Double.toString(value);
    }

    private static String pathOf(ModConfigSpec.ConfigValue<?> value) {
        return String.join(".", value.getPath());
    }
}
