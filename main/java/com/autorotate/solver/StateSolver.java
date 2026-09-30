package com.autorotate.solver;

import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CalibratedSculkSensorBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.DOOR_HINGE;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING_HOPPER;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HALF;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.ORIENTATION;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.RAIL_SHAPE;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.RAIL_SHAPE_STRAIGHT;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.ROTATION_16;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE;

public final class StateSolver {

    private static final float BANNER_SEGMENT_OFFSET = 180.0F;

    private StateSolver() {
    }

    public static Goal solve(BlockState want, float yaw, float pitch) {
        Block block = want.getBlock();
        boolean horizontal = want.hasProperty(HORIZONTAL_FACING);

        if (block instanceof HopperBlock) {
            return hopper(want);
        }
        if (isWallMounted(block) && horizontal) {
            return Goal.face(want.getValue(HORIZONTAL_FACING));
        }
        if (block instanceof ShulkerBoxBlock && want.hasProperty(FACING)) {
            return Goal.face(want.getValue(FACING));
        }
        if (block instanceof RotatedPillarBlock && want.hasProperty(AXIS)) {
            return Goal.face(Direction.fromAxisAndDirection(want.getValue(AXIS), Direction.AxisDirection.POSITIVE));
        }
        if (block instanceof SlabBlock && want.hasProperty(SLAB_TYPE)) {
            return slab(want.getValue(SLAB_TYPE));
        }
        if (block instanceof StairBlock && horizontal) {
            return stairs(want, yaw);
        }
        if (block instanceof TrapDoorBlock && horizontal) {
            return Goal.of(Aim.NONE, want.getValue(HORIZONTAL_FACING), Bias.ofY(wantsTopHalf(want) ? 0.75 : 0.25));
        }
        if (block instanceof DoorBlock && horizontal) {
            return door(want, yaw);
        }
        if (block instanceof FaceAttachedHorizontalDirectionalBlock && horizontal && want.hasProperty(ATTACH_FACE)) {
            return attached(want, yaw);
        }
        if (block instanceof ChestBlock && horizontal) {
            return chest(want, yaw);
        }
        if (want.hasProperty(ROTATION_16) && isSegmented(block)) {
            float offset = block instanceof SkullBlock ? 0.0F : BANNER_SEGMENT_OFFSET;
            return Goal.aim(new Aim(Aim.segmentYaw(want.getValue(ROTATION_16), yaw, offset), null));
        }
        if (block instanceof BaseRailBlock) {
            return rail(want, yaw);
        }
        if (want.hasProperty(ORIENTATION)) {
            return orientation(want.getValue(ORIENTATION), yaw, pitch);
        }
        if (horizontal) {
            Direction look = neededHorizontalLook(block, want.getValue(HORIZONTAL_FACING));
            return Goal.aim(new Aim(Aim.horizontalYaw(look, yaw), null));
        }
        if (want.hasProperty(FACING)) {
            Direction facing = want.getValue(FACING);
            return look(block instanceof ObserverBlock ? facing : facing.getOpposite(), yaw, pitch);
        }
        return null;
    }

    public static boolean readsHeadYaw(BlockState want) {
        return want.hasProperty(FACING) || want.hasProperty(ORIENTATION);
    }

    public static Bias hingeBias(Direction facing, boolean wantLeft) {
        return switch (facing) {
            case SOUTH -> Bias.ofX(wantLeft ? 0.75 : 0.25);
            case NORTH -> Bias.ofX(wantLeft ? 0.25 : 0.75);
            case EAST -> Bias.ofZ(wantLeft ? 0.25 : 0.75);
            case WEST -> Bias.ofZ(wantLeft ? 0.75 : 0.25);
            default -> Bias.NONE;
        };
    }

    private static Goal hopper(BlockState want) {
        Direction facing = want.hasProperty(FACING_HOPPER) ? want.getValue(FACING_HOPPER)
            : want.hasProperty(FACING) ? want.getValue(FACING) : null;
        if (facing == null) {
            return null;
        }
        return Goal.face(facing == Direction.DOWN ? Direction.UP : facing.getOpposite());
    }

    private static Goal slab(SlabType type) {
        if (type == SlabType.DOUBLE) {
            return null;
        }
        return Goal.face(type == SlabType.TOP ? Direction.DOWN : Direction.UP);
    }

    private static Goal stairs(BlockState want, float yaw) {
        float stairYaw = Aim.horizontalYaw(want.getValue(HORIZONTAL_FACING), yaw);
        Direction face = wantsTopHalf(want) ? Direction.DOWN : Direction.UP;
        return Goal.of(new Aim(stairYaw, null), face, Bias.NONE);
    }

    private static Goal door(BlockState want, float yaw) {
        Direction facing = want.getValue(HORIZONTAL_FACING);
        Bias bias = want.hasProperty(DOOR_HINGE)
            ? hingeBias(facing, want.getValue(DOOR_HINGE) == DoorHingeSide.LEFT)
            : Bias.NONE;
        return Goal.of(new Aim(Aim.horizontalYaw(facing, yaw), null), Direction.UP, bias);
    }

    private static Goal attached(BlockState want, float yaw) {
        Direction facing = want.getValue(HORIZONTAL_FACING);
        Aim aim = new Aim(Aim.horizontalYaw(facing, yaw), null);
        return switch (want.getValue(ATTACH_FACE)) {
            case WALL -> Goal.face(facing);
            case FLOOR -> Goal.of(aim, Direction.UP, Bias.NONE);
            case CEILING -> Goal.of(aim, Direction.DOWN, Bias.NONE);
        };
    }

    private static Goal chest(BlockState want, float yaw) {
        Direction facing = want.getValue(HORIZONTAL_FACING);
        Aim aim = new Aim(Aim.horizontalYaw(facing.getOpposite(), yaw), null);
        return Goal.aim(aim).withSneak(SneakRule.EITHER);
    }

    private static Goal rail(BlockState want, float yaw) {
        RailShape shape = want.hasProperty(RAIL_SHAPE) ? want.getValue(RAIL_SHAPE)
            : want.hasProperty(RAIL_SHAPE_STRAIGHT) ? want.getValue(RAIL_SHAPE_STRAIGHT) : null;
        if (shape == RailShape.EAST_WEST) {
            return Goal.aim(new Aim(Aim.horizontalYaw(Direction.EAST, yaw), null));
        }
        if (shape == RailShape.NORTH_SOUTH) {
            return Goal.aim(new Aim(Aim.horizontalYaw(Direction.NORTH, yaw), null));
        }
        return null;
    }

    private static Goal orientation(FrontAndTop orientation, float yaw, float pitch) {
        Direction front = orientation.front();
        Direction look = front.getOpposite();
        if (front.getAxis() != Direction.Axis.Y) {
            float lookYaw = Aim.horizontalYaw(look, yaw);
            return Goal.aim(new Aim(lookYaw, Aim.keepHorizontal(pitch)));
        }
        Direction top = front == Direction.DOWN ? orientation.top().getOpposite() : orientation.top();
        return Goal.aim(new Aim(Aim.horizontalYaw(top, yaw), Aim.lookVertical(look, pitch)));
    }

    private static Goal look(Direction look, float yaw, float pitch) {
        if (look.getAxis() == Direction.Axis.Y) {
            return Goal.aim(new Aim(null, Aim.lookVertical(look, pitch)));
        }
        return Goal.aim(new Aim(Aim.horizontalYaw(look, yaw), Aim.keepHorizontal(pitch)));
    }

    private static Direction neededHorizontalLook(Block block, Direction facing) {
        if (block instanceof AnvilBlock) {
            return facing.getCounterClockWise();
        }
        boolean followsLook = block instanceof BedBlock
            || block instanceof BellBlock
            || block instanceof CampfireBlock
            || block instanceof DecoratedPotBlock
            || block instanceof CalibratedSculkSensorBlock
            || block instanceof FenceGateBlock
            || block instanceof DoorBlock;
        return followsLook ? facing : facing.getOpposite();
    }

    private static boolean isWallMounted(Block block) {
        return block instanceof WallTorchBlock
            || block instanceof WallSignBlock
            || block instanceof WallSkullBlock
            || block instanceof WallBannerBlock;
    }

    private static boolean isSegmented(Block block) {
        return block instanceof BannerBlock || block instanceof SignBlock || block instanceof SkullBlock;
    }

    private static boolean wantsTopHalf(BlockState want) {
        return want.hasProperty(HALF) && want.getValue(HALF) == Half.TOP;
    }
}
