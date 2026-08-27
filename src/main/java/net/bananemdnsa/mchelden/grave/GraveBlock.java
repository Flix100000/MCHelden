package net.bananemdnsa.mchelden.grave;

import com.mojang.serialization.MapCodec;

import net.bananemdnsa.mchelden.registry.MCHeldenBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Der Grabstein. Flach genug, dass man darüber hinwegsieht, hoch genug, dass er im Gras
 * auffällt.
 *
 * <p>Er wird nie platziert, sondern nur beim Tod gesetzt, und verschwindet von selbst,
 * sobald er leer ist. Deswegen gibt es kein Item dazu.
 *
 * <p>Im Überlebensmodus ist er unzerstörbar und kolbenfest. Im Kreativmodus lässt er sich
 * abbauen — das ist gewollt, damit Admins aufräumen können —, dabei fällt aber alles heraus.
 */
public class GraveBlock extends BaseEntityBlock {
    public static final MapCodec<GraveBlock> CODEC = simpleCodec(GraveBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** Sockel und aufrechter Stein, zusammen neun Sechzehntel hoch. */
    private static final VoxelShape SHAPE_NORTH_SOUTH = Shapes.or(
            box(3, 0, 3, 13, 3, 13),
            box(4, 3, 6, 12, 9, 10));
    private static final VoxelShape SHAPE_EAST_WEST = Shapes.or(
            box(3, 0, 3, 13, 3, 13),
            box(6, 3, 4, 10, 9, 12));

    public GraveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return facing.getAxis() == Direction.Axis.Z ? SHAPE_NORTH_SOUTH : SHAPE_EAST_WEST;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GraveBlockEntity(pos, state);
    }

    /**
     * Jeder darf jedes Grab öffnen — auch das eines Gegners.
     *
     * <p>Das ist die Regel, die jeden Tod zu einem Rennen macht: die Beute liegt offen da,
     * und wer zuerst ankommt, nimmt sie.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof GraveBlockEntity grave) {
            grave.open(player);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Beim Abbauen faellt alles heraus, statt spurlos zu verschwinden.
     *
     * <p>Im Ueberlebensmodus ist das Grab unzerstoerbar, aber der Kreativmodus ignoriert
     * Blockhaerte — dort laesst sich auch Bedrock abbauen. Ohne diese Absicherung wuerde ein
     * versehentlicher Linksklick beim Aufraeumen jemandem seine halbe Ausruestung loeschen.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GraveBlockEntity grave) {
            grave.removeNameplate();
            Containers.dropContents(level, pos, grave);

            if (grave.getStoredXp() > 0 && level instanceof ServerLevel serverLevel) {
                ExperienceOrb.award(serverLevel, Vec3.atCenterOf(pos), grave.getStoredXp());
            }
        }
        // Austragen auf jedem Weg, der den Block entfernt: abgebaut, geleert, abgeraeumt.
        // Ein Eintrag ohne Block waere harmlos, aber er wuerde sich ansammeln.
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            GraveRegistry.get(serverLevel.getServer()).remove(pos);
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return createTickerHelper(type, MCHeldenBlockEntities.GRAVE.get(), GraveBlockEntity::tick);
    }
}
