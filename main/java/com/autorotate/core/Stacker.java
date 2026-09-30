package com.autorotate.core;

import com.autorotate.config.Config;
import com.autorotate.solver.Simulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Stacker {

    private static final long TIMEOUT_MS = 4000;

    private static final Map<BlockPos, Long> JOBS = new LinkedHashMap<>();

    private Stacker() {
    }

    public static boolean isStackable(BlockState want) {
        if (want.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            return want.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE;
        }
        for (IntegerProperty property : Simulator.STEPPED) {
            if (want.hasProperty(property)) {
                return true;
            }
        }
        return false;
    }

    public static void track(BlockPos pos) {
        if (Config.get().stackLayers) {
            JOBS.put(pos.immutable(), System.currentTimeMillis());
        }
    }

    public static void clear() {
        JOBS.clear();
    }

    public static void tick(Minecraft minecraft, LocalPlayer player) {
        if (JOBS.isEmpty()) {
            return;
        }
        if (!Config.get().stackLayers) {
            clear();
            return;
        }
        Level level = minecraft.level;
        Iterator<Map.Entry<BlockPos, Long>> iterator = JOBS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> job = iterator.next();
            BlockPos pos = job.getKey();
            BlockState wanted = SchematicView.visibleWant(pos);
            if (wanted == null || System.currentTimeMillis() - job.getValue() > TIMEOUT_MS
                || !player.isWithinBlockInteractionRange(pos, 0.0)) {
                iterator.remove();
                continue;
            }
            BlockState placed = level.getBlockState(pos);
            if (placed.canBeReplaced() && placed.getBlock() != wanted.getBlock()) {
                continue;
            }
            if (missing(placed, wanted) <= 0) {
                iterator.remove();
                continue;
            }
            Item item = wanted.getBlock().asItem();
            Hotbar.Supply supply = Hotbar.ensure(player, InteractionHand.MAIN_HAND, item);
            if (supply == Hotbar.Supply.MISSING) {
                iterator.remove();
                continue;
            }
            if (supply == Hotbar.Supply.PULLED) {
                return;
            }
            BlockHitResult hit = addingHit(pos, placed);
            Debug.log("Stacking another {} at {}", item, pos);
            PlannedAction action = PlannedAction.block(InteractionHand.MAIN_HAND, hit, hit, pos, false, item);
            Executor.submit(player, action, HitSearch.lookingAt(player, hit), ServerRotation.sees(level, player, hit));
            return;
        }
    }

    private static int missing(BlockState placed, BlockState wanted) {
        if (placed.getBlock() != wanted.getBlock()) {
            return 0;
        }
        if (wanted.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            boolean wantsDouble = wanted.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE;
            return wantsDouble && placed.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE ? 1 : 0;
        }
        for (IntegerProperty property : Simulator.STEPPED) {
            if (wanted.hasProperty(property) && placed.hasProperty(property)) {
                return wanted.getValue(property) - placed.getValue(property);
            }
        }
        return 0;
    }

    private static BlockHitResult addingHit(BlockPos pos, BlockState placed) {
        boolean topSlab = placed.hasProperty(BlockStateProperties.SLAB_TYPE)
            && placed.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.TOP;
        Direction face = topSlab ? Direction.DOWN : Direction.UP;
        return new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
    }
}
