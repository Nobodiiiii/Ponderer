package com.nododiiiii.ponderer.ponder;

import com.nododiiiii.ponderer.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.projector.client.ProjectorRenderContext;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class ClickInterfaceInstruction extends TickingInstruction {

    private final DslScene.DslStep step;

    public ClickInterfaceInstruction(DslScene.DslStep step) {
        super(true, 1);
        this.step = step;
    }

    @Override
    protected void firstTick(PonderScene scene) {
        if (ProjectorRenderContext.isActive() || step.pos == null || step.pos.size() < 2 || step.action == null) {
            return;
        }
        int button = switch (step.action.trim().toLowerCase(Locale.ROOT)) {
            case "left" -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
            case "right" -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            default -> -1;
        };
        if (button >= 0) {
            InterfaceHost.clickNormalized(step.pos.get(0), step.pos.get(1), button);
        }
    }
}
