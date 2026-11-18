// Author: dishanhai
package com.dishanhai.sh_better_ui.internal.fumo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/** A seated plush with horizontal placement and vanilla waterlogging. */
public final class DishanhaiFumoBlock extends Block implements SimpleWaterloggedBlock, EntityBlock {
    public static final String ID = "dishanhai_fumo";
    public static final String BEIBEIPUO_ID = "beibeipuo_fumo";
    public static final BooleanProperty DANCING = BooleanProperty.create("dancing");
    // The model extends above the block; keep the interaction shape within this cell.
    private static final VoxelShape NORTH = Shapes.or(
            box(3, 0, 2.25, 13, 3, 9.75),
            box(4, 2.25, 6, 12, 11, 13),
            box(1, 4, 6.5, 15, 10.75, 12.5),
            box(2.5, 10.75, 5, 13.5, 16, 14));
    private static final VoxelShape EAST = Shapes.or(
            box(6.25, 0, 3, 13.75, 3, 13),
            box(3, 2.25, 4, 10, 11, 12),
            box(3.5, 4, 1, 9.5, 10.75, 15),
            box(2, 10.75, 2.5, 11, 16, 13.5));
    private static final VoxelShape SOUTH = Shapes.or(
            box(3, 0, 6.25, 13, 3, 13.75),
            box(4, 2.25, 3, 12, 11, 10),
            box(1, 4, 3.5, 15, 10.75, 9.5),
            box(2.5, 10.75, 2, 13.5, 16, 11));
    private static final VoxelShape WEST = Shapes.or(
            box(2.25, 0, 3, 9.75, 3, 13),
            box(6, 2.25, 4, 13, 11, 12),
            box(6.5, 4, 1, 12.5, 10.75, 15),
            box(5, 10.75, 2.5, 14, 16, 13.5));

    private final Supplier<? extends BlockEntityType<DanceEntity>> entityType;

    public DishanhaiFumoBlock(Properties properties, Supplier<? extends BlockEntityType<DanceEntity>> entityType) {
        super(properties.strength(0.8F).sound(SoundType.WOOL).noOcclusion());
        this.entityType = entityType;
        registerDefaultState(defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(DANCING, false)
                .setValue(BlockStateProperties.WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.WATERLOGGED, DANCING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite())
                .setValue(BlockStateProperties.WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    // Forge 1.20.1 interaction entry point.
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        return useWithoutItem(state, level, pos, player, hit);
    }

    // NeoForge 1.21.1 interaction entry point.
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || player.isShiftKeyDown()) return InteractionResult.PASS;
        toggleDance(state, level, pos);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private void toggleDance(BlockState state, Level level, BlockPos pos) {
        if (!level.isClientSide()) level.setBlock(pos, state.cycle(DANCING), UPDATE_CLIENTS);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(DANCING) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public DanceEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DanceEntity(entityType.get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() && state.getValue(DANCING) && type == entityType.get()
                ? (world, pos, current, entity) -> FumoMusic.playNearby((DanceEntity) entity) : null;
    }

    public static final class DanceEntity extends BlockEntity {
        public DanceEntity(BlockEntityType<DanceEntity> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }
}
