package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigHelper;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

final class ConfigEntrySupport {

    private ConfigEntrySupport() {
    }

    static Metadata metadataOf(ForgeConfigSpec.ConfigValue<?> value, ForgeConfigSpec.ValueSpec spec) {
        String path = String.join(".", value.getPath());
        @Nullable String unit = null;
        Map<String, String> annotations = new HashMap<>();

        String comment = spec.getComment();
        if (comment != null && !comment.isBlank()) {
            var metadata = ConfigHelper.readMetadataFromComment(new ArrayList<>(Arrays.asList(comment.split("\n"))));
            unit = metadata.getFirst();
            if (metadata.getSecond() != null) {
                annotations.putAll(metadata.getSecond());
            }
        }

        return new Metadata(path, unit, Map.copyOf(annotations));
    }

    static <T> T currentValue(Metadata metadata, ForgeConfigSpec.ConfigValue<T> value) {
        return ConfigHelper.getValue(metadata.path(), value);
    }

    record Metadata(String path, @Nullable String unit, Map<String, String> annotations) {
    }
}
