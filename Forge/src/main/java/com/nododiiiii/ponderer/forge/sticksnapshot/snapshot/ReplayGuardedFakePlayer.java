package com.nododiiiii.ponderer.forge.sticksnapshot.snapshot;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stat;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

public class ReplayGuardedFakePlayer extends FakePlayer {
    public ReplayGuardedFakePlayer(ServerLevel level, GameProfile profile) {
        super(level, profile);
    }

    // Forge's FakePlayer hard-overrides position()/blockPosition() to Vec3.ZERO/BlockPos.ZERO,
    // even after setPos. Mods that read player.position() / player.blockPosition() inside
    // Item.use (to write context into NetworkHooks.openScreen extraData) would see (0, 0, 0)
    // and the client menu factory then resolves a bogus pos. Restore standard behavior so
    // virtual item-use replays carry real sandbox coordinates.
    @Override
    public Vec3 position() {
        return new Vec3(this.getX(), this.getY(), this.getZ());
    }

    @Override
    public BlockPos blockPosition() {
        return BlockPos.containing(this.getX(), this.getY(), this.getZ());
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void awardStat(Stat stat) {
        if (ReplayGuard.isActive()) {
            ReplayGuard.auditBlocked("api", "awardStat", getScoreboardName());
            return;
        }
        super.awardStat(stat);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void awardStat(Stat stat, int amount) {
        if (ReplayGuard.isActive()) {
            ReplayGuard.auditBlocked("api", "awardStat(" + amount + ")", getScoreboardName());
            return;
        }
        super.awardStat(stat, amount);
    }

    @Override
    public void giveExperiencePoints(int points) {
        if (ReplayGuard.isActive()) {
            ReplayGuard.auditBlocked("api", "giveExperience(" + points + ")", getScoreboardName());
            return;
        }
        super.giveExperiencePoints(points);
    }
}

