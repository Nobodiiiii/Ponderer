package com.nododiiiii.ponderer.projector;

import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class ProjectorBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final ProjectorKind kind;

    public ProjectorBlock(ProjectorKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(POWERED, Boolean.FALSE)
            .setValue(LIT, Boolean.FALSE));
    }

    public ProjectorKind kind() {
        return kind;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = kind == ProjectorKind.MINIATURE
            ? context.getHorizontalDirection()
            : context.getHorizontalDirection().getOpposite();
        return defaultBlockState()
            .setValue(FACING, facing)
            .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()))
            .setValue(LIT, Boolean.FALSE);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, LIT);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ProjectorBlockEntity projector) {
            projector.refreshRedstoneState(level.hasNeighborSignal(pos));
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide
            && player instanceof ServerPlayer serverPlayer
            && level.getBlockEntity(pos) instanceof ProjectorBlockEntity projector) {
            PondererServices.PLATFORM.openProjectorMenu(serverPlayer, projector);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.hasBlockEntity() || state.getBlock() == newState.getBlock()) {
            super.onRemove(state, level, pos, newState, moving);
            return;
        }

        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ProjectorBlockEntity projector) {
            Containers.dropContents(level, pos, projector);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProjectorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.PROJECTOR.get(),
            level.isClientSide ? ProjectorBlockEntity::clientTick : ProjectorBlockEntity::serverTick);
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }
}
