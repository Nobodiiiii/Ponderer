package com.nododiiiii.ponderer.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * A normalized interface anchor carried through Ponder's overlay API as a Vec3.
 * SceneTransformOffsetMixin resolves it in screen space when the overlay renders,
 * after the sandbox has opened and laid out its actual screen.
 */
public final class UiAnchorPoint extends Vec3 {

    public UiAnchorPoint(double u, double v) {
        super(u, v, 0);
    }

    public Vec2 toScreen() {
        UiAnchorViewport.Rect viewport = UiAnchorViewport.resolve(Minecraft.getInstance());
        return new Vec2(
            (float) (viewport.left() + UiAnchorCoords.decodeToPixelX(x, (int) Math.max(1, viewport.width()))),
            (float) (viewport.top() + UiAnchorCoords.decodeToPixelYTopLeft(y, (int) Math.max(1, viewport.height()))));
    }
}
