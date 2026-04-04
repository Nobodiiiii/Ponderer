package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigHelper;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;

public class LocalizedIntegerConfigEntry extends PlainTextListEntry {

    private final int textColor = UIRenderHelper.COLOR_TEXT.getFirst().getRGB();
    private final int failTextColor = AbstractSimiWidget.COLOR_FAIL.getFirst().getRGB();

    public LocalizedIntegerConfigEntry(String labelKey, @Nullable String hintKey, @Nullable String tooltipKey,
                                       ForgeConfigSpec.ConfigValue<Integer> value, ForgeConfigSpec.ValueSpec spec) {
        this(labelKey, hintKey, tooltipKey, value, spec, ConfigEntrySupport.metadataOf(value, spec));
    }

    private LocalizedIntegerConfigEntry(String labelKey, @Nullable String hintKey, @Nullable String tooltipKey,
                                        ForgeConfigSpec.ConfigValue<Integer> value, ForgeConfigSpec.ValueSpec spec,
                                        ConfigEntrySupport.Metadata metadata) {
        super(labelKey, tooltipKey, hintKey, formatValue(
            ConfigEntrySupport.currentValue(metadata, value),
            metadata.annotations()), newValue -> {
        });
        this.path = metadata.path();
        this.annotations.putAll(metadata.annotations());
        if (metadata.unit() != null && !metadata.unit().isBlank()) {
            setUnitText(metadata::unit);
        }
        field().setTextColor(textColor);
        field().setResponder(rawValue -> applyValue(rawValue, value, spec, metadata));
    }

    private void applyValue(String rawValue, ForgeConfigSpec.ConfigValue<Integer> value,
                            ForgeConfigSpec.ValueSpec spec, ConfigEntrySupport.Metadata metadata) {
        try {
            Integer parsed = parseInteger(rawValue);
            if (!spec.test(parsed)) {
                throw new IllegalArgumentException();
            }
            field().setTextColor(textColor);
            ConfigHelper.setValue(metadata.path(), value, parsed, metadata.annotations());
        } catch (IllegalArgumentException ignored) {
            field().setTextColor(failTextColor);
        }
    }

    private static String formatValue(Integer value, Map<String, String> annotations) {
        if (annotations.containsKey("IntDisplay")) {
            String intDisplay = annotations.get("IntDisplay");
            return switch (intDisplay) {
                case "#" -> "#" + Integer.toHexString(value).toUpperCase(Locale.ROOT);
                case "0x" -> "0x" + Integer.toHexString(value).toUpperCase(Locale.ROOT);
                case "0b" -> "0b" + Integer.toBinaryString(value);
                default -> String.valueOf(value);
            };
        }
        return String.valueOf(value);
    }

    private static Integer parseInteger(String string) {
        if (string.startsWith("#")) {
            return Integer.parseUnsignedInt(string.substring(1), 16);
        }
        if (string.startsWith("0x")) {
            return Integer.parseUnsignedInt(string.substring(2), 16);
        }
        if (string.startsWith("0b")) {
            return Integer.parseUnsignedInt(string.substring(2), 2);
        }
        return Integer.parseInt(string);
    }
}
