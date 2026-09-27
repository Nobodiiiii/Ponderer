package com.nododiiiii.ponderer.ponder;

import com.nododiiiii.ponderer.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.interfaces.model.InterfaceDescriptor;
import com.nododiiiii.ponderer.interfaces.model.LegacyInterfaceMigrator;
import com.nododiiiii.ponderer.projector.client.ProjectorRenderContext;
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
        if (ProjectorRenderContext.isActive()) {
            return;
        }
        InterfaceHost.open(scene, descriptor);
    }
}
