package com.nododiiiii.ponderer.runtime.viewer;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import com.nododiiiii.ponderer.runtime.interfaces.host.InterfaceHost;

/**
 * A normalized interface anchor carried through Ponder's overlay API as a Vec3.
 * SceneTransformOffsetMixin resolves it in screen space when the overlay renders,
 * after the sandbox has opened and laid out its actual screen.
 */
public final class UiAnchorPoint extends Vec3 {
    private final InterfaceHost.Target target;

    public UiAnchorPoint(double u, double v) {
        this(u, v, null);
    }

    public UiAnchorPoint(double u, double v, InterfaceHost.Target target) {
        super(u, v, 0);
        this.target = target;
    }

    public Vec2 toScreen() {
        UiAnchorViewport.Rect viewport = target == null ? UiAnchorViewport.resolve(Minecraft.getInstance())
            : InterfaceHost.withTarget(target, () -> UiAnchorViewport.resolve(Minecraft.getInstance()));
        return new Vec2(
            (float) (viewport.left() + UiAnchorCoords.decodeToPixelX(x, 1) * Math.max(1, viewport.width())),
            (float) (viewport.top() + UiAnchorCoords.decodeToPixelYTopLeft(y, 1) * Math.max(1, viewport.height())));
    }
}
