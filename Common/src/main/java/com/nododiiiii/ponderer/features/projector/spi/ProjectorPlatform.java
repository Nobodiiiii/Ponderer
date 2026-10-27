package com.nododiiiii.ponderer.features.projector.spi;

import com.nododiiiii.ponderer.features.projector.ProjectorBlockEntity;
import net.minecraft.server.level.ServerPlayer;

public interface ProjectorPlatform {
    void openProjectorMenu(ServerPlayer player, ProjectorBlockEntity projector);
}
