package com.nododiiiii.ponderer.ui;

import java.util.Locale;

public final class SelectionAnimationOptions {

    public static final String[] DIRECTIONS = {"down", "up", "north", "south", "west", "east"};
    public static final String[] ENTRANCE_ANIMATIONS = {"none", "simultaneous", "down", "up", "south", "north", "east", "west"};

    private SelectionAnimationOptions() {
    }

    public static String normalizeDirection(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "up", "上", "向上" -> "up";
            case "north", "北", "向北" -> "north";
            case "south", "南", "向南" -> "south";
            case "west", "西", "向西" -> "west";
            case "east", "东", "向东" -> "east";
            default -> "down";
        };
    }

    public static String normalizeEntranceAnimation(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "从上到下", "上到下", "top_to_bottom", "top-down", "down" -> "down";
            case "从下到上", "下到上", "bottom_to_top", "bottom-up", "up" -> "up";
            case "从北到南", "北到南", "north_to_south", "north-south", "south" -> "south";
            case "从南到北", "南到北", "south_to_north", "south-north", "north" -> "north";
            case "从西到东", "西到东", "west_to_east", "west-east", "east" -> "east";
            case "从东到西", "东到西", "east_to_west", "east-west", "west" -> "west";
            case "同时", "simultaneous" -> "simultaneous";
            default -> "none";
        };
    }

    public static String optionLabel(String prefix, String value) {
        String key = prefix + "." + value;
        String translated = UIText.of(key);
        return key.equals(translated) ? value : translated;
    }

    public static String entranceAnimationLabel(String value) {
        return UIText.of("ponderer.ui.entrance_animation.option." + value);
    }
}
