package com.nododiiiii.ponderer.mixin;

import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PonderLevel.class)
public abstract class PonderLevelModelDataMixin {
    public ModelData getModelData(BlockPos pos) {
        BlockEntity blockEntity = ((PonderLevel) (Object) this).getBlockEntity(pos);
        return blockEntity != null ? blockEntity.getModelData() : ModelData.EMPTY;
    }
}
