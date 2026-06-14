package com.nododiiiii.ponderer.projector;

import com.nododiiiii.ponderer.DisabledFeatureUse;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class ProjectorBlockItem extends BlockItem {

    public ProjectorBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!ProjectorFeature.isProjectorEnabled()) {
            if (!level.isClientSide) {
                DisabledFeatureUse.consumeOne(player, stack, "ponderer.message.disabled.projector");
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, usedHand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!ProjectorFeature.isProjectorEnabled()) {
            if (!context.getLevel().isClientSide) {
                DisabledFeatureUse.consumeOne(
                    context.getPlayer(),
                    context.getItemInHand(),
                    "ponderer.message.disabled.projector");
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return super.useOn(context);
    }
}
