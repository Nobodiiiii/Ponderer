package com.nododiiiii.ponderer.runtime.core;

import com.nododiiiii.ponderer.runtime.api.PonderSceneViewOffsetAccess;
import com.nododiiiii.ponderer.runtime.interfaces.instruction.ShowInterfaceInstruction;
import com.nododiiiii.ponderer.scene.DslScene;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.runtime.mixin.PonderSceneAccessor;
import com.nododiiiii.ponderer.runtime.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.runtime.interfaces.instruction.AwaitInterfaceInstruction;
import com.nododiiiii.ponderer.runtime.interfaces.instruction.HideInterfaceInstruction;
import com.nododiiiii.ponderer.runtime.interfaces.instruction.InterfaceActionInstruction;
import com.nododiiiii.ponderer.runtime.interfaces.instruction.SetInterfaceContextInstruction;
import com.nododiiiii.ponderer.runtime.interfaces.instruction.SetInterfaceDataInstruction;
import com.nododiiiii.ponderer.runtime.interfaces.instruction.SetInterfaceSlotsInstruction;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceStepSpecs;
import com.nododiiiii.ponderer.runtime.viewer.UiAnchorPoint;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.element.InputElementBuilder;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.nododiiiii.ponderer.runtime.spi.*;

/** Runtime step semantics shared by every playback host. */
final class SceneStepExecutor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final StructureSource structures;
    private final IngredientResolver ingredients;
    private final com.nododiiiii.ponderer.runtime.api.StepRegistry steps;
    SceneStepExecutor(StructureSource structures, IngredientResolver ingredients, com.nododiiiii.ponderer.runtime.api.StepRegistry steps) {
        this.structures = structures;
        this.ingredients = ingredients;
        this.steps = steps;
    }

    static class StepContext {
        final Map<String, Object> state = new HashMap<>();
        final com.nododiiiii.ponderer.runtime.api.PonderActions.Context actions = new com.nododiiiii.ponderer.runtime.api.PonderActions.Context();
        final Map<String, ElementLink<WorldSectionElement>> sectionLinks = actions.sections();
        final Map<String, List<ElementLink<EntityElement>>> entityLinks = actions.entities();
        boolean uiAnchorMode;
    }

    void applyStep(SceneBuilder scene, SceneBuildingUtil util, DslScene dsl, DslScene.DslStep step, StepContext context) {
        if (Boolean.TRUE.equals(step.attachKeyFrame)) {
            scene.addKeyframe();
        }
        if (PortableSteps.apply(scene, step, context.actions)) {
            if ("show_structure".equalsIgnoreCase(step.type)) {
                if (context.uiAnchorMode) scene.addInstruction(InterfaceHost::hide);
                context.uiAnchorMode = false;
                scene.addInstruction(ps -> {
                    if (ps instanceof PonderSceneAccessor accessor && ps instanceof PonderSceneViewOffsetAccess viewOffset)
                        viewOffset.ponderer$setDefaultScale(accessor.ponderer$getScaleFactor());
                });
            }
            return;
        }
        switch (step.type.toLowerCase(Locale.ROOT)) {

            case "show_extra_structure" -> applyShowExtraStructure(scene, dsl, step, context);
            case "idle" -> scene.idle(step.durationOrDefault(20));
            case "text" -> applyText(scene, step, context);
            case "shared_text" -> applySharedText(scene, step, context);

            case "zoom_scene" -> applyZoomScene(scene, step);

            case "show_controls" -> applyShowControls(scene, step, context);
            case "show_interface" -> applyShowInterface(scene, step, context);
            case "interface_panel" -> applyInterfacePanel(scene, util, dsl, step);
            case "change_interface_slot" -> applyChangeInterfaceSlot(scene, step);
            case "click_interface" -> applyClickInterface(scene, step);
            case "set_interface_slots" -> scene.addInstruction(
                new SetInterfaceSlotsInstruction(InterfaceStepSpecs.slots(step)));
            case "set_interface_data" -> scene.addInstruction(
                new SetInterfaceDataInstruction(InterfaceStepSpecs.data(step), step.durationOrDefault(1)));
            case "set_interface_context" -> scene.addInstruction(
                new SetInterfaceContextInstruction(InterfaceStepSpecs.context(step)));
            case "interface_action" -> applyInterfaceAction(scene, step);
            case "hide_interface" -> {
                scene.addInstruction(new HideInterfaceInstruction(step.durationOrDefault(1)));
                context.uiAnchorMode = false;
            }
            case "await_interface" -> applyAwaitInterface(scene, step);

            case "next_scene" -> {
            }
            default -> steps.build(step.type, step.parameters,
                new com.nododiiiii.ponderer.runtime.api.StepBuildContext(scene, util, dsl.id, step.id,
                    context.sectionLinks, context.entityLinks, context.state, context.actions));
        }
    }

    private void applyText(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        String text = step.text == null ? "" : step.text.resolve();
        int duration = step.durationOrDefault(60);
        com.nododiiiii.ponderer.runtime.api.PonderActions.showText(scene, text,
            hasExplicitPoint(step) ? resolveOverlayPoint(step, context) : null,
            duration, step.color, Boolean.TRUE.equals(step.placeNearTarget));
    }

    private void applySharedText(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        if (step.text != null) {
            applyText(scene, step, context);
            return;
        }
        String key = step.key;
        if (key == null || key.isBlank()) {
            LOGGER.warn("shared_text missing key");
            return;
        }
        ResourceLocation loc = key.contains(":")
            ? ResourceLocation.tryParse(key)
            : ResourceLocation.fromNamespaceAndPath(scene.getScene().getNamespace(), key);
        if (loc == null) {
            LOGGER.warn("shared_text invalid key: {}", key);
            return;
        }

        int duration = step.durationOrDefault(60);
        scene.addInstruction(new TextMarkerInstruction(key, true));
        TextElementBuilder builder = scene.overlay().showText(duration).sharedText(loc);

        if (hasExplicitPoint(step)) {
            builder.pointAt(resolveOverlayPoint(step, context));
        }

        PonderPalette palette = parsePalette(step.color);
        if (palette != null) {
            builder.colored(palette);
        }
        if (Boolean.TRUE.equals(step.placeNearTarget)) {
            builder.placeNearTarget();
        }
    }

    private static boolean hasExplicitPoint(DslScene.DslStep step) {
        return step.point != null && !step.point.isEmpty();
    }

    private void applyZoomScene(SceneBuilder scene, DslScene.DslStep step) {
        float multiplier = step.scale == null ? Float.NaN : step.scale;
        if (multiplier <= 0) {
            LOGGER.warn("zoom_scene scale must be > 0, got {}", multiplier);
            return;
        }

        boolean useDefaultCenter = step.point == null || step.point.size() < 3;
        Vec3 center = useDefaultCenter ? Vec3.ZERO : toPoint(step.point);
        int duration = step.durationOrDefault(20);
        scene.addInstruction(new AdjustViewInstruction(center, useDefaultCenter, multiplier, duration));
    }

    private void applyShowControls(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        Vec3 point = resolveOverlayPoint(step, context);
        Pointing pointing = parsePointing(step.direction);
        int duration = step.durationOrDefault(60);

        InputElementBuilder builder = scene.overlay().showControls(point, pointing, duration);

        switch (step.action == null ? "" : step.action.toLowerCase(Locale.ROOT)) {
            case "left" -> builder.leftClick();
            case "right" -> builder.rightClick();
            case "scroll" -> builder.scroll();
            default -> {
            }
        }

        if (step.item != null && !step.item.isBlank()) {
            resolveIngredient(builder, step.item, step.nbt);
        }

        if (Boolean.TRUE.equals(step.whileSneaking)) {
            builder.whileSneaking();
        }
        if (Boolean.TRUE.equals(step.whileCTRL)) {
            builder.whileCTRL();
        }
    }

    private void applyInterfacePanel(SceneBuilder scene, SceneBuildingUtil util, DslScene dsl, DslScene.DslStep panel) {
        com.nododiiiii.ponderer.scene.interfaces.InterfacePanelSpec.validate(panel);
        var descriptor = com.nododiiiii.ponderer.scene.interfaces.LegacyInterfaceMigrator.fromStep(panel.steps.getFirst());
        scene.addInstruction(ps -> {
            if (!InterfaceHost.isPlaybackSuppressed(ps))
                InterfaceHost.openPanel(ps, descriptor, toPoint(panel.point), parsePointing(panel.direction),
                    panel.panelTransform == null ? com.nododiiiii.ponderer.scene.interfaces.InterfacePanelSpec.DEFAULT_TRANSFORM : panel.panelTransform,
                    panel.panelCrop, !Boolean.FALSE.equals(panel.panelBackground));
        });
        StepContext nested = new StepContext();
        nested.uiAnchorMode = true;
        int tail = 0;
        for (int i = 1; i < panel.steps.size(); i++) {
            var child = panel.steps.get(i);
            applyStep(scene, util, dsl, child, nested);
            switch (child.type.toLowerCase(Locale.ROOT)) {
                case "idle" -> tail = Math.max(0, tail - child.durationOrDefault(20));
                case "click_interface", "await_interface" -> tail = Math.max(0, tail - 1);
                case "interface_action" -> {
                    var action = InterfaceStepSpecs.action(child);
                    if (action != null) tail = Math.max(0, tail - Math.max(1, action.duration()));
                }
                // Native text/control instructions include five ticks of fade at each end.
                case "text", "shared_text", "show_controls" -> tail = Math.max(tail, child.durationOrDefault(60) + 10);
                case "set_interface_data" -> tail = Math.max(tail, Math.max(1, child.durationOrDefault(1)));
                default -> { }
            }
        }
        // Let child overlays finish before destroying their viewport.
        scene.idle(Math.max(1, tail));
        scene.addInstruction(InterfaceHost::hide);
    }

    private void applyShowInterface(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        if (step.interfaceSpec == null) {
            String source = step.interfaceSource == null ? "block" : step.interfaceSource.toLowerCase(java.util.Locale.ROOT);
            switch (source) {
                case "held_item" -> {
                    if (step.item == null || step.item.isBlank()) {
                        LOGGER.warn("show_interface missing item id for held_item source");
                        return;
                    }
                }
                case "ui_id" -> {
                    if (step.uiId == null || step.uiId.isBlank()) {
                        LOGGER.warn("show_interface missing uiId for ui_id source");
                        return;
                    }
                }
                default -> {
                    if (!"block".equals(source)) {
                        LOGGER.warn("show_interface unknown interfaceSource '{}', treating as block", step.interfaceSource);
                    }
                    if (step.block == null || step.block.isBlank()) {
                        LOGGER.warn("show_interface missing block id");
                        return;
                    }
                }
            }
        }

        scene.addInstruction(this::ponderer$resetSceneViewState);
        scene.addInstruction(new OverlayMarkerInstruction(step));
        scene.addInstruction(new ShowInterfaceInstruction(step));
        context.uiAnchorMode = true;
    }

    private void applyClickInterface(SceneBuilder scene, DslScene.DslStep step) {
        InterfaceStepSpecs.Action action = InterfaceStepSpecs.fromLegacyClick(step);
        if (action == null) {
            LOGGER.warn("click_interface needs pos [u, v] and action left|right");
            return;
        }
        scene.addInstruction(new InterfaceActionInstruction(action));
    }

    private void applyChangeInterfaceSlot(SceneBuilder scene, DslScene.DslStep step) {
        if (step.interfaceSlots == null || step.interfaceSlots.isEmpty()) {
            LOGGER.warn("change_interface_slot missing interfaceSlots");
            return;
        }
        scene.addInstruction(new SetInterfaceSlotsInstruction(InterfaceStepSpecs.fromLegacyChangeSlot(step.interfaceSlots)));
    }

    private void applyInterfaceAction(SceneBuilder scene, DslScene.DslStep step) {
        InterfaceStepSpecs.Action action = InterfaceStepSpecs.action(step);
        if (action == null) {
            LOGGER.warn("interface_action unknown action '{}'", step.action);
            return;
        }
        scene.addInstruction(new InterfaceActionInstruction(action));
    }

    private void applyAwaitInterface(SceneBuilder scene, DslScene.DslStep step) {
        InterfaceStepSpecs.Goal goal = InterfaceStepSpecs.goal(step);
        if (goal == null) {
            LOGGER.warn("await_interface needs goal.slot");
            return;
        }
        String hint = step.hint == null ? null : step.hint.resolve();
        scene.addInstruction(new AwaitInterfaceInstruction(goal, hint == null || hint.isBlank() ? null : hint,
            step.timeout == null ? 600 : step.timeout));
    }

    private void ponderer$resetSceneViewState(net.createmod.ponder.foundation.PonderScene ps) {
        if (!(ps instanceof PonderSceneViewOffsetAccess viewOffset)) {
            return;
        }

        viewOffset.ponderer$resetViewOffset();
        float defaultScale = viewOffset.ponderer$getDefaultScale();
        if (ps instanceof PonderSceneAccessor accessor && !Float.isNaN(defaultScale)) {
            accessor.ponderer$setScaleFactor(defaultScale);
        }
        viewOffset.ponderer$setDefaultScale(Float.NaN);
    }

    /**
     * Resolve an ingredient ID and apply it to the builder.
     * For items: use withItem() so the item renders alongside the action icon (LMB/RMB/Scroll).
     * For non-items (fluids, chemicals, etc.): use showing() via JEI renderer, which replaces the icon slot.
     * This distinction is critical because showing() overwrites the icon field (leftClick/rightClick/scroll),
     * while withItem() uses a separate item field that renders alongside the icon.
     */
    private void resolveIngredient(InputElementBuilder builder, String id, @Nullable String nbt) {
        // 1. Try parsing as item stack spec first (supports optional NBT)
        ItemStack stack = parseItemStackSpec(id, nbt);
        if (stack != null) {
            builder.withItem(stack);
            return;
        }

        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) return;

        // 2. Try fluid registry (requires JEI for rendering)
        net.minecraft.world.level.material.Fluid fluid =
                BuiltInRegistries.FLUID.getOptional(loc).orElse(null);
        if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
            if (ingredients.isAvailable()) {
                // Use JEI ingredient helper to create a fluid element in a platform-agnostic way
                net.createmod.catnip.gui.element.ScreenElement element =
                        ingredients.fluid(fluid, 1000);
                if (element != null) {
                    builder.showing(element);
                    return;
                }
            }
            LOGGER.warn("show_controls: fluid '{}' found but JEI is not available for rendering", id);
            return;
        }

        // 3. Fallback: search all JEI ingredient types (chemicals, etc.)
        if (ingredients.isAvailable()) {
            net.createmod.catnip.gui.element.ScreenElement element =
                    ingredients.resolve(id);
            if (element != null) {
                builder.showing(element);
                return;
            }
        }

        LOGGER.warn("show_controls: unable to resolve ingredient '{}'", id);
    }

    @Nullable
    private ItemStack parseItemStackSpec(String raw, @Nullable String nbtOverride) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String trimmed = raw.trim();
        String itemIdPart = trimmed;
        String nbtPart = null;

        int brace = trimmed.indexOf('{');
        if (brace >= 0) {
            itemIdPart = trimmed.substring(0, brace).trim();
            nbtPart = trimmed.substring(brace).trim();
        }

        ResourceLocation itemLoc = ResourceLocation.tryParse(itemIdPart);
        if (itemLoc == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(itemLoc).orElse(null);
        if (item == null) {
            return null;
        }

        ItemStack stack = new ItemStack(item);
        String finalNbtPart = (nbtOverride != null && !nbtOverride.isBlank()) ? nbtOverride.trim() : nbtPart;
        if (finalNbtPart != null && !finalNbtPart.isBlank() && !"{}".equals(finalNbtPart)) {
            try {
                CompoundTag tag = TagParser.parseTag(finalNbtPart);
                if (!tag.isEmpty()) {
                    stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            net.minecraft.world.item.component.CustomData.of(tag));
                }
            } catch (Exception e) {
                LOGGER.warn("show_controls invalid item nbt: {}", finalNbtPart);
            }
        }
        return stack;
    }

    private void applyShowExtraStructure(SceneBuilder scene, DslScene dsl, DslScene.DslStep step, StepContext context) {
        if (step.structure == null || step.structure.isBlank()) throw new IllegalArgumentException("Missing extra structure in " + dsl.id + "/" + step.id);
        try (var input = openExtraStructure(dsl, step.structure)) {
            if (input == null) throw new IllegalArgumentException("Missing structure " + step.structure + " in " + dsl.id + "/" + step.id);
            com.nododiiiii.ponderer.runtime.api.PonderActions.showExtraStructure(scene, context.actions, input,
                PortableSteps.pos(step.blockPos), step.rotation == null ? 0 : Math.round(step.rotation), Boolean.TRUE.equals(step.replaceAir),
                step.immediateDisplay, step.spawnParticles, step.entranceAnimation, step.entranceDuration, step.entranceInterval,
                step.smartDisplay, step.linkId, step.direction);
        } catch (java.io.IOException error) { throw new IllegalArgumentException("Cannot load structure " + step.structure + " in " + dsl.id + "/" + step.id, error); }
    }

    @Nullable
    private java.io.InputStream openExtraStructure(DslScene dsl, String reference) throws java.io.IOException {
        String trimmed = reference.trim();
        String relativePath;
        if (trimmed.contains(":")) {
            ResourceLocation rl = ResourceLocation.tryParse(trimmed);
            if (rl == null) {
                return null;
            }
            relativePath = "ponderer".equals(rl.getNamespace())
                    ? rl.getPath()
                    : rl.getNamespace() + "/" + rl.getPath();
        } else {
            relativePath = trimmed;
        }
        return structures.open(relativePath, dsl.pack);
    }

    private Vec3 resolveOverlayPoint(DslScene.DslStep step, StepContext context) {
        if (!context.uiAnchorMode) {
            return toPoint(step.point);
        }

        double u = 0.0;
        double v = 0.0;
        if (step.point != null && !step.point.isEmpty()) {
            u = step.point.get(0);
            if (step.point.size() >= 2) {
                v = step.point.get(1);
            }
        }

        return new UiAnchorPoint(u, v);
    }

    private Vec3 toPoint(List<Double> point) {
        if (point == null || point.size() < 3) {
            return new Vec3(2.5, 1.5, 2.5);
        }
        return new Vec3(point.get(0), point.get(1), point.get(2));
    }

    private Pointing parsePointing(String raw) {
        if (raw == null || raw.isBlank()) {
            return Pointing.DOWN;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "up" -> Pointing.UP;
            case "left" -> Pointing.LEFT;
            case "right" -> Pointing.RIGHT;
            default -> Pointing.DOWN;
        };
    }

    private PonderPalette parsePalette(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "white" -> PonderPalette.WHITE;
            case "black" -> PonderPalette.BLACK;
            case "red" -> PonderPalette.RED;
            case "green" -> PonderPalette.GREEN;
            case "blue" -> PonderPalette.BLUE;
            case "input" -> PonderPalette.INPUT;
            case "output" -> PonderPalette.OUTPUT;
            case "slow" -> PonderPalette.SLOW;
            case "medium" -> PonderPalette.MEDIUM;
            case "fast" -> PonderPalette.FAST;
            default -> null;
        };
    }
}
