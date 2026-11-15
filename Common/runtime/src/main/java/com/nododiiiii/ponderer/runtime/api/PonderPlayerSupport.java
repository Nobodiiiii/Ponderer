package com.nododiiiii.ponderer.runtime.api;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.element.ElementLinkImpl;
import net.createmod.ponder.foundation.instruction.FadeOutOfSceneInstruction;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class PonderPlayerSupport {

	private PonderPlayerSupport() {
	}

	public static ElementLink<PonderPlayerElement> addPlayer(SceneBuilder scene, Vec3 location, float bodyYaw,
		float headYaw, float pitch, @Nullable GameProfile profile) {
		PonderPlayerElement element = new PonderPlayerElement(location)
			.profile(profile)
			.facing(bodyYaw, headYaw, pitch);
		ElementLink<PonderPlayerElement> link = new ElementLinkImpl<>(PonderPlayerElement.class);
		scene.addInstruction(ps -> {
			ps.addElement(element);
			ps.linkElement(element, link);
			element.setVisible(true);
			element.forceApplyFade(1f);
		});
		return link;
	}

	public static ElementLink<PonderPlayerElement> addPlayer(SceneBuilder scene, Vec3 location) {
		return addPlayer(scene, location, 180f);
	}

	public static ElementLink<PonderPlayerElement> addPlayer(SceneBuilder scene, Vec3 location, float yaw) {
		return addPlayer(scene, location, yaw, yaw, 0f, null);
	}

	public static void removePlayer(SceneBuilder scene, ElementLink<PonderPlayerElement> link, int fadeOutTicks) {
		scene.addInstruction(new FadeOutOfSceneInstruction<>(Math.max(0, fadeOutTicks), Direction.DOWN, link));
	}

	public static void hidePlayer(SceneBuilder scene, ElementLink<PonderPlayerElement> link) {
		scene.addInstruction(ps -> {
			PonderPlayerElement element = ps.resolve(link);
			if (element != null)
				element.setVisible(false);
		});
	}
}
