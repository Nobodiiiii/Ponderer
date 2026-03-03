package com.nododiiiii.ponderer.ponder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M3: StepParserRegistry -- parses PonderJS code lines back to DslScene.DslStep.
 * Uses regex-based pattern matching for each recognized API call.
 * Covers all step types in the emitter registry (M2).
 */
public final class PonderJsParsers {

    @FunctionalInterface
    interface StepParser {
        /** Try to parse the JS fragment into a DslStep. Return null if no match. */
        DslScene.DslStep parse(String jsFragment);
    }

    private static final List<StepParser> PARSERS = new ArrayList<>();

    static {
        // Order matters: more specific patterns first
        PARSERS.add(PonderJsParsers::parseShowStructure);
        PARSERS.add(PonderJsParsers::parseIdle);
        PARSERS.add(PonderJsParsers::parseIdleSeconds);
        PARSERS.add(PonderJsParsers::parseSharedText);
        PARSERS.add(PonderJsParsers::parseText);
        PARSERS.add(PonderJsParsers::parseShowControls);
        PARSERS.add(PonderJsParsers::parseCreateEntity);
        PARSERS.add(PonderJsParsers::parseCreateItemEntity);
        PARSERS.add(PonderJsParsers::parseRotateCameraY);
        PARSERS.add(PonderJsParsers::parseEncapsulateBounds);
        PARSERS.add(PonderJsParsers::parsePlaySound);
        PARSERS.add(PonderJsParsers::parseSetBlock);
        PARSERS.add(PonderJsParsers::parseSetBlocks);
        PARSERS.add(PonderJsParsers::parseDestroyBlock);
        PARSERS.add(PonderJsParsers::parseReplaceBlocks);
        PARSERS.add(PonderJsParsers::parseHideSection);
        PARSERS.add(PonderJsParsers::parseShowSectionAndMerge);
        PARSERS.add(PonderJsParsers::parseShowIndependentSection);
        PARSERS.add(PonderJsParsers::parseRotateSection);
        PARSERS.add(PonderJsParsers::parseMoveSection);
        PARSERS.add(PonderJsParsers::parseToggleRedstonePower);
        PARSERS.add(PonderJsParsers::parseModifyBlockEntityNbt);
        PARSERS.add(PonderJsParsers::parseIndicateRedstone);
        PARSERS.add(PonderJsParsers::parseIndicateSuccess);
    }

    /**
     * Try to parse a JS statement (may span multiple lines) into a DslStep.
     * Returns null if unrecognized.
     */
    public static DslScene.DslStep tryParse(String jsFragment) {
        if (jsFragment == null || jsFragment.isBlank()) return null;
        // Normalize multiline chained calls: collapse whitespace around dots
        // so "scene\n    .text(...)" becomes "scene.text(...)"
        String stripped = normalizeWhitespace(jsFragment.strip());
        for (StepParser parser : PARSERS) {
            DslScene.DslStep result = parser.parse(stripped);
            if (result != null) return result;
        }
        return null;
    }

    /**
     * Collapse whitespace (including newlines) in JS code while preserving string literals.
     * Turns multiline chained calls into single-line for regex matching.
     */
    static String normalizeWhitespace(String src) {
        if (src == null || src.isEmpty()) return "";
        StringBuilder out = new StringBuilder(src.length());
        boolean inString = false;
        char stringChar = 0;
        boolean lastWasSpace = false;

        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);

            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < src.length()) {
                    out.append(src.charAt(i + 1));
                    i++;
                    continue;
                }
                if (c == stringChar) {
                    inString = false;
                }
                lastWasSpace = false;
                continue;
            }

            if (c == '"' || c == '\'') {
                inString = true;
                stringChar = c;
                out.append(c);
                lastWasSpace = false;
                continue;
            }

            if (Character.isWhitespace(c)) {
                if (!lastWasSpace) {
                    out.append(' ');
                    lastWasSpace = true;
                }
                continue;
            }

            out.append(c);
            lastWasSpace = false;
        }

        return removeSpaceBeforeDot(out.toString());
    }

    /**
     * Remove whitespace immediately before dots in method chains (outside string literals).
     * Converts "scene .text(...)" to "scene.text(...)" for correct regex matching.
     */
    private static String removeSpaceBeforeDot(String src) {
        StringBuilder sb = new StringBuilder(src.length());
        boolean inStr = false;
        char strChr = 0;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (inStr) {
                sb.append(c);
                if (c == '\\' && i + 1 < src.length()) { sb.append(src.charAt(++i)); continue; }
                if (c == strChr) inStr = false;
                continue;
            }
            if (c == '"' || c == '\'') { inStr = true; strChr = c; sb.append(c); continue; }
            if (c == ' ' && i + 1 < src.length() && src.charAt(i + 1) == '.') continue;
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Parse a full scene body (code between the scene callback braces) into a
     * list of DslSteps. Unrecognized statements are silently skipped.
     */
    public static List<DslScene.DslStep> parseSceneBody(String body) {
        List<DslScene.DslStep> steps = new ArrayList<>();
        List<String> statements = splitStatements(stripComments(body));
        boolean nextKeyFrame = false;
        for (String stmt : statements) {
            // Detect standalone scene.addKeyframe() and apply to next parsed step
            String norm = normalizeWhitespace(stmt.strip());
            if (norm.matches("(?i)scene\\.addKeyframe\\s*\\(\\s*\\)\\s*;?")) {
                nextKeyFrame = true;
                continue;
            }
            DslScene.DslStep step = tryParse(stmt);
            if (step != null) {
                if (nextKeyFrame) {
                    step.attachKeyFrame = true;
                    nextKeyFrame = false;
                }
                steps.add(step);
            }
        }
        return steps;
    }

    // -- statement splitting -----------------------------------------------------

    /**
     * Splits a JS body into logical statements by tracking brace/paren depth
     * and splitting on semicolons at depth 0.
     */
    static List<String> splitStatements(String body) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        char stringChar = 0;
        int start = 0;

        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < body.length()) {
                    i++; // skip escaped char
                    continue;
                }
                if (c == stringChar) inString = false;
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                stringChar = c;
                continue;
            }
            if (c == '(' || c == '{' || c == '[') { depth++; continue; }
            if (c == ')' || c == '}' || c == ']') { depth--; continue; }
            if (c == ';' && depth <= 0) {
                String fragment = body.substring(start, i + 1).strip();
                if (!fragment.isEmpty() && !fragment.equals(";")) {
                    result.add(fragment);
                }
                start = i + 1;
            }
        }
        // trailing fragment without semicolon
        String trailing = body.substring(start).strip();
        if (!trailing.isEmpty()) {
            result.add(trailing);
        }
        return result;
    }

    static String stripComments(String src) {
        if (src == null || src.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(src.length());
        boolean inString = false;
        char stringChar = 0;
        boolean inLine = false;
        boolean inBlock = false;

        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            char n = i + 1 < src.length() ? src.charAt(i + 1) : '\0';

            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                    out.append(c);
                }
                continue;
            }

            if (inBlock) {
                if (c == '*' && n == '/') {
                    inBlock = false;
                    i++;
                }
                continue;
            }

            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < src.length()) {
                    out.append(src.charAt(i + 1));
                    i++;
                    continue;
                }
                if (c == stringChar) {
                    inString = false;
                }
                continue;
            }

            if (c == '"' || c == '\'') {
                inString = true;
                stringChar = c;
                out.append(c);
                continue;
            }

            if (c == '/' && n == '/') {
                inLine = true;
                i++;
                continue;
            }
            if (c == '/' && n == '*') {
                inBlock = true;
                i++;
                continue;
            }

            out.append(c);
        }

        return out.toString();
    }

    // -- individual parsers ------------------------------------------------------

    private static final Pattern SHOW_STRUCTURE = Pattern.compile(
        "scene\\.showStructure\\s*\\(\\s*(\\d+)?\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseShowStructure(String js) {
        Matcher m = SHOW_STRUCTURE.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("show_structure");
        if (m.group(1) != null) s.height = Integer.parseInt(m.group(1));
        return s;
    }

    private static final Pattern IDLE = Pattern.compile(
        "scene\\.idle\\s*\\(\\s*(\\d+)\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseIdle(String js) {
        Matcher m = IDLE.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("idle");
        s.duration = Integer.parseInt(m.group(1));
        return s;
    }

    private static final Pattern IDLE_SECONDS = Pattern.compile(
        "scene\\.idleSeconds\\s*\\(\\s*(\\d+)\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseIdleSeconds(String js) {
        Matcher m = IDLE_SECONDS.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("idle");
        s.duration = Integer.parseInt(m.group(1)) * 20;
        return s;
    }

    private static final Pattern TEXT = Pattern.compile(
        "scene\\.text\\s*\\(\\s*(\\d+)\\s*,\\s*\"([^\"]*?)\"(?:\\s*,\\s*\\[([^\\]]+)\\])?\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseText(String js) {
        Matcher m = TEXT.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("text");
        s.duration = Integer.parseInt(m.group(1));
        s.text = LocalizedText.of(m.group(2));
        if (m.group(3) != null) s.point = parseDoubleList(m.group(3));
        parseTextChain(s, js);
        return s;
    }

    private static final Pattern SHARED_TEXT = Pattern.compile(
        "scene\\.sharedText\\s*\\(\\s*(\\d+)\\s*,\\s*\"([^\"]*?)\"(?:\\s*,\\s*\\[([^\\]]+)\\])?\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseSharedText(String js) {
        Matcher m = SHARED_TEXT.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("shared_text");
        s.duration = Integer.parseInt(m.group(1));
        s.key = m.group(2);
        if (m.group(3) != null) s.point = parseDoubleList(m.group(3));
        parseTextChain(s, js);
        return s;
    }

    private static final Pattern SHOW_CONTROLS = Pattern.compile(
        "scene\\.showControls\\s*\\(\\s*(\\d+)\\s*,\\s*\\[([^\\]]+)\\]\\s*,\\s*\"([^\"]+)\"\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseShowControls(String js) {
        Matcher m = SHOW_CONTROLS.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("show_controls");
        s.duration = Integer.parseInt(m.group(1));
        s.point = parseDoubleList(m.group(2));
        s.direction = m.group(3);
        if (js.contains(".leftClick()")) s.action = "left";
        else if (js.contains(".rightClick()")) s.action = "right";
        else if (js.contains(".scroll()")) s.action = "scroll";
        Matcher itemM = Pattern.compile("\\.withItem\\s*\\(\\s*\"([^\"]+)\"\\s*\\)").matcher(js);
        if (itemM.find()) s.item = itemM.group(1);
        if (js.contains(".whileSneaking()")) s.whileSneaking = true;
        if (js.contains(".whileCTRL()")) s.whileCTRL = true;
        return s;
    }

    private static final Pattern CREATE_ENTITY = Pattern.compile(
        "scene\\.world\\.createEntity\\s*\\(\\s*\"([^\"]+)\"\\s*,\\s*\\[([^\\]]+)\\]", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseCreateEntity(String js) {
        Matcher m = CREATE_ENTITY.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("create_entity");
        s.entity = m.group(1);
        s.pos = parseDoubleList(m.group(2));
        return s;
    }

    private static final Pattern CREATE_ITEM_ENTITY = Pattern.compile(
        "scene\\.world\\.createItemEntity\\s*\\(\\s*\\[([^\\]]+)\\]\\s*,\\s*\\[([^\\]]+)\\]\\s*,\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseCreateItemEntity(String js) {
        Matcher m = CREATE_ITEM_ENTITY.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("create_item_entity");
        s.pos = parseDoubleList(m.group(1));
        s.motion = parseDoubleList(m.group(2));
        s.item = m.group(3);
        return s;
    }

    private static final Pattern ROTATE_CAMERA_Y = Pattern.compile(
        "scene\\.rotateCameraY\\s*\\(\\s*(-?[\\d.]+)\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseRotateCameraY(String js) {
        Matcher m = ROTATE_CAMERA_Y.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("rotate_camera_y");
        s.degrees = Float.parseFloat(m.group(1));
        return s;
    }

    private static final Pattern ENCAPSULATE = Pattern.compile(
        "scene\\.encapsulateBounds\\s*\\(\\s*\\[([^\\]]+)\\]\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseEncapsulateBounds(String js) {
        Matcher m = ENCAPSULATE.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("encapsulate_bounds");
        s.bounds = parseIntList(m.group(1));
        return s;
    }

    private static final Pattern PLAY_SOUND = Pattern.compile(
        "scene\\.playSound\\s*\\(\\s*\"([^\"]+)\"(?:\\s*,\\s*\"([^\"]*)\")?(?:\\s*,\\s*(-?[\\d.]+))?(?:\\s*,\\s*(-?[\\d.]+))?", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parsePlaySound(String js) {
        Matcher m = PLAY_SOUND.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("play_sound");
        s.sound = m.group(1);
        if (m.group(2) != null) s.source = m.group(2);
        if (m.group(3) != null) s.soundVolume = Float.parseFloat(m.group(3));
        if (m.group(4) != null) s.pitch = Float.parseFloat(m.group(4));
        return s;
    }

    private static final Pattern SET_BLOCK = Pattern.compile(
        "scene\\.world\\.setBlock\\s*\\(\\s*\\[([^\\]]+)\\]\\s*,\\s*\"([^\"]+)\"(?:\\s*,\\s*(true|false))?", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseSetBlock(String js) {
        Matcher m = SET_BLOCK.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("set_block");
        s.blockPos = parseIntList(m.group(1));
        s.block = m.group(2);
        if (m.group(3) != null) s.spawnParticles = Boolean.parseBoolean(m.group(3));
        return s;
    }

    private static final Pattern SET_BLOCKS = Pattern.compile(
        "scene\\.world\\.setBlocks\\s*\\(\\s*util\\.select\\.fromTo\\s*\\(([^)]+)\\)\\s*,\\s*\"([^\"]+)\"(?:\\s*,\\s*(true|false))?", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseSetBlocks(String js) {
        Matcher m = SET_BLOCKS.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("set_block");
        List<Integer> coords = parseIntList(m.group(1));
        if (coords.size() >= 6) {
            s.blockPos = coords.subList(0, 3);
            s.blockPos2 = coords.subList(3, 6);
        }
        s.block = m.group(2);
        if (m.group(3) != null) s.spawnParticles = Boolean.parseBoolean(m.group(3));
        return s;
    }

    private static final Pattern DESTROY_BLOCK = Pattern.compile(
        "scene\\.world\\.destroyBlock\\s*\\(\\s*\\[([^\\]]+)\\]\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseDestroyBlock(String js) {
        Matcher m = DESTROY_BLOCK.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("destroy_block");
        s.blockPos = parseIntList(m.group(1));
        return s;
    }

    private static final Pattern REPLACE_BLOCKS = Pattern.compile(
        "scene\\.world\\.replaceBlocks\\s*\\(\\s*(?:util\\.select\\.fromTo\\s*\\(([^)]+)\\)|\\[([^\\]]+)\\])\\s*,\\s*\"([^\"]+)\"(?:\\s*,\\s*(true|false))?", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseReplaceBlocks(String js) {
        Matcher m = REPLACE_BLOCKS.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("replace_blocks");
        s.block = m.group(3);
        String coordStr = m.group(1) != null ? m.group(1) : m.group(2);
        List<Integer> coords = parseIntList(coordStr);
        if (coords.size() >= 6) {
            s.blockPos = coords.subList(0, 3);
            s.blockPos2 = coords.subList(3, 6);
        } else if (coords.size() >= 3) {
            s.blockPos = coords.subList(0, 3);
        }
        if (m.group(4) != null) s.spawnParticles = Boolean.parseBoolean(m.group(4));
        return s;
    }

    private static final Pattern HIDE_SECTION = Pattern.compile(
        "scene\\.world\\.hideSection\\s*\\(\\s*(?:util\\.select\\.fromTo\\s*\\(([^)]+)\\)|\\[([^\\]]+)\\])\\s*,\\s*\"([^\"]+)\"\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseHideSection(String js) {
        Matcher m = HIDE_SECTION.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("hide_section");
        String coordStr = m.group(1) != null ? m.group(1) : m.group(2);
        List<Integer> coords = parseIntList(coordStr);
        if (coords.size() >= 6) {
            s.blockPos = coords.subList(0, 3);
            s.blockPos2 = coords.subList(3, 6);
        } else if (coords.size() >= 3) {
            s.blockPos = coords.subList(0, 3);
        }
        s.direction = m.group(3);
        return s;
    }

    private static final Pattern SHOW_SECTION_AND_MERGE = Pattern.compile(
        "scene\\.world\\.showSectionAndMerge\\s*\\(\\s*(?:util\\.select\\.fromTo\\s*\\(([^)]+)\\)|\\[([^\\]]+)\\])\\s*,\\s*\"([^\"]+)\"\\s*,\\s*(\\w+)", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseShowSectionAndMerge(String js) {
        Matcher m = SHOW_SECTION_AND_MERGE.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("show_section_and_merge");
        String coordStr = m.group(1) != null ? m.group(1) : m.group(2);
        List<Integer> coords = parseIntList(coordStr);
        if (coords.size() >= 6) {
            s.blockPos = coords.subList(0, 3);
            s.blockPos2 = coords.subList(3, 6);
        } else if (coords.size() >= 3) {
            s.blockPos = coords.subList(0, 3);
        }
        s.direction = m.group(3);
        s.linkId = m.group(4);
        return s;
    }

    private static final Pattern SHOW_INDEPENDENT_SECTION = Pattern.compile(
        "(?:const|let|var)\\s+(\\w+)\\s*=\\s*scene\\.world\\.showIndependentSection\\s*\\(\\s*(?:util\\.select\\.fromTo\\s*\\(([^)]+)\\)|\\[([^\\]]+)\\])\\s*,\\s*\"([^\"]+)\"\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseShowIndependentSection(String js) {
        Matcher m = SHOW_INDEPENDENT_SECTION.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("show_section_and_merge");
        s.linkId = m.group(1);
        String coordStr = m.group(2) != null ? m.group(2) : m.group(3);
        List<Integer> coords = parseIntList(coordStr);
        if (coords.size() >= 6) {
            s.blockPos = coords.subList(0, 3);
            s.blockPos2 = coords.subList(3, 6);
        } else if (coords.size() >= 3) {
            s.blockPos = coords.subList(0, 3);
        }
        s.direction = m.group(4);
        return s;
    }

    private static final Pattern ROTATE_SECTION = Pattern.compile(
        "scene\\.world\\.rotateSection\\s*\\(\\s*(\\w+)\\s*,\\s*(-?[\\d.]+)\\s*,\\s*(-?[\\d.]+)\\s*,\\s*(-?[\\d.]+)\\s*,\\s*(\\d+)\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseRotateSection(String js) {
        Matcher m = ROTATE_SECTION.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("rotate_section");
        s.linkId = m.group(1);
        s.rotX = Float.parseFloat(m.group(2));
        s.rotY = Float.parseFloat(m.group(3));
        s.rotZ = Float.parseFloat(m.group(4));
        s.duration = Integer.parseInt(m.group(5));
        return s;
    }

    private static final Pattern MOVE_SECTION = Pattern.compile(
        "scene\\.world\\.moveSection\\s*\\(\\s*(\\w+)\\s*,\\s*\\[([^\\]]+)\\]\\s*,\\s*(\\d+)\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseMoveSection(String js) {
        Matcher m = MOVE_SECTION.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("move_section");
        s.linkId = m.group(1);
        s.offset = parseDoubleList(m.group(2));
        s.duration = Integer.parseInt(m.group(3));
        return s;
    }

    private static final Pattern TOGGLE_REDSTONE = Pattern.compile(
        "scene\\.world\\.toggleRedstonePower\\s*\\(\\s*(?:util\\.select\\.fromTo\\s*\\(([^)]+)\\)|\\[([^\\]]+)\\])\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseToggleRedstonePower(String js) {
        Matcher m = TOGGLE_REDSTONE.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("toggle_redstone_power");
        String coordStr = m.group(1) != null ? m.group(1) : m.group(2);
        List<Integer> coords = parseIntList(coordStr);
        if (coords.size() >= 6) {
            s.blockPos = coords.subList(0, 3);
            s.blockPos2 = coords.subList(3, 6);
        } else if (coords.size() >= 3) {
            s.blockPos = coords.subList(0, 3);
        }
        return s;
    }

    private static final Pattern MODIFY_BE_NBT = Pattern.compile(
        "scene\\.world\\.modifyBlockEntityNBT\\s*\\(\\s*(?:util\\.select\\.fromTo\\s*\\(([^)]+)\\)|\\[([^\\]]+)\\])", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseModifyBlockEntityNbt(String js) {
        Matcher m = MODIFY_BE_NBT.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("modify_block_entity_nbt");
        String coordStr = m.group(1) != null ? m.group(1) : m.group(2);
        List<Integer> coords = parseIntList(coordStr);
        if (coords.size() >= 6) {
            s.blockPos = coords.subList(0, 3);
            s.blockPos2 = coords.subList(3, 6);
        } else if (coords.size() >= 3) {
            s.blockPos = coords.subList(0, 3);
        }
        // Try to extract nbt from NBT.parseTag("...")
        Matcher nbtM = Pattern.compile("NBT\\.parseTag\\s*\\(\\s*\"([^\"]+)\"\\s*\\)").matcher(js);
        if (nbtM.find()) {
            s.nbt = nbtM.group(1);
        }
        if (js.contains(", true,") || js.contains(",true,")) {
            s.reDrawBlocks = true;
        }
        return s;
    }

    private static final Pattern INDICATE_REDSTONE = Pattern.compile(
        "scene\\.effects\\.indicateRedstone\\s*\\(\\s*\\[([^\\]]+)\\]\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseIndicateRedstone(String js) {
        Matcher m = INDICATE_REDSTONE.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("indicate_redstone");
        s.blockPos = parseIntList(m.group(1));
        return s;
    }

    private static final Pattern INDICATE_SUCCESS = Pattern.compile(
        "scene\\.effects\\.indicateSuccess\\s*\\(\\s*\\[([^\\]]+)\\]\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static DslScene.DslStep parseIndicateSuccess(String js) {
        Matcher m = INDICATE_SUCCESS.matcher(js);
        if (!m.find()) return null;
        DslScene.DslStep s = step("indicate_success");
        s.blockPos = parseIntList(m.group(1));
        return s;
    }

    // -- chain parsing helpers ---------------------------------------------------

    private static void parseTextChain(DslScene.DslStep step, String js) {
        if (js.contains(".placeNearTarget()")) step.placeNearTarget = true;
        if (js.contains(".attachKeyFrame()")) step.attachKeyFrame = true;
        Matcher colorM = Pattern.compile("\\.colored\\s*\\(\\s*PonderPalette\\.(\\w+)\\s*\\)").matcher(js);
        if (colorM.find()) step.color = colorM.group(1).toLowerCase(Locale.ROOT);
    }

    // -- utility -----------------------------------------------------------------

    private static DslScene.DslStep step(String type) {
        DslScene.DslStep s = new DslScene.DslStep();
        s.type = type;
        return s;
    }

    static List<Integer> parseIntList(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<Integer> result = new ArrayList<>();
        for (String part : csv.split(",")) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                try {
                    result.add((int) Double.parseDouble(trimmed));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }

    static List<Double> parseDoubleList(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<Double> result = new ArrayList<>();
        for (String part : csv.split(",")) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                try {
                    result.add(Double.parseDouble(trimmed));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }
}
