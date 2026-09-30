package com.autorotate.solver;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FluidPlanner {

    private static final int MAX_TRACE = 512;
    private static final int SOURCE_STRENGTH = 8;

    private static final Map<Block, Item> BUCKETS = Map.of(
        Blocks.WATER, Items.WATER_BUCKET,
        Blocks.BUBBLE_COLUMN, Items.WATER_BUCKET,
        Blocks.LAVA, Items.LAVA_BUCKET,
        Blocks.POWDER_SNOW, Items.POWDER_SNOW_BUCKET);

    private FluidPlanner() {
    }

    public static Item bucketFor(BlockState want) {
        return BUCKETS.get(want.getBlock());
    }

    public static boolean isFlowing(BlockState want) {
        FluidState fluid = want.getFluidState();
        return !fluid.isEmpty() && !fluid.isSource();
    }

    public static BlockPos sourceOf(Level schematic, BlockPos flowing) {
        Fluid type = schematic.getBlockState(flowing).getFluidState().getType();
        Deque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        open.add(flowing);
        seen.add(flowing);
        while (!open.isEmpty() && seen.size() <= MAX_TRACE) {
            BlockPos pos = open.poll();
            FluidState here = schematic.getBlockState(pos).getFluidState();
            if (here.isSource()) {
                return pos;
            }
            for (BlockPos next : upstream(schematic, pos, here, type)) {
                if (seen.add(next)) {
                    open.add(next);
                }
            }
        }
        return null;
    }

    public static boolean isFilled(Level level, BlockPos pos, BlockState want) {
        if (want.is(Blocks.POWDER_SNOW)) {
            return level.getBlockState(pos).is(Blocks.POWDER_SNOW);
        }
        FluidState present = level.getFluidState(pos);
        return present.isSource() && present.getType().isSame(want.getFluidState().getType());
    }

    public static Aim solve(Level level, Player player, BlockPos target, Item bucket) {
        Vec3 eye = player.getEyePosition();
        boolean water = bucket == Items.WATER_BUCKET;
        Aim best = null;
        double bestTurn = Double.MAX_VALUE;

        for (Vec3 point : candidates(level, target)) {
            Aim aim = Sight.lookAt(eye, point);
            if (aim == null || !lands(level, player, aim, target, water)) {
                continue;
            }
            double turn = Math.abs(Mth.wrapDegrees(aim.yaw() - player.getYRot())) + Math.abs(aim.pitch() - player.getXRot());
            if (turn < bestTurn) {
                bestTurn = turn;
                best = aim;
            }
        }
        return best;
    }

    private static List<BlockPos> upstream(Level schematic, BlockPos pos, FluidState here, Fluid type) {
        List<BlockPos> next = new ArrayList<>(5);
        BlockPos above = pos.above();
        if (schematic.getBlockState(above).getFluidState().getType().isSame(type)) {
            next.add(above);
        }
        if (isFalling(here)) {
            return next;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            FluidState neighbour = schematic.getBlockState(side).getFluidState();
            if (neighbour.getType().isSame(type) && strength(neighbour) > strength(here)) {
                next.add(side);
            }
        }
        return next;
    }

    private static int strength(FluidState fluid) {
        return fluid.isSource() || isFalling(fluid) ? SOURCE_STRENGTH : fluid.getAmount();
    }

    private static boolean isFalling(FluidState fluid) {
        return fluid.hasProperty(FlowingFluid.FALLING) && fluid.getValue(FlowingFluid.FALLING);
    }

    private static List<Vec3> candidates(Level level, BlockPos target) {
        List<Vec3> points = new ArrayList<>(7);
        points.add(Vec3.atCenterOf(target));
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = target.relative(direction);
            if (level.getBlockState(neighbour).isAir()) {
                continue;
            }
            Direction inward = direction.getOpposite();
            points.add(Vec3.atCenterOf(neighbour).add(inward.getStepX() * 0.5, inward.getStepY() * 0.5, inward.getStepZ() * 0.5));
        }
        return points;
    }

    private static boolean lands(Level level, Player player, Aim aim, BlockPos target, boolean water) {
        BlockHitResult hit = Sight.ray(level, player, aim.yaw(), aim.pitch());
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos clicked = hit.getBlockPos();
        boolean fillsClicked = water && level.getBlockState(clicked).getBlock() instanceof LiquidBlockContainer;
        BlockPos placed = fillsClicked ? clicked : clicked.relative(hit.getDirection());
        return placed.equals(target);
    }
}
