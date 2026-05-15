package com.nododiiiii.ponderer.nbt;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import javax.annotation.Nullable;

public final class NbtTextCodec {

    private NbtTextCodec() {
    }

    public record ParseResult(@Nullable CompoundTag tag, @Nullable String errorMessage) {
        public boolean success() {
            return tag != null && errorMessage == null;
        }
    }

    public static ParseResult parse(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return new ParseResult(new CompoundTag(), null);
        }
        try {
            return new ParseResult(TagParser.parseTag(value), null);
        } catch (CommandSyntaxException e) {
            return new ParseResult(null, normalizeError(e));
        }
    }

    public static ParseResult parseNonEmpty(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return new ParseResult(null, "NBT is empty");
        }
        return parse(value);
    }

    public static String compact(CompoundTag tag) {
        return tag == null || tag.isEmpty() ? "" : tag.toString();
    }

    public static ParseResult compactFromText(String raw) {
        ParseResult parsed = parse(raw);
        if (!parsed.success()) {
            return parsed;
        }
        return new ParseResult(parsed.tag(), null);
    }

    private static String normalizeError(CommandSyntaxException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "Invalid NBT";
        }
        return message;
    }
}
