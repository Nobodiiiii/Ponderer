package com.nododiiiii.ponderer.support.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import javax.annotation.Nullable;

/**
 * Treats scene-provided NBT as untrusted input: bounded size, parse failures degrade
 * instead of throwing, and fields that would reach outside the sandbox are dropped.
 */
public final class DataSanitizer {

    public static final int DEFAULT_MAX_SNBT_CHARS = 256 * 1024;

    private DataSanitizer() {
    }

    public record Parsed(@Nullable CompoundTag tag, @Nullable String error) {
        public boolean ok() {
            return tag != null;
        }
    }

    /** Parses block entity SNBT, rejecting oversized input and stripping loot table references. */
    public static Parsed parseBlockEntityNbt(@Nullable String snbt, int maxChars) {
        Parsed parsed = parse(snbt, maxChars);
        if (parsed.tag() != null) {
            stripBlockEntityHazards(parsed.tag());
        }
        return parsed;
    }

    public static Parsed parse(@Nullable String snbt, int maxChars) {
        if (snbt == null || snbt.isBlank()) {
            return new Parsed(null, null);
        }
        if (snbt.length() > maxChars) {
            return new Parsed(null, "SNBT exceeds " + maxChars + " characters (" + snbt.length() + ")");
        }
        try {
            // TagParser enforces its own nesting depth limit.
            return new Parsed(TagParser.parseTag(snbt), null);
        } catch (Exception ex) {
            return new Parsed(null, "invalid SNBT: " + ex.getMessage());
        }
    }

    /**
     * Loot tables would unpack on a server; coordinates would re-anchor the block entity.
     * Neither belongs in a sandbox block entity.
     */
    public static void stripBlockEntityHazards(CompoundTag tag) {
        tag.remove("LootTable");
        tag.remove("LootTableSeed");
        tag.remove("x");
        tag.remove("y");
        tag.remove("z");
    }
}
