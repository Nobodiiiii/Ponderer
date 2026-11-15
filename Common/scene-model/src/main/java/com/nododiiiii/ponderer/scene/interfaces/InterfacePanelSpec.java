package com.nododiiiii.ponderer.scene.interfaces;

import com.nododiiiii.ponderer.scene.DslScene;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** A panel owns a flat interface tutorial; recursive panels and world mutations are excluded. */
public final class InterfacePanelSpec {
    public static final String TYPE = "interface_panel";
    public static final float DEFAULT_SCALE = 0.5f;
    public static final List<String> EDITABLE_STEPS = List.of(
        "idle", "text", "show_controls", "change_interface_slot", "click_interface", "play_sound");
    private static final Set<String> ALLOWED = Set.of("show_interface", "idle", "text", "shared_text",
        "show_controls", "change_interface_slot", "click_interface", "play_sound", "set_interface_slots",
        "set_interface_data", "set_interface_context", "interface_action", "await_interface");
    private InterfacePanelSpec() {}

    public static void validate(DslScene.DslStep panel) {
        if (panel.steps == null || panel.steps.isEmpty()
            || panel.steps.getFirst() == null || !"show_interface".equalsIgnoreCase(panel.steps.getFirst().type))
            throw new IllegalArgumentException("interface_panel must start with show_interface");
        if (panel.scale != null && (!Float.isFinite(panel.scale) || panel.scale <= 0))
            throw new IllegalArgumentException("interface_panel scale must be finite and positive");
        validateCrop(panel.panelCrop);
        for (int i = 0; i < panel.steps.size(); i++) {
            var child = panel.steps.get(i);
            String type = child == null || child.type == null ? "" : child.type.toLowerCase(Locale.ROOT);
            if (!ALLOWED.contains(type) || i > 0 && type.equals("show_interface"))
                throw new IllegalArgumentException("interface_panel unsupported child at " + i + ": " + type);
        }
    }

    public static void validateCrop(List<Double> crop) {
        if (crop == null) return;
        if (crop.size() != 4 || crop.stream().anyMatch(v -> v == null || !Double.isFinite(v)))
            throw new IllegalArgumentException("interface_panel panelCrop must contain four finite numbers");
        double x = crop.get(0), y = crop.get(1), width = crop.get(2), height = crop.get(3);
        if (x < 0 || x >= 1 || y < 0 || y >= 1 || width <= 0 || width > 1 || height <= 0 || height > 1
            || x + width > 1.000000001 || y + height > 1.000000001)
            throw new IllegalArgumentException("interface_panel panelCrop must stay within the normalized GUI bounds");
    }

    /** Round endpoints independently so a selection remains stable across GUI scale changes. */
    public static int[] cropRect(int[] full, List<Double> crop) {
        if (crop == null) return full;
        int x = Math.clamp(Math.round(full[2] * crop.get(0)), 0, full[2] - 1);
        int y = Math.clamp(Math.round(full[3] * crop.get(1)), 0, full[3] - 1);
        int right = Math.clamp(Math.round(full[2] * (crop.get(0) + crop.get(2))), x + 1, full[2]);
        int bottom = Math.clamp(Math.round(full[3] * (crop.get(1) + crop.get(3))), y + 1, full[3]);
        return new int[]{full[0] + x, full[1] + y, right - x, bottom - y};
    }
}
