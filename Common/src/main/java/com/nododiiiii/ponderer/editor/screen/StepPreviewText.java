package com.nododiiiii.ponderer.editor.screen;

import com.nododiiiii.ponderer.editor.capture.InterfaceSlotEditState;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceDescriptor;
import com.nododiiiii.ponderer.scene.interfaces.LegacyInterfaceMigrator;
import com.nododiiiii.ponderer.scene.DslScene;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Short, language-aware summaries shared by every row in the step list. */
final class StepPreviewText {
    private StepPreviewText() {
    }

    static String title(@Nullable DslScene.DslStep step) {
        if (step == null || step.type == null || step.type.isBlank()) {
            return UIText.of("ponderer.ui.invalid");
        }
        String key = "ponderer.ui.step.type." + step.type.toLowerCase(Locale.ROOT);
        String translated = UIText.of(key);
        return key.equals(translated) ? step.type : translated;
    }

    static String summary(@Nullable DslScene.DslStep step) {
        if (step == null || step.type == null) return "";
        return singleLine(switch (step.type.toLowerCase(Locale.ROOT)) {
            case "show_structure", "show_extra_structure" -> value(step.structure);
            case "idle" -> step.durationOrDefault(20) + " " + UIText.of("ponderer.ui.ticks");
            case "text" -> step.text == null ? "" : step.text.resolve();
            case "shared_text" -> sharedText(step.key);
            case "create_entity" -> entityName(step.entity);
            case "create_item_entity" -> counted(itemName(step.item), step.count);
            case "rotate_camera_y" -> step.degreesX == null
                ? number(step.degrees == null ? 90 : step.degrees) + "°"
                : join("X " + number(step.degreesX) + "°", "Y " + number(step.degrees == null ? 90 : step.degrees) + "°");
            case "zoom_scene" -> join(step.scale == null ? "" : "×" + number(step.scale), vector(step.point));
            case "show_controls" -> join(action(step.action), blank(step.item) ? "" : ingredientName(step.item));
            case "show_interface" -> interfaceTarget(step);
            case "change_interface_slot" -> slotSummary(step);
            case "click_interface" -> join(action(blank(step.action) ? "left" : step.action), vector(step.point));
            case "encapsulate_bounds" -> step.bounds == null ? "" : step.bounds.stream()
                .map(StepPreviewText::number).collect(Collectors.joining(" × "));
            case "play_sound" -> soundName(step.sound);
            case "set_block", "replace_blocks" -> blockName(step.block);
            case "destroy_block", "toggle_redstone_power", "modify_block_entity_nbt",
                 "highlight_section", "indicate_redstone", "indicate_success" -> region(step);
            case "hide_section", "show_section_and_merge" -> join(region(step), direction(step.direction));
            case "rotate_section" -> join(sectionTarget(step), vector(Arrays.asList(step.rotX, step.rotY, step.rotZ), "°"));
            case "move_section" -> join(sectionTarget(step), vector(step.offset));
            case "spawn_particles" -> particleName(step.particle);
            case "clear_entities", "modify_entities_nbt" -> entityTarget(step, false);
            case "clear_item_entities", "modify_item_entities_nbt" -> entityTarget(step, true);
            case "set_interface_slots" -> UIText.of("ponderer.ui.step.preview.slots",
                (step.slots == null ? 0 : step.slots.size()) + (step.ghosts == null ? 0 : step.ghosts.size()));
            case "set_interface_data" -> UIText.of("ponderer.ui.step.preview.values", step.data == null ? 0 : step.data.size());
            case "set_interface_context" -> UIText.of("ponderer.ui.step.preview.values",
                (step.player == null ? 0 : step.player.size()) + (step.target == null ? 0 : step.target.size()));
            case "interface_action" -> action(step.action);
            case "hide_interface" -> duration(step, 1);
            case "await_interface" -> step.hint == null ? "" : step.hint.resolve();
            case "next_scene" -> "";
            default -> "";
        });
    }

    private static String interfaceTarget(DslScene.DslStep step) {
        InterfaceDescriptor descriptor = LegacyInterfaceMigrator.fromStep(step);
        if (descriptor.block != null) return blockName(descriptor.block);
        if (descriptor.item != null) return itemName(descriptor.item.id());
        return value(descriptor.menuType);
    }

    private static String slotSummary(DslScene.DslStep step) {
        List<DslScene.InterfaceSlotBinding> bindings = step.interfaceSlots == null ? List.of() : step.interfaceSlots.stream()
            .filter(binding -> binding != null && !InterfaceSlotEditState.isClearBinding(binding)).toList();
        if (bindings.size() == 1) {
            DslScene.InterfaceSlotBinding binding = bindings.getFirst();
            return counted(ingredientName(binding.ingredientId), binding.itemCount);
        }
        return UIText.of("ponderer.ui.step.preview.slots", bindings.size());
    }

    private static String entityTarget(DslScene.DslStep step, boolean item) {
        String id = item ? step.item : step.entity;
        String target = blank(id) ? "" : item ? itemName(id) : entityName(id);
        String scope = !blank(step.linkId) ? "#" + step.linkId
            : Boolean.TRUE.equals(step.fullScene) ? UIText.of("ponderer.ui.clear_entities.full_scene") : region(step);
        String result = join(target, scope);
        return result.isBlank() ? UIText.of("ponderer.ui.step.preview.all") : result;
    }

    private static String sectionTarget(DslScene.DslStep step) {
        return blank(step.linkId) ? region(step) : "#" + step.linkId;
    }

    private static String region(DslScene.DslStep step) {
        String start = vector(step.blockPos);
        String end = vector(step.blockPos2);
        return start.isEmpty() ? end : end.isEmpty() || start.equals(end) ? start : start + " → " + end;
    }

    private static String vector(@Nullable List<? extends Number> values) {
        return vector(values, "");
    }

    private static String vector(@Nullable List<? extends Number> values, String unit) {
        if (values == null || values.size() < 3) return "";
        return values.stream().limit(3).map(value -> number(value) + unit).collect(Collectors.joining(", ", "(", ")"));
    }

    private static String duration(DslScene.DslStep step, int fallback) {
        return UIText.of("ponderer.ui.step.preview.seconds", number(step.durationOrDefault(fallback) / 20.0));
    }

    private static String number(@Nullable Number value) {
        if (value == null) return "0";
        if (!Double.isFinite(value.doubleValue())) return "?";
        return BigDecimal.valueOf(value.doubleValue()).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String counted(String name, @Nullable Integer count) {
        return count == null || count <= 1 ? name : name + " ×" + count;
    }

    private static String blockName(@Nullable String id) {
        ResourceLocation key = location(id);
        if (key == null || !BuiltInRegistries.BLOCK.containsKey(key)) return value(id);
        var block = BuiltInRegistries.BLOCK.get(key);
        return translatedName(block.getName(), block.getDescriptionId(), id);
    }

    private static String itemName(@Nullable String id) {
        ResourceLocation key = location(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return value(id);
        var stack = BuiltInRegistries.ITEM.get(key).getDefaultInstance();
        return translatedName(stack.getHoverName(), stack.getDescriptionId(), id);
    }

    private static String ingredientName(@Nullable String id) {
        ResourceLocation key = location(id);
        return key != null && BuiltInRegistries.ITEM.containsKey(key) ? itemName(id) : blockName(id);
    }

    private static String entityName(@Nullable String id) {
        ResourceLocation key = location(id);
        if (key == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(key)) return value(id);
        var entity = BuiltInRegistries.ENTITY_TYPE.get(key);
        return translatedName(entity.getDescription(), entity.getDescriptionId(), id);
    }

    private static String translatedName(Component name, String translationKey, @Nullable String fallback) {
        String text = name.getString();
        return text.isBlank() || text.equals(translationKey) ? value(fallback) : text;
    }

    private static String soundName(@Nullable String id) {
        ResourceLocation key = location(id);
        var sound = key == null ? null : Minecraft.getInstance().getSoundManager().getSoundEvent(key);
        Component subtitle = sound == null ? null : sound.getSubtitle();
        if (subtitle == null) return value(id);
        return subtitle.getContents() instanceof TranslatableContents translated
            ? translatedName(subtitle, translated.getKey(), id) : subtitle.getString();
    }

    private static String particleName(@Nullable String particle) {
        if (blank(particle)) return "?";
        String id = particle.trim().split("[\\s{]", 2)[0];
        ResourceLocation key = location(id);
        return key == null ? id : translation("particle." + key.getNamespace() + "." + key.getPath(), id);
    }

    private static String sharedText(@Nullable String key) {
        if (blank(key)) return "?";
        ResourceLocation id = ResourceLocation.tryParse(key.contains(":") ? key : "ponderer:" + key);
        return id == null ? key : translation(id.getNamespace() + "." + PonderLocalization.LANG_PREFIX + "shared." + id.getPath(),
            translation(key, key));
    }

    private static String action(@Nullable String action) {
        if (blank(action) || "none".equalsIgnoreCase(action)) return "";
        String normalized = action.toLowerCase(Locale.ROOT);
        return translation("ponderer.ui.show_controls.action." + normalized,
            translation("ponderer.ui.step.preview.action." + normalized, action));
    }

    private static String direction(@Nullable String direction) {
        return blank(direction) ? "" : translation("ponderer.ui.show_controls.direction." + direction.toLowerCase(Locale.ROOT), direction);
    }

    private static String translation(String key, String fallback) {
        String text = UIText.of(key);
        return text.equals(key) ? fallback : text;
    }

    @Nullable
    private static ResourceLocation location(@Nullable String id) {
        return blank(id) ? null : ResourceLocation.tryParse(id.trim());
    }

    private static boolean blank(@Nullable String text) {
        return text == null || text.isBlank();
    }

    private static String value(@Nullable String text) {
        return blank(text) ? "?" : text;
    }

    private static String join(String... parts) {
        return Arrays.stream(parts).filter(part -> !part.isBlank()).collect(Collectors.joining(" · "));
    }

    private static String singleLine(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
