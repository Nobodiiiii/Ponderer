package com.nododiiiii.ponderer.ponder;

import com.nododiiiii.ponderer.platform.PondererServices;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;

public class ClickInterfaceInstruction extends TickingInstruction {

    private final DslScene.DslStep step;

    public ClickInterfaceInstruction(DslScene.DslStep step) {
        super(true, 1);
        this.step = step;
    }

    @Override
    protected void firstTick(PonderScene scene) {
        PondererServices.PLATFORM.clickInterfaceStep(step);
    }
}
