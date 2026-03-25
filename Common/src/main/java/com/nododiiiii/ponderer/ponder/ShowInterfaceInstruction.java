package com.nododiiiii.ponderer.ponder;

import com.nododiiiii.ponderer.platform.PondererServices;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;

public class ShowInterfaceInstruction extends TickingInstruction {

    private final DslScene.DslStep step;
    private final int durationTicks;

    public ShowInterfaceInstruction(DslScene.DslStep step, int durationTicks) {
        super(true, Math.max(1, durationTicks));
        this.step = step;
        this.durationTicks = Math.max(1, durationTicks);
    }

    @Override
    protected void firstTick(PonderScene scene) {
        PondererServices.PLATFORM.showInterfaceStep(step, durationTicks);
    }
}