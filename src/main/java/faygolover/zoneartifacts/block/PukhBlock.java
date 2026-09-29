package faygolover.zoneartifacts.block;

import faygolover.zoneartifacts.anomaly.AnomalyCombat;
import faygolover.zoneartifacts.anomaly.Pukh;
import faygolover.zoneartifacts.anomaly.PukhSpores;
import faygolover.zoneartifacts.network.PukhEventPacket;
import faygolover.zoneartifacts.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Burning Fluff ({@link Pukh}) as a block: only the fleshy base is a block; the strands hanging
 * from it are drawn and handled by its block entity ({@link PukhBlockEntity}) and can be walked
 * through. Placed on the underside of a block ({@code facing = down}) or on a wall ({@code facing}
 * = away from it); never on a floor. Falls off when its support goes. Breaking it burns the
 * breaker; it drops pieces of its base, the strands crumble away.
 */
public class PukhBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final VoxelShape CEILING = Block.box(0, 11, 0, 16, 16, 16);
    // The plate on the wall plus the ledge at its top the strands hang from.
    private static final VoxelShape WALL_NORTH = Block.box(0, 0, 0, 16, 16, 3); // support to the north
    private static final VoxelShape WALL_SOUTH = Block.box(0, 0, 13, 16, 16, 16);
    private static final VoxelShape WALL_WEST = Block.box(0, 0, 0, 3, 16, 16);
    private static final VoxelShape WALL_EAST = Block.box(13, 0, 0, 16, 16, 16);

    public PukhBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face == Direction.UP) return null; // hangs, never stands
        BlockState state = defaultBlockState().setValue(FACING, face);
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        if (facing == Direction.UP) return false;
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> WALL_NORTH;
            case NORTH -> WALL_SOUTH;
            case EAST -> WALL_WEST;
            case WEST -> WALL_EAST;
            default -> CEILING;
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PukhBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, ModBlockEntities.PUKH.get(), PukhBlockEntity::clientTick)
                : createTickerHelper(type, ModBlockEntities.PUKH.get(), PukhBlockEntity::serverTick);
    }

    /** Burns whoever breaks it: a hit and a puff of spores in the face. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel && !player.isCreative()
                && level.getBlockEntity(pos) instanceof PukhBlockEntity pukh) {
            Vec3 origin = Pukh.puffOrigin(pos, state.getValue(FACING), pukh.effectiveLength());
            player.invulnerableTime = 0;
            AnomalyCombat.hurt(serverLevel, player, Pukh.DAMAGE_TYPE, pukh.damage(), origin);
            PukhSpores.shoot(serverLevel, origin, player.getEyePosition(), 2.0, pukh.damage());
            PukhEventPacket.puff(serverLevel, origin, player.getEyePosition(), 2.0);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /** Whatever takes it away (breaking, its support gone), the strands crumble. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof PukhBlockEntity pukh) {
            PukhEventPacket.crumble(serverLevel, pos, state.getValue(FACING), pukh.effectiveLength());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
