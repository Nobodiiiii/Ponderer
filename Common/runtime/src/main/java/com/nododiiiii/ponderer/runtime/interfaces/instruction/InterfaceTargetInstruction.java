package com.nododiiiii.ponderer.runtime.interfaces.instruction;

import com.nododiiiii.ponderer.runtime.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.runtime.mixin.TickingInstructionAccessor;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSceneBuilder;
import net.createmod.ponder.foundation.instruction.PonderInstruction;
import net.createmod.ponder.foundation.instruction.TickingInstruction;

/** Carries interface ownership through deferred timeline execution, including seeks. */
public final class InterfaceTargetInstruction extends TickingInstruction {
    private final PonderInstruction instruction;
    private final InterfaceHost.Target target;

    private InterfaceTargetInstruction(PonderInstruction instruction, InterfaceHost.Target target) {
        super(instruction.isBlocking(), instruction instanceof TickingInstruction ticking
            ? ((TickingInstructionAccessor) ticking).ponderer$getTotalTicks() : 1);
        this.instruction = instruction;
        this.target = target;
    }

    public static SceneBuilder bind(SceneBuilder destination, InterfaceHost.Target target) {
        return new PonderSceneBuilder(destination.getScene()) {
            @Override public void addInstruction(PonderInstruction instruction) {
                destination.addInstruction(new InterfaceTargetInstruction(instruction, target));
            }
        };
    }

    @Override public void reset(PonderScene scene) {
        InterfaceHost.withTarget(target, () -> instruction.reset(scene));
    }

    @Override public void onScheduled(PonderScene scene) {
        InterfaceHost.withTarget(target, () -> instruction.onScheduled(scene));
    }

    @Override public boolean isComplete() { return InterfaceHost.withTarget(target, instruction::isComplete); }

    @Override public void tick(PonderScene scene) {
        InterfaceHost.withTarget(target, () -> instruction.tick(scene));
    }
}
