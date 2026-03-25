package com.nododiiiii.ponderer.forge.sticksnapshot.snapshot;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stat;
import net.minecraftforge.common.util.FakePlayer;

public class ReplayGuardedFakePlayer extends FakePlayer {
    public ReplayGuardedFakePlayer(ServerLevel level, GameProfile profile) {
        super(level, profile);
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
