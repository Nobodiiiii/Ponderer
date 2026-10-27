package com.nododiiiii.ponderer.runtime.core;

import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.DisplayWorldSectionInstruction;
import net.createmod.ponder.foundation.instruction.FadeOutOfSceneInstruction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Coordinates ownership across the base section, linked sections and temporary fade sections. */
final class SceneSectionOwnership {
    private final Map<Long, WorldSectionElement> owners = new HashMap<>();
    private boolean initialized;

    static final class Reveal {
        private final Set<Long> pending = new HashSet<>();
    }

    private void initialize(SceneBuilder scene) {
        if (!initialized) {
            initialized = true;
            // This instruction runs again on begin/rewind, before any ownership is acquired.
            scene.addInstruction(ps -> owners.clear());
        }
    }

    Reveal prepare(SceneBuilder scene, Selection requested, boolean smartDisplay) {
        initialize(scene);
        Selection selection = requested.copy();
        Reveal reveal = new Reveal();
        scene.addInstruction(ps -> {
            reveal.pending.clear();
            selection.forEach(pos -> {
                ps.getWorld().getBounds().encapsulate(pos);
                WorldSectionElement owner = owners.get(pos.asLong());
                if (!smartDisplay || owner == null || !owner.isVisible()
                        || ps.getWorld().getBlockState(pos).isAir()) {
                    reveal.pending.add(pos.asLong());
                }
            });
            release(ps, filter(ps, selection, pos -> reveal.pending.contains(pos.asLong())));
        });
        return reveal;
    }

    void place(SceneBuilder scene, Selection requested, boolean visible) {
        initialize(scene);
        Selection selection = requested.copy();
        scene.addInstruction(ps -> {
            selection.forEach(pos -> ps.getWorld().getBounds().encapsulate(pos));
            release(ps, selection);
            if (visible) {
                WorldSectionElement base = ps.getBaseWorldSection();
                if (base.isEmpty()) {
                    base.set(selection);
                } else {
                    base.add(selection);
                }
                base.setVisible(true);
                base.setFade(1);
                selection.forEach(pos -> owners.put(pos.asLong(), base));
            }
        });
    }

    ElementLink<WorldSectionElement> show(SceneBuilder scene, Selection requested, int duration,
                                         Direction direction, Supplier<WorldSectionElement> mergeOnto,
                                         Reveal reveal) {
        initialize(scene);
        Selection selection = requested.copy();
        DisplayWorldSectionInstruction instruction = new DisplayWorldSectionInstruction(
                Math.max(0, duration), direction, selection, mergeOnto) {
            private Selection shown;

            @Override
            protected void firstTick(PonderScene ps) {
                // Capture actual blocks now: an air coordinate must not recruit a later placement.
                shown = filter(ps, selection, pos -> reveal.pending.contains(pos.asLong())
                        && !ps.getWorld().getBlockState(pos).isAir());
                release(ps, shown);
                super.firstTick(ps);
                element.set(shown);
                shown.forEach(pos -> owners.put(pos.asLong(), element));
            }

            @Override
            public void tick(PonderScene ps) {
                super.tick(ps);
                if (remainingTicks == 0 && mergeOnto != null) {
                    WorldSectionElement target = mergeOnto.get();
                    // Native merge uses the element's CURRENT selection. Coordinates released by
                    // a later operation stay erased and cannot return when this fade completes.
                    shown.forEach(pos -> owners.computeIfPresent(pos.asLong(),
                            (key, owner) -> owner == element ? target : owner));
                }
            }
        };
        scene.addInstruction(instruction);
        return instruction.createLink(scene.getScene());
    }

    void hide(SceneBuilder scene, Selection selection, int duration, Direction direction) {
        Reveal reveal = prepare(scene, selection, false);
        ElementLink<WorldSectionElement> link = show(scene, selection, 0, direction, null, reveal);
        scene.addInstruction(ps -> {
            WorldSectionElement element = ps.resolve(link);
            owners.values().removeIf(owner -> owner == element);
            if (duration <= 0 && element != null) {
                element.setVisible(false);
                element.setFade(0);
            }
        });
        if (duration > 0) {
            scene.addInstruction(new FadeOutOfSceneInstruction<>(duration, direction, link));
        }
    }

    private void release(PonderScene scene, Selection selection) {
        // Links do not enumerate transient show_structure/merge/fade-out elements. Visit the
        // actual scene, including invisible elements which may still have a pending merge.
        scene.forEach(WorldSectionElement.class, section -> {
            if (!section.isEmpty()) {
                section.erase(selection);
            }
        });
        selection.forEach(pos -> owners.remove(pos.asLong()));
    }

    private static Selection filter(PonderScene scene, Selection selection, Predicate<BlockPos> predicate) {
        Selection origin = scene.getSceneBuildingUtil().select().position(BlockPos.ZERO);
        Selection result = origin.substract(origin);
        for (BlockPos pos : selection) {
            if (predicate.test(pos)) {
                result = result.add(scene.getSceneBuildingUtil().select().position(pos));
            }
        }
        return result;
    }
}
