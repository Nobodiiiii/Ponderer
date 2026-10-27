package com.nododiiiii.ponderer.interfaces.instruction;

import com.nododiiiii.ponderer.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceStepSpecs;
import com.nododiiiii.ponderer.interfaces.sandbox.SessionOps;
import com.nododiiiii.ponderer.projector.client.ProjectorRenderContext;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;

/**
 * {@code interface_action} (and migrated {@code click_interface}): animates a ghost cursor to
 * the target, then performs the action through the mod's own menu/screen code. Drags pick the
 * stack up part-way through and drop it at the end. When seeking, Ponder fast-forwards the
 * ticks, so the action lands immediately with no visible animation.
 */
public class InterfaceActionInstruction extends TickingInstruction {

    private final InterfaceStepSpecs.Action action;
    private boolean picked;

    public InterfaceActionInstruction(InterfaceStepSpecs.Action action) {
        super(true, Math.max(1, action.duration()));
        this.action = action;
    }

    @Override
    public void reset(PonderScene scene) {
        super.reset(scene);
        picked = false;
    }

    @Override
    protected void firstTick(PonderScene scene) {
        if (!ProjectorRenderContext.isActive()) {
            InterfaceHost.startCursor(scene, action);
        }
    }

    @Override
    public void tick(PonderScene scene) {
        super.tick(scene);
        if (ProjectorRenderContext.isActive()) {
            return;
        }
        int elapsed = totalTicks - remainingTicks;
        InterfaceHost.tickCursor(scene, action, elapsed);
        boolean drag = action.kind() == InterfaceStepSpecs.ActionKind.DRAG;
        if (drag && !picked && (remainingTicks == 0 || elapsed >= Math.round(totalTicks * InterfaceHost.DRAG_PICK))) {
            picked = true;
            InterfaceHost.pressRipple();
            InterfaceHost.script(scene, null, session -> SessionOps.beginDrag(session, action));
        }
        if (remainingTicks == 0) {
            if (action.kind() != InterfaceStepSpecs.ActionKind.HOVER) {
                InterfaceHost.pressRipple();
            }
            InterfaceHost.script(scene, null, session -> SessionOps.perform(session, action));
            InterfaceHost.endCursor(scene, action);
        }
    }
}
