package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigHelper;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.neoforged.neoforge.common.ModConfigSpec;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;

public class LocalizedDoubleConfigEntry extends PlainTextListEntry {

    private final BoxWidget resetButton;
    private final BooleanSupplier resetAvailable;

    public LocalizedDoubleConfigEntry(String labelKey, @Nullable String tooltipKey,
                                      ModConfigSpec.DoubleValue value, ModConfigSpec.ValueSpec spec) {
        this(labelKey, tooltipKey, value, spec, ConfigEntrySupport.metadataOf(value, spec));
    }

    private LocalizedDoubleConfigEntry(String labelKey, @Nullable String tooltipKey,
                                       ModConfigSpec.DoubleValue value, ModConfigSpec.ValueSpec spec,
                                       ConfigEntrySupport.Metadata metadata) {
        super(labelKey, tooltipKey, null, String.valueOf(ConfigEntrySupport.currentValue(metadata, value)), raw -> {});
        this.path = metadata.path();
        this.annotations.putAll(metadata.annotations());
        if (metadata.unit() != null && !metadata.unit().isBlank()) {
            setUnitText(metadata::unit);
        }
        field().setTextColor(UIRenderHelper.COLOR_TEXT.getFirst().getRGB());
        field().setResponder(rawValue -> {
            try {
                double parsed = Double.parseDouble(rawValue);
                if (!spec.test(parsed)) {
                    throw new IllegalArgumentException();
                }
                field().setTextColor(UIRenderHelper.COLOR_TEXT.getFirst().getRGB());
                ConfigHelper.setValue(metadata.path(), value, parsed, metadata.annotations());
            } catch (IllegalArgumentException ignored) {
                field().setTextColor(AbstractSimiWidget.COLOR_FAIL.getFirst().getRGB());
            }
        });
        resetAvailable = () -> !spec.getDefault().equals(ConfigEntrySupport.currentValue(metadata, value));
        resetButton = addTrailingButton(16,
            () -> field().setValue(String.valueOf(spec.getDefault())), () -> "", () -> 0xFFFFFF, null);
        PonderIconStencils.attach(resetButton, PonderIconStencils.centered(PonderGuiTextures.ICON_CONFIG_RESET));
        resetButton.active = resetAvailable.getAsBoolean();
    }

    @Override
    public void tick() {
        resetButton.active = resetAvailable.getAsBoolean();
        super.tick();
    }
}
