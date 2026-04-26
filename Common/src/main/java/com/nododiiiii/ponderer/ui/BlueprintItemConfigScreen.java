package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry;
import net.createmod.catnip.net.ServerboundConfigPacket;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BlueprintItemConfigScreen extends AbstractJeiAwareFormScreen {

    private static final String ENABLE_BUILTIN_KEY = "enable_builtin";
    private static final String CARRIER_ITEM_KEY = "carrier_item";

    private boolean enableBuiltinItem;
    private String carrierItem;

    public BlueprintItemConfigScreen(Screen parent) {
        super(parent,
            "ponderer.ui.scope.blueprint",
            "ponderer.ui.function_page.blueprint_item.title",
            UILayoutConstants.EDITOR_LIST_W,
            JeiCompat::setActiveScreen);
        this.enableBuiltinItem = readEnableBuiltinItem();
        this.carrierItem = readCarrierItem();
    }

    @Override
    protected void collectFormEntries(List<DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.toggle(
            FieldBindings.transientBool(
                () -> enableBuiltinItem,
                value -> enableBuiltinItem = value),
            "ponderer.ui.function_page.blueprint_item.use_builtin",
            "ponderer.ui.function_page.blueprint_item.use_builtin.tooltip"));
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

        Config.ENABLE_BLUEPRINT_ITEM.set(enableBuiltinItem);
        CatnipServices.NETWORK.sendToServer(new ServerboundConfigPacket<>(
            Ponderer.MODID,
            String.join(".", Config.ENABLE_BLUEPRINT_ITEM.getPath()),
            enableBuiltinItem));

        Config.BLUEPRINT_CARRIER_ITEM.set(normalizedCarrier);
        carrierItem = normalizedCarrier;

        markStateSaved();
        return true;
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
