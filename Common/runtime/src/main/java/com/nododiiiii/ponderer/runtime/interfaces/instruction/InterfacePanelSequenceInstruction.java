package com.nododiiiii.ponderer.runtime.interfaces.instruction;

import com.nododiiiii.ponderer.runtime.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxSession;
import com.nododiiiii.ponderer.runtime.mixin.TickingInstructionAccessor;
import net.createmod.ponder.api.element.PonderElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.PonderInstruction;
import net.createmod.ponder.foundation.instruction.TickingInstruction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Runs the native child schedule independently of the main scene's blocking instructions. */
public final class InterfacePanelSequenceInstruction extends TickingInstruction {
    private final List<PonderInstruction> instructions;
    private final List<PonderInstruction> active = new ArrayList<>();
    private final Set<PonderElement> elements = new HashSet<>();
    private SandboxSession session;
    private boolean started;
    private int endTick;

    public InterfacePanelSequenceInstruction(List<PonderInstruction> instructions) {
        super(false, duration(instructions));
        this.instructions = List.copyOf(instructions);
    }

    private static int ticks(PonderInstruction instruction) {
        return instruction instanceof TickingInstruction ticking
            ? Math.max(1, ((TickingInstructionAccessor) ticking).ponderer$getTotalTicks()) : 1;
    }

    private static int duration(List<PonderInstruction> instructions) {
        int cursor = 0, end = 1;
        for (var instruction : instructions) {
            int ticks = ticks(instruction);
            end = Math.max(end, cursor + ticks);
            if (instruction.isBlocking()) cursor += ticks;
        }
        return end;
    }

    @Override
    public void onScheduled(PonderScene scene) {
        int start = scene.getTotalTime();
        endTick = start + totalTicks;
        // Let native text markers and keyframes see their child offsets, then restore the
        // main cursor so the next scene step starts immediately alongside this sequence.
        try {
            for (var instruction : instructions) {
                int before = scene.getTotalTime();
                instruction.onScheduled(scene);
                if (instruction.isBlocking() && scene.getTotalTime() == before) scene.addToSceneTime(1);
            }
        } finally {
            scene.addToSceneTime(start - scene.getTotalTime());
        }
    }

    /** Called after the main schedule is counted, so a trailing panel remains seekable. */
    public void includeLifetime(PonderScene scene) {
        scene.addToSceneTime(Math.max(0, endTick - scene.getTotalTime()));
    }

    @Override
    public void reset(PonderScene scene) {
        super.reset(scene);
        instructions.forEach(instruction -> instruction.reset(scene));
        active.clear();
        active.addAll(instructions);
        elements.clear();
        session = null;
        started = false;
    }

    @Override
    public void tick(PonderScene scene) {
        if (started && session != null && InterfaceHost.sessionOf(scene) != session) {
            // A later interface replaces the single active session. Never replay stale
            // actions against it or let this sequence's closing callback hide it.
            remainingTicks = 0;
            active.clear();
            elements.forEach(element -> element.setVisible(false));
            return;
        }
        if (started && session != null && InterfaceHost.mode() != InterfaceHost.Mode.WATCH) {
            // An interactive goal holds this child schedule while the main scene continues.
            endTick++;
            includeLifetime(scene);
            return;
        }
        Set<PonderElement> before = new HashSet<>(scene.getElements());
        super.tick(scene);
        for (var iterator = active.iterator(); iterator.hasNext();) {
            var instruction = iterator.next();
            instruction.tick(scene);
            if (instruction.isComplete()) iterator.remove();
            if (instruction.isBlocking()) break;
        }
        for (var element : scene.getElements()) if (!before.contains(element)) elements.add(element);
        if (!started) {
            session = InterfaceHost.sessionOf(scene);
            started = true;
        }
        if (active.isEmpty()) remainingTicks = 0;
        if (isComplete()) elements.forEach(element -> element.setVisible(false));
    }
}
