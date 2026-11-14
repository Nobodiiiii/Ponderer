package com.nododiiiii.ponderer.runtime.interfaces.instruction;

import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.runtime.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceDescriptor;
import com.nododiiiii.ponderer.scene.interfaces.LegacyInterfaceMigrator;
import com.nododiiiii.ponderer.runtime.api.PlaybackExecutionContext;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;

/**
 * Opens the step's interface in a client-side sandbox and adds it to the scene as an overlay
 * element. Seeking replays the timeline from 0, so the session is rebuilt deterministically.
 */
public class ShowInterfaceInstruction extends TickingInstruction {

    private final InterfaceDescriptor descriptor;

    public ShowInterfaceInstruction(DslScene.DslStep step) {
        super(true, 1);
        this.descriptor = LegacyInterfaceMigrator.fromStep(step);
    }

    @Override
    protected void firstTick(PonderScene scene) {
        if (PlaybackExecutionContext.isActive()) {
            return;
        }
        InterfaceHost.open(scene, descriptor);
    }
}
