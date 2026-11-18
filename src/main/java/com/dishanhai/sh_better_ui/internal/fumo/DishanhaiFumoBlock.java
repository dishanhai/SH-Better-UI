// Author: dishanhai
package com.dishanhai.sh_better_ui.internal.fumo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
    public static final BooleanProperty DANCING = BooleanProperty.create("dancing");
    private static final int NOTE_TICKS = 5;
    private static final int[] MELODY = {6, 10, 13, 10, 8, 11, 15, -1, 13, 10, 8, 10, 11, 8, 6, -1};
    private static final VoxelShape NORTH = Shapes.or(
            box(2.5, 0, 2, 13.5, 3.5, 10.5),
            box(1.5, 3.5, 5, 14.5, 8, 11.5),
            box(2.5, 8, 3, 13.5, 16, 13));
    private static final VoxelShape EAST = Shapes.or(
            box(5.5, 0, 2.5, 14, 3.5, 13.5),
            box(4.5, 3.5, 1.5, 11, 8, 14.5),
            box(3, 8, 2.5, 13, 16, 13.5));
    private static final VoxelShape SOUTH = Shapes.or(
            box(2.5, 0, 5.5, 13.5, 3.5, 14),
            box(1.5, 3.5, 4.5, 14.5, 8, 11),
            box(2.5, 8, 3, 13.5, 16, 13));
    private static final VoxelShape WEST = Shapes.or(
            box(2, 0, 2.5, 10.5, 3.5, 13.5),
            box(5, 3.5, 1.5, 11.5, 8, 14.5),
            box(3, 8, 2.5, 13, 16, 13.5));

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
        return !level.isClientSide() && state.getValue(DANCING) && type == entityType.get()
                ? (world, pos, current, entity) -> playNote(world, pos) : null;
    }

    private static void playNote(Level level, BlockPos pos) {
        long tick = level.getGameTime();
        if (tick % NOTE_TICKS != 0) return;
        int note = MELODY[Math.floorMod(tick / NOTE_TICKS, MELODY.length)];
        if (note < 0) return;
        float pitch = (float) Math.pow(2.0, (note - 12) / 12.0);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.RECORDS, 0.22F, pitch);
    }

    public static float squash(long tick, float partialTick) {
        double beat = (Math.floorMod(tick, NOTE_TICKS * 2) + partialTick) / (NOTE_TICKS * 2);
        return (float) ((1.0 - Math.cos(beat * Math.PI * 2.0)) * 0.5);
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
