package com.nododiiiii.ponderer.runtime.api;

import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;

import net.createmod.catnip.levelWrappers.WrappedClientLevel;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedSceneElementBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class PonderPlayerElement extends AnimatedSceneElementBase {

	private static final UUID DEFAULT_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final String DEFAULT_NAME = "Player";

	private final Vec3 location;
	private float bodyYaw = 180f;
	private float headYaw = 180f;
	private float pitch = 0f;

	@Nullable
	private GameProfile profile;
	@Nullable
	private AbstractClientPlayer puppet;

	public PonderPlayerElement(Vec3 location) {
		this.location = location;
	}

	public PonderPlayerElement profile(@Nullable GameProfile profile) {
		this.profile = profile;
		return this;
	}

	public PonderPlayerElement facing(float bodyYaw, float headYaw, float pitch) {
		this.bodyYaw = bodyYaw;
		this.headYaw = headYaw;
		this.pitch = pitch;
		applyFacingToPuppet();
		return this;
	}

	private void applyFacingToPuppet() {
		if (puppet == null)
			return;
		puppet.setYRot(bodyYaw);
		puppet.yRotO = bodyYaw;
		puppet.setYBodyRot(bodyYaw);
		puppet.yBodyRotO = bodyYaw;
		puppet.yHeadRot = headYaw;
		puppet.yHeadRotO = headYaw;
		puppet.setXRot(pitch);
		puppet.xRotO = pitch;
	}

	private void ensurePuppet(PonderLevel world) {
		if (puppet != null)
			return;
		GameProfile resolved = resolveProfile();
		ClientLevel clientLevel = WrappedClientLevel.of(world);
		puppet = new RemotePlayer(clientLevel, resolved);
		puppet.setPosRaw(0, 0, 0);
		puppet.xo = 0;
		puppet.yo = 0;
		puppet.zo = 0;
		puppet.xOld = 0;
		puppet.yOld = 0;
		puppet.zOld = 0;
		puppet.setOnGround(true);
		puppet.noPhysics = true;
		applyFacingToPuppet();
	}

	private GameProfile resolveProfile() {
		if (profile != null)
			return profile;
		var localPlayer = Minecraft.getInstance().player;
		if (localPlayer != null)
			return localPlayer.getGameProfile();
		return new GameProfile(DEFAULT_UUID, DEFAULT_NAME);
	}

	@Override
	public void reset(PonderScene scene) {
		super.reset(scene);
		if (puppet == null)
			return;
		puppet.tickCount = 0;
		puppet.setPosRaw(0, 0, 0);
		puppet.xo = 0;
		puppet.yo = 0;
		puppet.zo = 0;
		puppet.xOld = 0;
		puppet.yOld = 0;
		puppet.zOld = 0;
		puppet.walkAnimation.setSpeed(0);
		applyFacingToPuppet();
	}

	@Override
	public void tick(PonderScene scene) {
		super.tick(scene);
		if (puppet == null)
			return;
		puppet.tickCount++;
		puppet.xo = puppet.getX();
		puppet.yo = puppet.getY();
		puppet.zo = puppet.getZ();
		puppet.xOld = puppet.getX();
		puppet.yOld = puppet.getY();
		puppet.zOld = puppet.getZ();
		puppet.yRotO = puppet.getYRot();
		puppet.xRotO = puppet.getXRot();
		puppet.yBodyRotO = puppet.yBodyRot;
		puppet.yHeadRotO = puppet.yHeadRot;
		puppet.oAttackAnim = puppet.attackAnim;
		puppet.walkAnimation.setSpeed(0);
	}

	@Override
	protected void renderLast(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float fade, float pt) {
		ensurePuppet(world);
		if (puppet == null)
			return;

		PoseStack poseStack = graphics.pose();
		EntityRenderDispatcher dispatcher = Minecraft.getInstance()
			.getEntityRenderDispatcher();

		poseStack.pushPose();
		poseStack.translate(location.x, location.y, location.z);
		poseStack.translate(Mth.lerp(pt, puppet.xo, puppet.getX()), Mth.lerp(pt, puppet.yo, puppet.getY()),
			Mth.lerp(pt, puppet.zo, puppet.getZ()));

		dispatcher.render(puppet, 0, 0, 0, 0, pt, poseStack, buffer, lightCoordsFromFade(fade));
		poseStack.popPose();
	}
}
