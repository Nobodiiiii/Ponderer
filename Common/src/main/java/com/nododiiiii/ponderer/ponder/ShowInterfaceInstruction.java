package com.nododiiiii.ponderer.ponder;

import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.projector.client.ProjectorRenderContext;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;

public class ShowInterfaceInstruction extends TickingInstruction {

    private final DslScene.DslStep step;

    public ShowInterfaceInstruction(DslScene.DslStep step) {
        super(true, 1);
        this.step = step;
    }

    @Override
    protected void firstTick(PonderScene scene) {
        if (ProjectorRenderContext.isActive()) {
            return;
        }
        PondererServices.PLATFORM.showInterfaceStep(step);
    }
}
