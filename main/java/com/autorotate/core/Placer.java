package com.autorotate.core;

import com.autorotate.AutoRotate;
import com.autorotate.config.Config;
import com.autorotate.solver.Aim;
import com.autorotate.solver.FluidPlanner;
import com.autorotate.solver.Goal;
import com.autorotate.solver.HitPlanner;
import com.autorotate.solver.Simulator;
import com.autorotate.solver.StateSolver;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Placer {

    public static final int SWAP_ATTEMPTS = 3;
    private static final int OBSTRUCTION_ATTEMPTS = 10;
    private static final int WATER_WAIT_TICKS = 2;
    private static final int WATER_ATTEMPTS = 15;
    private static final int WATER_PENDING_TICKS = 40;

    private enum Water {
        STARTED,
        SWAPPING,
        UNAVAILABLE
    }

    private static final List<Property<?>> TYPE_PROPERTIES = List.of(
        BlockStateProperties.SLAB_TYPE,
        BlockStateProperties.CHEST_TYPE,
        BlockStateProperties.HALF,
        BlockStateProperties.DOOR_HINGE);

    private record Click(LocalPlayer player, InteractionHand hand, BlockHitResult hit, Level level,
                         boolean interactive, boolean shield) {
    }

    private static final Map<BlockPos, Long> PENDING_WATER = new HashMap<>();

    private Placer() {
    }

    public static InteractionResult onUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
        if (!Config.get().enabled) {
            return null;
        }
        if (Executor.isBusy() || Cooldown.sentThisTick()) {
            Executor.enqueue(hand, hit);
            return InteractionResult.CONSUME;
        }
        try {
            return decide(player, hand, hit);
        } catch (RuntimeException e) {
            AutoRotate.LOG.error("AutoRotate could not handle a click, leaving it to vanilla", e);
            return null;
        }
    }

    public static InteractionResult decide(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
        Config config = Config.get();
        Level level = player.level();
        boolean interactive = Interactions.isInteractive(level.getBlockState(hit.getBlockPos()));
        boolean shield = config.noBlockInteraction && interactive;
        if (!shield && config.deferToBlockInteraction && interactive && !player.isShiftKeyDown()) {
            return null;
        }
        Click click = new Click(player, hand, hit, level, interactive, shield);

        Level schematic = SchematicView.world();
        if (schematic == null) {
            return leave(click);
        }

        ItemStack held = player.getItemInHand(hand);
        boolean placing = isPlaceable(held);
        BlockPos target = new BlockPlaceContext(player, hand, held, hit).getClickedPos();
        BlockState want = SchematicView.visibleWant(target);

        if (want.isAir()) {
            return nothingWanted(click, schematic, held, placing, target);
        }
        BlockPos anchor = anchorOf(target, want);
        if (anchor != null) {
            return placing ? placeFromAnchor(click, target, anchor, want) : leave(click);
        }
        if (Cooldown.active()) {
            Executor.enqueue(hand, hit);
            return InteractionResult.CONSUME;
        }

        Item bucket = FluidPlanner.bucketFor(want);
        if (bucket != null) {
            return placeFluid(click, schematic, held, placing, target, want, bucket);
        }

        Item needed = want.getBlock().asItem();
        Hotbar.Supply supply = Hotbar.ensure(player, hand, needed);
        if (supply == Hotbar.Supply.MISSING) {
            if (placing && config.strictSchematic) {
                return Notify.refuse(player, "missing", nameOf(want, needed));
            }
            return leave(click);
        }
        if (supply == Hotbar.Supply.PULLED && config.swapSettleTicks > 0) {
            return deferClick(click, config.swapSettleTicks, SWAP_ATTEMPTS);
        }

        ItemStack block = player.getItemInHand(hand);
        if (!(block.getItem() instanceof BlockItem)) {
            return leave(click);
        }
        if (waitForClearance(click, block)) {
            return InteractionResult.CONSUME;
        }
        InteractionResult water = waterFirst(click, target, want);
        if (water != null) {
            return water;
        }
        if (Stacker.isStackable(want)) {
            Stacker.track(target);
        }
        return placeBlock(click, block, target, want);
    }

    private static BlockPos anchorOf(BlockPos target, BlockState want) {
        if (want.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
            && want.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return target.below();
        }
        if (want.hasProperty(BlockStateProperties.BED_PART) && want.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
            return target.relative(want.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
        }
        return null;
    }

    private static InteractionResult placeFromAnchor(Click click, BlockPos target, BlockPos anchor, BlockState want) {
        LocalPlayer player = click.player();
        Component name = want.getBlock().getName();
        if (click.level().getBlockState(anchor).is(want.getBlock())) {
            return Notify.refuse(player, "alreadyThere", name);
        }
        BlockHitResult hit = HitSearch.supportHit(click.level(), player, anchor);
        if (hit == null) {
            return Notify.refuse(player, "needsSupport", name, Notify.position(anchor.below()));
        }
        Debug.log("{} at {} is the second part, placing it from {}", name.getString(), target, anchor);
        return decide(player, click.hand(), hit);
    }

    private static InteractionResult nothingWanted(Click click, Level schematic, ItemStack held, boolean placing,
                                                   BlockPos target) {
        Config config = Config.get();
        boolean strict = placing && config.strictSchematic;
        if (strict && !wantsWaterlogging(schematic, click, held)) {
            boolean hidden = !SchematicView.isVisible(target) && !schematic.getBlockState(target).isAir();
            return Notify.refuse(click.player(), hidden ? "hiddenLayer" : "outside");
        }
        return leave(click);
    }

    private static InteractionResult placeFluid(Click click, Level schematic, ItemStack held, boolean placing,
                                                BlockPos target, BlockState want, Item bucket) {
        boolean guarded = held.getItem() instanceof BucketItem || (placing && Config.get().strictSchematic);
        if (FluidPlanner.isFlowing(want)) {
            return guarded ? pointToSource(click, schematic, target, want) : leave(click);
        }
        if (FluidPlanner.isFilled(click.level(), target, want)) {
            return guarded ? Notify.refuse(click.player(), "alreadyThere", want.getBlock().getName()) : leave(click);
        }
        if (bucket == Items.WATER_BUCKET) {
            return switch (supplyWater(click, target)) {
                case STARTED -> InteractionResult.CONSUME;
                case SWAPPING -> deferClick(click, Config.get().swapSettleTicks, SWAP_ATTEMPTS);
                case UNAVAILABLE -> explainNoWater(click, placing, target, want);
            };
        }
        LocalPlayer player = click.player();
        Hotbar.Supply supply = Hotbar.ensure(player, click.hand(), bucket);
        if (supply == Hotbar.Supply.MISSING) {
            return placing && Config.get().strictSchematic
                ? Notify.refuse(player, "missing", nameOf(want, bucket))
                : leave(click);
        }
        if (supply == Hotbar.Supply.PULLED && Config.get().swapSettleTicks > 0) {
            return deferClick(click, Config.get().swapSettleTicks, SWAP_ATTEMPTS);
        }
        Aim aim = FluidPlanner.solve(click.level(), player, target, bucket);
        if (aim == null) {
            return Notify.refuse(player, "unsolved", nameOf(want, bucket));
        }
        return dispatch(click, PlannedAction.fluid(click.hand(), click.hit(), target, bucket), aim, ServerRotation.holds(aim));
    }

    private static InteractionResult pointToSource(Click click, Level schematic, BlockPos target, BlockState want) {
        Component fluid = want.getBlock().getName();
        BlockPos source = FluidPlanner.sourceOf(schematic, target);
        if (source == null) {
            return Notify.refuse(click.player(), "noSource", fluid);
        }
        if (FluidPlanner.isFilled(click.level(), source, want)) {
            return Notify.refuse(click.player(), "alreadyFlowing", fluid, Notify.position(source));
        }
        Debug.log("Flowing {} at {} comes from {}", fluid.getString(), target, source);
        return Notify.refuse(click.player(), "clickSource", fluid, Notify.position(source));
    }

    private static Water supplyWater(Click click, BlockPos pos) {
        LocalPlayer player = click.player();
        Config config = Config.get();
        if (Hotbar.has(player, Items.WATER_BUCKET) || player.hasInfiniteMaterials()) {
            Hotbar.Supply supply = Hotbar.ensure(player, InteractionHand.MAIN_HAND, Items.WATER_BUCKET);
            if (supply == Hotbar.Supply.PULLED && config.swapSettleTicks > 0) {
                return Water.SWAPPING;
            }
            Aim aim = supply == Hotbar.Supply.MISSING ? null : FluidPlanner.solve(click.level(), player, pos, Items.WATER_BUCKET);
            if (aim != null) {
                PlannedAction action = PlannedAction.fluid(InteractionHand.MAIN_HAND, click.hit(), pos, Items.WATER_BUCKET);
                dispatch(click, action, aim, ServerRotation.holds(aim));
                return Water.STARTED;
            }
        }
        if (!config.iceForWater || !Hotbar.has(player, Items.ICE) || IceMelter.problem(player, click.level(), pos) != null) {
            return Water.UNAVAILABLE;
        }
        BlockHitResult hit = HitSearch.supportHit(click.level(), player, pos);
        if (hit == null) {
            return Water.UNAVAILABLE;
        }
        Hotbar.Supply supply = Hotbar.ensure(player, InteractionHand.MAIN_HAND, Items.ICE);
        if (supply == Hotbar.Supply.MISSING) {
            return Water.UNAVAILABLE;
        }
        if (supply == Hotbar.Supply.PULLED && config.swapSettleTicks > 0) {
            return Water.SWAPPING;
        }
        IceMelter.begin(pos);
        PlannedAction action = PlannedAction.block(InteractionHand.MAIN_HAND, hit, click.hit(), pos, false, Items.ICE);
        dispatch(click, action, HitSearch.lookingAt(player, hit), ServerRotation.sees(click.level(), player, hit));
        return Water.STARTED;
    }

    private static InteractionResult explainNoWater(Click click, boolean placing, BlockPos target, BlockState want) {
        LocalPlayer player = click.player();
        Config config = Config.get();
        boolean bucket = Hotbar.has(player, Items.WATER_BUCKET) || player.hasInfiniteMaterials();
        boolean ice = config.iceForWater && Hotbar.has(player, Items.ICE);
        if (!bucket && !ice) {
            return placing && config.strictSchematic
                ? Notify.refuse(player, "missing", nameOf(want, Items.WATER_BUCKET))
                : leave(click);
        }
        String problem = ice && !bucket ? IceMelter.problem(player, click.level(), target) : null;
        if (problem != null) {
            return Notify.refuse(player, problem);
        }
        return Notify.refuse(player, "unsolved", nameOf(want, Items.WATER_BUCKET));
    }

    private static InteractionResult waterFirst(Click click, BlockPos target, BlockState want) {
        if (!Config.get().autoWaterlog || !needsWaterFirst(click.level(), target, want)) {
            return null;
        }
        long now = Executor.now();
        PENDING_WATER.values().removeIf(at -> now - at > WATER_PENDING_TICKS);
        if (PENDING_WATER.containsKey(target)) {
            return deferClick(click, WATER_WAIT_TICKS, WATER_ATTEMPTS);
        }
        return switch (supplyWater(click, target)) {
            case STARTED -> {
                PENDING_WATER.put(target.immutable(), now);
                Debug.log("Filling {} with water before placing {}", target, want.getBlock());
                yield deferClick(click, WATER_WAIT_TICKS, WATER_ATTEMPTS);
            }
            case SWAPPING -> deferClick(click, Config.get().swapSettleTicks, SWAP_ATTEMPTS);
            case UNAVAILABLE -> null;
        };
    }

    private static boolean needsWaterFirst(Level level, BlockPos target, BlockState want) {
        boolean wantsWater = !(want.getBlock() instanceof LiquidBlock) && want.getFluidState().isSourceOfType(Fluids.WATER);
        return wantsWater && !level.getFluidState(target).isSourceOfType(Fluids.WATER);
    }

    private static InteractionResult placeBlock(Click click, ItemStack held, BlockPos target, BlockState want) {
        LocalPlayer player = click.player();
        Goal goal = StateSolver.solve(want, player.getYRot(), player.getXRot());
        if (goal == null || goal.isNothing()) {
            return plain(click, held, target, want);
        }

        BlockHitResult planned = click.hit();
        if (goal.face() != null && Config.get().controlClickedFace) {
            BlockHitResult replanned = HitPlanner.plan(goal, target, click.level(), player, click.hit());
            if (replanned != null) {
                planned = replanned;
            }
        }

        HitSearch.Candidate candidate = HitSearch.best(player, click.hand(), held, click.level(), click.hit(),
            planned, target, want, goal);
        if (candidate == null) {
            Debug.log("No click produces {} at {}", want, target);
            BlockPos support = goal.face() != null ? target.relative(goal.face().getOpposite()) : null;
            return withoutSolution(click, held, target, want, support);
        }
        PlannedAction action = PlannedAction.block(click.hand(), candidate.hit(), click.hit(), target, candidate.shift(),
            held.getItem()).readingHeadYaw(StateSolver.readsHeadYaw(want));
        boolean serverReady = ServerRotation.isSettled(action.readsHeadYaw())
            && producesAtServerRotation(click, action, held, want, candidate.unpaired())
            && (!candidate.inSight() || ServerRotation.sees(click.level(), player, action.hit()));
        return dispatch(click, action, candidate.aim(), serverReady);
    }

    private static InteractionResult withoutSolution(Click click, ItemStack held, BlockPos target, BlockState want,
                                                     BlockPos support) {
        LocalPlayer player = click.player();
        BlockState asAimed = Simulator.predict(player, click.hand(), held, click.hit(), target, null, null,
            player.isShiftKeyDown());
        if (!Config.get().strictSchematic || asAimed == null) {
            return plain(click, held, target, want);
        }
        Component name = nameOf(want, held.getItem());
        if (asAimed.getBlock() != want.getBlock()) {
            return support != null
                ? Notify.refuse(player, "needsSupport", name, Notify.position(support))
                : Notify.refuse(player, "unsolved", name);
        }
        BlockPos partner = HitSearch.chestPartner(target, want);
        if (partner != null && !HitSearch.awaitsChestPartner(click.level(), target, want)) {
            return Notify.refuse(player, "chestPartner", name, Notify.position(partner));
        }
        Property<?> wrong = wrongType(click.level(), target, asAimed, want);
        if (wrong == BlockStateProperties.DOOR_HINGE) {
            return Notify.refuse(player, "wrongHinge", name, Component.literal(valueName(wrong, want)));
        }
        if (wrong != null) {
            return Notify.refuse(player, "wrongType", name, Component.literal(valueName(wrong, want)));
        }
        return plain(click, held, target, want);
    }

    private static Property<?> wrongType(Level level, BlockPos target, BlockState predicted, BlockState want) {
        for (Property<?> property : TYPE_PROPERTIES) {
            if (!want.hasProperty(property) || !predicted.hasProperty(property)
                || predicted.getValue(property).equals(want.getValue(property))) {
                continue;
            }
            boolean slabHalf = property == BlockStateProperties.SLAB_TYPE
                && want.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE;
            boolean firstChestHalf = property == BlockStateProperties.CHEST_TYPE
                && predicted.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.SINGLE
                && HitSearch.awaitsChestPartner(level, target, want);
            if (!slabHalf && !firstChestHalf) {
                return property;
            }
        }
        return null;
    }

    private static <T extends Comparable<T>> String valueName(Property<T> property, BlockState state) {
        return property.getName(state.getValue(property));
    }

    private static boolean waitForClearance(Click click, ItemStack held) {
        if (!Simulator.blockedByPlayer(click.player(), click.hand(), held, click.hit())) {
            return false;
        }
        Debug.log("Waiting for the player to clear {}", click.hit().getBlockPos().relative(click.hit().getDirection()));
        Executor.defer(click.hand(), click.hit(), 1, OBSTRUCTION_ATTEMPTS);
        return true;
    }

    private static boolean producesAtServerRotation(Click click, PlannedAction action, ItemStack held, BlockState want,
                                                    boolean unpaired) {
        BlockState predicted = Simulator.predict(click.player(), click.hand(), held, action.hit(), action.target(),
            ServerRotation.yaw(), ServerRotation.pitch(), action.shift());
        return Simulator.matches(predicted, want, unpaired);
    }

    private static InteractionResult dispatch(Click click, PlannedAction action, Aim aim, boolean serverReady) {
        Executor.submit(click.player(), action, aim, serverReady);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult deferClick(Click click, int ticks, int attempts) {
        Executor.defer(click.hand(), click.hit(), ticks, attempts);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult plain(Click click, ItemStack held, BlockPos target, BlockState want) {
        if (!click.interactive() || click.player().isShiftKeyDown()) {
            return null;
        }
        BlockHitResult quiet = Config.get().controlClickedFace
            ? HitSearch.quiet(click.player(), click.hand(), held, click.level(), click.hit(), target, want)
            : null;
        if (quiet != null) {
            Debug.log("Clicking {} instead of the interactive {}", quiet.getBlockPos(), click.hit().getBlockPos());
            PlannedAction action = PlannedAction.block(click.hand(), quiet, click.hit(), target, false, held.getItem());
            return dispatch(click, action, HitSearch.lookingAt(click.player(), quiet),
                ServerRotation.sees(click.level(), click.player(), quiet));
        }
        return placeNow(click, click.hit(), target, true, held);
    }

    private static InteractionResult leave(Click click) {
        if (!click.shield()) {
            return null;
        }
        LocalPlayer player = click.player();
        boolean holdsAnything = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
        if (player.isShiftKeyDown() && holdsAnything) {
            return null;
        }
        ItemStack held = player.getItemInHand(click.hand());
        if (!(held.getItem() instanceof BlockItem)) {
            return InteractionResult.PASS;
        }
        if (waitForClearance(click, held)) {
            return InteractionResult.CONSUME;
        }
        BlockPos target = new BlockPlaceContext(player, click.hand(), held, click.hit()).getClickedPos();
        return plain(click, held, target, null);
    }

    private static InteractionResult placeNow(Click click, BlockHitResult hit, BlockPos target, boolean shift, ItemStack held) {
        return dispatch(click, PlannedAction.block(click.hand(), hit, click.hit(), target, shift, held.getItem()), Aim.NONE, true);
    }

    private static Component nameOf(BlockState want, Item item) {
        return item == Items.AIR ? want.getBlock().getName() : item.getDefaultInstance().getHoverName();
    }

    private static boolean isPlaceable(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof BlockItem || (item instanceof BucketItem && item != Items.BUCKET);
    }

    private static boolean wantsWaterlogging(Level schematic, Click click, ItemStack held) {
        BlockPos clicked = click.hit().getBlockPos();
        if (!held.is(Items.WATER_BUCKET) || !(click.level().getBlockState(clicked).getBlock() instanceof LiquidBlockContainer)) {
            return false;
        }
        BlockState want = schematic.getBlockState(clicked);
        return want.hasProperty(BlockStateProperties.WATERLOGGED) && want.getValue(BlockStateProperties.WATERLOGGED);
    }
}
