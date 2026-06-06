package com.nododiiiii.ponderer.ai;

final class LlmJsonSupport {

    private LlmJsonSupport() {
    }

    static String extractJson(String response) {
        String trimmed = response == null ? "" : response.trim();
        java.util.regex.Matcher fenceMatcher = java.util.regex.Pattern.compile(
            "```(?:json|JSON)?\\s*\\n", java.util.regex.Pattern.MULTILINE
        ).matcher(trimmed);
        if (fenceMatcher.find()) {
            int contentStart = fenceMatcher.end();
            int fenceEnd = trimmed.indexOf("```", contentStart);
            if (fenceEnd > contentStart) {
                trimmed = trimmed.substring(contentStart, fenceEnd).trim();
            }
        }

        int braceStart = trimmed.indexOf('{');
        if (braceStart < 0) {
            return trimmed;
        }

        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = braceStart; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return trimmed.substring(braceStart, i + 1);
                    }
                }
            }
        }

        int braceEnd = trimmed.lastIndexOf('}');
        if (braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }
        return trimmed;
    }

    static String cleanJson(String json) {
        StringBuilder sb = new StringBuilder(json.length());
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                escape = false;
                sb.append(c);
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                sb.append(c);
                continue;
            }
            if (c == '"') {
                inString = !inString;
                sb.append(c);
                continue;
            }
            if (!inString && c == ',') {
                int j = i + 1;
                while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
                    j++;
                }
                if (j < json.length() && (json.charAt(j) == '}' || json.charAt(j) == ']')) {
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
