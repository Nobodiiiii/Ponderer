package com.nododiiiii.ponderer.ponder;

import com.nododiiiii.ponderer.ui.InterfaceSlotOverlayRenderer;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;

public class ChangeInterfaceSlotInstruction extends TickingInstruction {
    private final DslScene.DslStep step;

    public ChangeInterfaceSlotInstruction(DslScene.DslStep step) {
        super(true, 1);
        this.step = step;
    }

    @Override
    protected void firstTick(PonderScene scene) {
        InterfaceSlotOverlayRenderer.applyStep(step);
    }
}
