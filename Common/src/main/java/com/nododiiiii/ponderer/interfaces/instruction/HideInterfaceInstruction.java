package com.nododiiiii.ponderer.interfaces.instruction;

import com.nododiiiii.ponderer.interfaces.host.InterfaceHost;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;

/** {@code hide_interface}: closes the shown interface; the scene view returns to the 3D world. */
public class HideInterfaceInstruction extends TickingInstruction {

    public HideInterfaceInstruction(int duration) {
        super(false, Math.max(1, duration));
    }

    @Override
    protected void firstTick(PonderScene scene) {
        InterfaceHost.hide(scene);
    }
}
