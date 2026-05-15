package com.nododiiiii.ponderer.nbt;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class NbtPrettyPrinter {

    private static final Pattern SIMPLE_KEY = Pattern.compile("[A-Za-z0-9._+-]+");

    private NbtPrettyPrinter() {
    }

    public record Line(int number, NbtPath path, Tag tag) {
    }

    public record FormattedText(String text, List<Line> lines) {
        @Nullable
        public Line lineForPath(NbtPath path) {
            for (Line line : lines) {
                if (line.path().equals(path)) {
                    return line;
                }
            }
            return null;
        }
    }

    public static FormattedText format(CompoundTag tag) {
        List<String> out = new ArrayList<>();
        List<Line> lines = new ArrayList<>();
        appendCompound(tag, NbtPath.ROOT, 0, out, lines, null);
        return new FormattedText(String.join("\n", out), List.copyOf(lines));
    }

    private static void appendCompound(CompoundTag tag, NbtPath path, int indent, List<String> out,
                                       List<Line> lines, @Nullable String prefix) {
        if (tag.isEmpty()) {
            addLine(out, lines, indent, (prefix == null ? "" : prefix) + "{}", path, tag);
            return;
        }

        addLine(out, lines, indent, (prefix == null ? "" : prefix) + "{", path, tag);
        List<String> keys = new ArrayList<>(tag.getAllKeys());
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            Tag child = tag.get(key);
            if (child == null) {
                continue;
            }
            appendNamedValue(quoteKey(key) + ": ", child, path.child(key), indent + 1, i + 1 < keys.size(), out, lines);
        }
        addPlainLine(out, indent, "}");
    }

    private static void appendNamedValue(String prefix, Tag tag, NbtPath path, int indent, boolean comma,
                                         List<String> out, List<Line> lines) {
        if (tag instanceof CompoundTag compound && !compound.isEmpty()) {
            appendCompound(compound, path, indent, out, lines, prefix);
            appendCommaToLastLine(out, comma);
            return;
        }
        if (tag instanceof ListTag list && multilineList(list)) {
            appendList(list, path, indent, out, lines, prefix);
            appendCommaToLastLine(out, comma);
            return;
        }
        addLine(out, lines, indent, prefix + inline(tag), path, tag);
        appendCommaToLastLine(out, comma);
    }

    private static void appendList(ListTag list, NbtPath path, int indent, List<String> out,
                                   List<Line> lines, @Nullable String prefix) {
        addLine(out, lines, indent, (prefix == null ? "" : prefix) + "[", path, list);
        for (int i = 0; i < list.size(); i++) {
            Tag child = list.get(i);
            boolean comma = i + 1 < list.size();
            if (child instanceof CompoundTag compound && !compound.isEmpty()) {
                appendCompound(compound, path.child(i), indent + 1, out, lines, null);
                appendCommaToLastLine(out, comma);
            } else if (child instanceof ListTag childList && multilineList(childList)) {
                appendList(childList, path.child(i), indent + 1, out, lines, null);
                appendCommaToLastLine(out, comma);
            } else {
                addLine(out, lines, indent + 1, inline(child), path.child(i), child);
                appendCommaToLastLine(out, comma);
            }
        }
        addPlainLine(out, indent, "]");
    }

    private static boolean multilineList(ListTag list) {
        if (list.isEmpty()) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            Tag child = list.get(i);
            if (child instanceof CompoundTag compound && !compound.isEmpty()) {
                return true;
            }
            if (child instanceof ListTag childList && multilineList(childList)) {
                return true;
            }
        }
        return false;
    }

    private static String inline(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            return inlineCompound(compound);
        }
        if (tag instanceof ListTag list) {
            return inlineList(list);
        }
        if (tag instanceof IntArrayTag array) {
            return inlineIntArray(array.getAsIntArray());
        }
        if (tag instanceof LongArrayTag array) {
            return inlineLongArray(array.getAsLongArray());
        }
        if (tag instanceof ByteArrayTag array) {
            return inlineByteArray(array.getAsByteArray());
        }
        return tag.toString();
    }

    private static String inlineCompound(CompoundTag tag) {
        if (tag.isEmpty()) {
            return "{}";
        }
        StringBuilder out = new StringBuilder("{");
        List<String> keys = new ArrayList<>(tag.getAllKeys());
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            out.append(quoteKey(key)).append(": ").append(inline(tag.get(key)));
            if (i + 1 < keys.size()) {
                out.append(", ");
            }
        }
        return out.append('}').toString();
    }

    private static String inlineList(ListTag list) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            out.append(inline(list.get(i)));
            if (i + 1 < list.size()) {
                out.append(", ");
            }
        }
        return out.append(']').toString();
    }

    private static String inlineIntArray(int[] values) {
        StringBuilder out = new StringBuilder("[I;");
        appendArray(values.length, out, i -> Integer.toString(values[i]));
        return out.append(']').toString();
    }

    private static String inlineLongArray(long[] values) {
        StringBuilder out = new StringBuilder("[L;");
        appendArray(values.length, out, i -> values[i] + "L");
        return out.append(']').toString();
    }

    private static String inlineByteArray(byte[] values) {
        StringBuilder out = new StringBuilder("[B;");
        appendArray(values.length, out, i -> values[i] + "B");
        return out.append(']').toString();
    }

    private static void appendArray(int size, StringBuilder out, ArrayValue value) {
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(value.get(i));
        }
    }

    private static void addLine(List<String> out, List<Line> lines, int indent, String text, NbtPath path, Tag tag) {
        addPlainLine(out, indent, text);
        lines.add(new Line(out.size() - 1, path, tag));
    }

    private static void addPlainLine(List<String> out, int indent, String text) {
        out.add("  ".repeat(Math.max(0, indent)) + text);
    }

    private static void appendCommaToLastLine(List<String> out, boolean comma) {
        if (comma && !out.isEmpty()) {
            int last = out.size() - 1;
            out.set(last, out.get(last) + ",");
        }
    }

    private static String quoteKey(String key) {
        return SIMPLE_KEY.matcher(key).matches() ? key : StringTag.quoteAndEscape(key);
    }

    @FunctionalInterface
    private interface ArrayValue {
        String get(int index);
    }
}
