package com.autorotate.solver;

import com.autorotate.access.LastSentPosition;
import com.autorotate.mixin.BlockItemAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Set;

public final class Simulator {

    private static final Set<Property<?>> WORLD_DECIDED = Set.of(
        BlockStateProperties.POWERED,
        BlockStateProperties.LIT,
        BlockStateProperties.WATERLOGGED,
        BlockStateProperties.TRIGGERED,
        BlockStateProperties.EXTENDED,
        BlockStateProperties.OPEN,
        BlockStateProperties.NORTH,
        BlockStateProperties.SOUTH,
        BlockStateProperties.EAST,
        BlockStateProperties.WEST,
        BlockStateProperties.UP,
        BlockStateProperties.DOWN,
        BlockStateProperties.STAIRS_SHAPE,
        BlockStateProperties.SNOWY,
        BlockStateProperties.DISTANCE,
        BlockStateProperties.PERSISTENT,
        BlockStateProperties.DELAY,
        BlockStateProperties.MODE_COMPARATOR,
        BlockStateProperties.LOCKED,
        BlockStateProperties.NOTE,
        BlockStateProperties.POWER);

    public static final Set<IntegerProperty> STEPPED = Set.of(
        BlockStateProperties.LAYERS,
        BlockStateProperties.CANDLES,
        BlockStateProperties.PICKLES,
        BlockStateProperties.EGGS,
        BlockStateProperties.FLOWER_AMOUNT,
        BlockStateProperties.SEGMENT_AMOUNT);

    private Simulator() {
    }

    public static BlockState predict(LocalPlayer player, InteractionHand hand, ItemStack stack, BlockHitResult hit,
                                     BlockPos target, Float yaw, Float pitch, boolean shift) {
        if (!(stack.getItem() instanceof BlockItem item)) {
            return null;
        }
        float realYaw = player.getYRot();
        float realPitch = player.getXRot();
        float realHeadYaw = player.getYHeadRot();
        Input realInput = player.input.keyPresses;
        try {
            float useYaw = yaw != null ? yaw : realYaw;
            player.setYRot(useYaw);
            player.setYHeadRot(useYaw);
            if (pitch != null) {
                player.setXRot(pitch);
            }
            if (realInput.shift() != shift) {
                player.input.keyPresses = withShift(realInput, shift);
            }
            return placementState(player, hand, stack, hit, target, item);
        } catch (RuntimeException e) {
            return null;
        } finally {
            player.setYRot(realYaw);
            player.setXRot(realPitch);
            player.setYHeadRot(realHeadYaw);
            player.input.keyPresses = realInput;
        }
    }

    public static boolean blockedByPlayer(LocalPlayer player, InteractionHand hand, ItemStack stack, BlockHitResult hit) {
        if (!(stack.getItem() instanceof BlockItem item)) {
            return false;
        }
        BlockPlaceContext context = item.updatePlacementContext(new BlockPlaceContext(player, hand, stack, hit));
        if (context == null || !context.canPlace()) {
            return false;
        }
        BlockState state = item.getBlock().getStateForPlacement(context);
        if (state == null) {
            return false;
        }
        BlockPos pos = context.getClickedPos();
        boolean clearNow = player.level().isUnobstructed(state, pos, CollisionContext.placementContext(player));
        return !clearNow || !clearAtLastSentPosition(player, pos, state);
    }

    public static boolean matches(BlockState predicted, BlockState wanted) {
        return matches(predicted, wanted, false);
    }

    public static boolean matches(BlockState predicted, BlockState wanted, boolean unpaired) {
        if (predicted == null || predicted.getBlock() != wanted.getBlock()) {
            return false;
        }
        for (Property<?> property : wanted.getProperties()) {
            if (WORLD_DECIDED.contains(property) || !predicted.hasProperty(property)) {
                continue;
            }
            if (unpaired && property == BlockStateProperties.CHEST_TYPE
                && predicted.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.SINGLE) {
                continue;
            }
            if (!isStep(property, predicted, wanted) && !predicted.getValue(property).equals(wanted.getValue(property))) {
                return false;
            }
        }
        return true;
    }

    public static Input withShift(Input input, boolean shift) {
        return new Input(input.forward(), input.backward(), input.left(), input.right(), input.jump(), shift, input.sprint());
    }

    private static BlockState placementState(LocalPlayer player, InteractionHand hand, ItemStack stack,
                                             BlockHitResult hit, BlockPos target, BlockItem item) {
        BlockPlaceContext context = new BlockPlaceContext(player, hand, stack, hit);
        if (!context.canPlace()) {
            return null;
        }
        context = item.updatePlacementContext(context);
        if (context == null || !context.getClickedPos().equals(target) || !context.canPlace()) {
            return null;
        }
        BlockState predicted = ((BlockItemAccessor) item).autorotate$getPlacementState(context);
        if (predicted == null || !clearAtLastSentPosition(player, target, predicted)) {
            return null;
        }
        return predicted;
    }

    private static boolean clearAtLastSentPosition(LocalPlayer player, BlockPos target, BlockState state) {
        Vec3 lastSent = ((LastSentPosition) player).autorotate$lastSentPosition();
        Vec3 now = player.position();
        if (lastSent.distanceToSqr(now) < 1.0E-8) {
            return true;
        }
        try {
            player.setPos(lastSent);
            return player.level().isUnobstructed(state, target, CollisionContext.placementContext(player));
        } finally {
            player.setPos(now);
        }
    }

    private static boolean isStep(Property<?> property, BlockState predicted, BlockState wanted) {
        if (property == BlockStateProperties.SLAB_TYPE) {
            return wanted.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE;
        }
        if (property instanceof IntegerProperty count && STEPPED.contains(count)) {
            return predicted.getValue(count) <= wanted.getValue(count);
        }
        return false;
    }
}
