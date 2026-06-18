package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.network.BlueprintConfigRequestPayload;
import com.nododiiiii.ponderer.network.BlueprintConfigResponsePayload;
import com.nododiiiii.ponderer.network.BlueprintConfigUpdatePayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BlueprintItemConfigScreen extends AbstractJeiAwareFormScreen {

    private static final String ENABLE_BUILTIN_KEY = "enable_builtin";
    private static final String CARRIER_ITEM_KEY = "carrier_item";

    private boolean enableBuiltinItem;
    private boolean serverEnableBuiltinItem;
    private boolean serverStateRequested;
    private boolean serverStateLoaded;
    private boolean waitingForServerUpdate;
    private boolean viewerCanManage;
    private String carrierItem;

    public BlueprintItemConfigScreen(Screen parent) {
        super(parent,
            "ponderer.ui.scope.blueprint",
            "ponderer.ui.mod_config.blueprint.title",
            UILayoutConstants.EDITOR_LIST_W,
            JeiCompat::setActiveScreen);
        this.enableBuiltinItem = readEnableBuiltinItem();
        this.serverEnableBuiltinItem = this.enableBuiltinItem;
        this.carrierItem = readCarrierItem();
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
            "ponderer.ui.function_page.blueprint_item.use_builtin",
            "ponderer.ui.function_page.blueprint_item.use_builtin.tooltip",
            () -> enableBuiltinItem,
            this::toggleEnableBuiltinItem));
        entries.add(FieldSpecs.sectionHeader(() -> UIText.of("ponderer.ui.scope.client")));
        entries.add(FieldSpecs.text(
            FieldBindings.transientString(
                () -> carrierItem,
                value -> carrierItem = value == null ? "" : value),
            "ponderer.ui.function_page.blueprint_item.carrier",
            "ponderer.ui.function_page.blueprint_item.carrier.tooltip",
            "ponderer.ui.function_page.blueprint_item.carrier.hint",
            -1,
            FieldDecorators.jei(IdFieldMode.ITEM)));
    }

    @Override
    protected boolean saveEdits() {
        clearStatusMessages();

        String normalizedCarrier = carrierItem == null ? "" : carrierItem.trim();
        if (normalizedCarrier.isEmpty()) {
            setErrorMessage(UIText.of("ponderer.ui.error.required_field",
                UIText.of("ponderer.ui.function_page.blueprint_item.carrier")));
            return false;
        }
        if (ResourceLocation.tryParse(normalizedCarrier) == null) {
            setErrorMessage(UIText.of("ponderer.ui.create_item_entity.error.invalid_id"));
            return false;
        }

        deactivateJei();

        Config.BLUEPRINT_CARRIER_ITEM.set(normalizedCarrier);
        carrierItem = normalizedCarrier;
        updateBaseline(null, normalizedCarrier);

        if (isEnableBuiltinDirty()) {
            if (!serverStateLoaded) {
                requestServerStateIfNeeded();
                setErrorMessage(UIText.of("ponderer.ui.function_page.blueprint_item.wait_server"));
                return false;
            }
            if (!viewerCanManage) {
                enableBuiltinItem = serverEnableBuiltinItem;
                setErrorMessage(UIText.of("ponderer.ui.function_page.blueprint_item.admin_required"));
                rebuildListPreservingScroll();
                return false;
            }

            waitingForServerUpdate = true;
            setInfoMessage(UIText.of("ponderer.ui.function_page.blueprint_item.saving"));
            PondererServices.NETWORK.sendToServer(new BlueprintConfigUpdatePayload(enableBuiltinItem));
            rebuildListPreservingScroll();
            return false;
        }

        setInfoMessage(UIText.of("ponderer.ui.function_page.blueprint_item.set", normalizedCarrier));
        return true;
    }

    @Override
    protected boolean isSaveButtonActive() {
        return !waitingForServerUpdate && hasUnsavedChanges();
    }

    public void receiveServerState(BlueprintConfigResponsePayload payload) {
        waitingForServerUpdate = false;
        boolean firstLoad = !serverStateLoaded;
        serverStateLoaded = true;
        viewerCanManage = payload.canManage();
        serverEnableBuiltinItem = payload.enableBuiltinItem();
        enableBuiltinItem = payload.enableBuiltinItem();
        writeLocalEnableBuiltinItem(payload.enableBuiltinItem());
        updateBaseline(payload.enableBuiltinItem(), null);

        String messageKey = payload.messageKey();
        if (messageKey != null && !messageKey.isBlank()) {
            if (payload.error()) {
                setErrorMessage(UIText.of(messageKey));
            } else {
                setInfoMessage(UIText.of(messageKey));
            }
        } else if (firstLoad) {
            setInfoMessage(UIText.of("ponderer.ui.function_page.blueprint_item.loaded"));
        }

        rebuildListPreservingScroll();
    }

    @Override
    protected Map<String, String> snapshotState() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put(ENABLE_BUILTIN_KEY, String.valueOf(enableBuiltinItem));
        snapshot.put(CARRIER_ITEM_KEY, carrierItem == null ? "" : carrierItem);
        return snapshot;
    }

    @Override
    protected void restoreSnapshot(Map<String, String> snapshot) {
        enableBuiltinItem = Boolean.parseBoolean(snapshot.getOrDefault(ENABLE_BUILTIN_KEY, String.valueOf(readEnableBuiltinItem())));
        carrierItem = snapshot.getOrDefault(CARRIER_ITEM_KEY, readCarrierItem());
    }

    private void toggleEnableBuiltinItem() {
        if (waitingForServerUpdate) {
            setInfoMessage(UIText.of("ponderer.ui.function_page.blueprint_item.saving"));
            return;
        }
        if (!serverStateLoaded) {
            requestServerStateIfNeeded();
            setErrorMessage(UIText.of("ponderer.ui.function_page.blueprint_item.wait_server"));
            return;
        }
        if (!viewerCanManage) {
            enableBuiltinItem = serverEnableBuiltinItem;
            setErrorMessage(UIText.of("ponderer.ui.function_page.blueprint_item.admin_required"));
            rebuildListPreservingScroll();
            return;
        }

        enableBuiltinItem = !enableBuiltinItem;
        clearStatusMessages();
    }

    private void requestServerStateIfNeeded() {
        if (serverStateRequested || serverStateLoaded) {
            return;
        }
        serverStateRequested = true;
        setInfoMessage(UIText.of("ponderer.ui.function_page.blueprint_item.loading"));
        PondererServices.NETWORK.sendToServer(new BlueprintConfigRequestPayload());
    }

    private boolean isEnableBuiltinDirty() {
        String baselineValue = isBaselineCaptured() ? baselineStateSnapshot().get(ENABLE_BUILTIN_KEY) : null;
        boolean baseline = baselineValue == null ? serverEnableBuiltinItem : Boolean.parseBoolean(baselineValue);
        return enableBuiltinItem != baseline;
    }

    private void updateBaseline(@Nullable Boolean enableBuiltin, @Nullable String carrier) {
        Map<String, String> baseline = new LinkedHashMap<>();
        if (isBaselineCaptured()) {
            baseline.putAll(baselineStateSnapshot());
        } else {
            baseline.putAll(snapshotState());
        }
        if (enableBuiltin != null) {
            baseline.put(ENABLE_BUILTIN_KEY, String.valueOf(enableBuiltin));
        }
        if (carrier != null) {
            baseline.put(CARRIER_ITEM_KEY, carrier);
        }
        restoreBaselineState(baseline);
    }

    private static void writeLocalEnableBuiltinItem(boolean value) {
        try {
            Config.ENABLE_BLUEPRINT_ITEM.set(value);
        } catch (Exception ignored) {
        }
    }

    private static boolean readEnableBuiltinItem() {
        try {
            return Config.ENABLE_BLUEPRINT_ITEM.get();
        } catch (Exception e) {
            return false;
        }
    }

    private static String readCarrierItem() {
        try {
            String value = Config.BLUEPRINT_CARRIER_ITEM.get();
            return value == null || value.isBlank() ? "minecraft:paper" : value;
        } catch (Exception e) {
            return "minecraft:paper";
        }
    }
}
