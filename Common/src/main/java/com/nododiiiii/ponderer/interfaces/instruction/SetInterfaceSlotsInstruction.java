package com.nododiiiii.ponderer.interfaces.instruction;

import com.nododiiiii.ponderer.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.interfaces.model.InterfaceStepSpecs;
import com.nododiiiii.ponderer.interfaces.sandbox.SessionOps;
import com.nododiiiii.ponderer.projector.client.ProjectorRenderContext;
import com.nododiiiii.ponderer.ui.InterfaceSlotOverlayRenderer;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;

/**
 * {@code set_interface_slots} (and migrated {@code change_interface_slot}): real items go into
 * the sandbox menu, non-item ingredients are drawn as ghosts over their slots.
 */
public class SetInterfaceSlotsInstruction extends TickingInstruction {

    private final InterfaceStepSpecs.Slots spec;

    public SetInterfaceSlotsInstruction(InterfaceStepSpecs.Slots spec) {
        super(false, 1);
        this.spec = spec;
    }

    @Override
    protected void firstTick(PonderScene scene) {
        if (ProjectorRenderContext.isActive()) {
            return;
        }
        if (spec.replace()) {
            InterfaceSlotOverlayRenderer.clearRuntimeBindings();
            InterfaceHost.forgetScript(scene, "slot:");
        }
        for (InterfaceStepSpecs.GhostWrite ghost : spec.ghosts()) {
            InterfaceSlotOverlayRenderer.putGhost(ghost.index(), ghost.id(), ghost.kind());
        }
        for (InterfaceStepSpecs.SlotWrite write : spec.slots()) {
            InterfaceHost.script(scene, "slot:" + write.index(), session -> SessionOps.writeSlot(session, write));
        }
    }
}
