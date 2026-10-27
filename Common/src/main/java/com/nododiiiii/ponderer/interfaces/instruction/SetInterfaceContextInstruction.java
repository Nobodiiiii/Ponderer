package com.nododiiiii.ponderer.interfaces.instruction;

import com.nododiiiii.ponderer.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceStepSpecs;
import com.nododiiiii.ponderer.interfaces.sandbox.SessionOps;
import com.nododiiiii.ponderer.runtime.api.PlaybackExecutionContext;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;

/** {@code set_interface_context}: patches the sandbox player (xp, game mode, inventory) mid-scene. */
public class SetInterfaceContextInstruction extends TickingInstruction {

    private final InterfaceStepSpecs.ContextPatch patch;

    public SetInterfaceContextInstruction(InterfaceStepSpecs.ContextPatch patch) {
        super(false, 1);
        this.patch = patch;
    }

    @Override
    protected void firstTick(PonderScene scene) {
        if (!PlaybackExecutionContext.isActive()) {
            InterfaceHost.script(scene, null, session -> SessionOps.patchContext(session, patch));
        }
    }
}
