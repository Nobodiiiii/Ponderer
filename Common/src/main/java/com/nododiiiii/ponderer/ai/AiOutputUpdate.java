package com.nododiiiii.ponderer.ai;

/** A replaceable assistant message for one request attempt, separate from diagnostic events. */
public record AiOutputUpdate(int turn, int attempt, String text) {
    static String visibleText(String text, boolean nativeTools) {
        if (nativeTools) {
            String trimmed = text.stripLeading();
            // A protocol envelope accidentally emitted as prose is never a user message.
            return trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("```") ? "" : text;
        }
        // JSON compatibility responses may opt in to a user-facing message. Decode only
        // that top-level string, even while incomplete; never show partial tool JSON.
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') depth--;
            else if (c == '"') {
                StringBuilder decoded = new StringBuilder();
                i = readString(text, i + 1, decoded);
                if (i >= text.length()) return "";
                if (depth != 1 || !"message".contentEquals(decoded)) continue;
                int next = i + 1;
                while (next < text.length() && Character.isWhitespace(text.charAt(next))) next++;
                if (next >= text.length() || text.charAt(next++) != ':') continue;
                while (next < text.length() && Character.isWhitespace(text.charAt(next))) next++;
                // Nested tool argument keys are excluded by depth.
                if (next < text.length() && text.charAt(next) == '"') {
                    decoded.setLength(0);
                    readString(text, next + 1, decoded);
                    return decoded.toString();
                }
            }
        }
        return "";
    }

    private static int readString(String source, int index, StringBuilder target) {
        for (; index < source.length(); index++) {
            char c = source.charAt(index);
            if (c == '"') return index;
            if (c != '\\') { target.append(c); continue; }
            if (++index >= source.length()) return index;
            c = source.charAt(index);
            switch (c) {
                case '"', '\\', '/' -> target.append(c);
                case 'n' -> target.append('\n');
                case 'r' -> target.append('\r');
                case 't' -> target.append('\t');
                case 'b', 'f' -> { }
                case 'u' -> {
                    if (index + 4 >= source.length()) return source.length();
                    try { target.append((char) Integer.parseInt(source.substring(index + 1, index + 5), 16)); }
                    catch (NumberFormatException invalid) { return source.length(); }
                    index += 4;
                }
                default -> { return source.length(); }
            }
        }
        return index;
    }
}
