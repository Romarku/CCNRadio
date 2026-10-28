package com.ccnradio.block;

import com.ccnradio.blockentity.TapePlayerBlockEntity;
import com.ccnradio.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public final class TapePlayerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    /** Footprint of the model, which is only 3.5/16 of a block tall. */
    private static final VoxelShape SHAPE_NORTH_SOUTH = Block.box(4.0D, 0.0D, 2.0D, 12.0D, 3.5D, 14.0D);
    private static final VoxelShape SHAPE_EAST_WEST = Block.box(2.0D, 0.0D, 4.0D, 14.0D, 3.5D, 12.0D);

    public TapePlayerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_EAST_WEST : SHAPE_NORTH_SOUTH;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, LevelAccessor level, BlockPos pos, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        Direction facing = state.getValue(FACING);
        if (mirror == Mirror.FRONT_BACK) {
            if (facing.getAxis() == Direction.Axis.X) {
                facing = facing.getOpposite();
            }
        } else if (mirror == Mirror.LEFT_RIGHT) {
            if (facing.getAxis() == Direction.Axis.Z) {
                facing = facing.getOpposite();
            }
        }
        return state.setValue(FACING, facing);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TapePlayerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof TapePlayerBlockEntity tapePlayer && tickLevel instanceof ServerLevel serverLevel) {
                tapePlayer.serverTick(serverLevel, tickPos, tickState);
            }
        };
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        boolean configurator = player.getMainHandItem().is(ModItems.TAPE_PLAYER_CONFIGURATOR.get())
                || player.getOffhandItem().is(ModItems.TAPE_PLAYER_CONFIGURATOR.get());

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }

        // The configurator opens the settings GUI whether or not the player sneaks: vanilla
        // routes sneaking item clicks to the held item before the block, so requiring sneak
        // here would make the GUI unreachable while anything is held.
        if (configurator) {
            if (level.getBlockEntity(pos) instanceof TapePlayerBlockEntity tapePlayer) {
                NetworkHooks.openScreen(serverPlayer, tapePlayer, buffer -> buffer.writeBlockPos(pos));
            }
            return InteractionResult.CONSUME;
        }

        if (!(level.getBlockEntity(pos) instanceof TapePlayerBlockEntity tapePlayer)) {
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown()) {
            tapePlayer.stopPlayback(serverPlayer);
        } else {
            tapePlayer.startPlayback(serverPlayer);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof TapePlayerBlockEntity tapePlayer) {
            tapePlayer.stopPlayback(null);
            Containers.dropContents(level, pos, tapePlayer);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
