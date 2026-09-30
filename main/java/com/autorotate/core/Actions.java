package com.autorotate.core;

import com.autorotate.AutoRotate;
import com.autorotate.solver.Simulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

public final class Actions {

    private static boolean replaying;

    private Actions() {
    }

    public static boolean isReplaying() {
        return replaying;
    }

    public static void perform(LocalPlayer player, PlannedAction action) {
        try {
            InteractionResult result = action.isFluid()
                ? useItem(player, action.hand())
                : useOn(player, action.hand(), action.hit(), action.shift());
            Debug.log("Placed {} at {} shift={} -> {}", action.item(), action.target(), action.shift(), result);
        } catch (RuntimeException e) {
            AutoRotate.LOG.warn("Placement at {} failed", action.target(), e);
        }
    }

    public static InteractionResult useOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit, boolean shift) {
        return replay(player, hand, () -> withShift(player, shift, () -> gameMode().useItemOn(player, hand, hit)));
    }

    public static InteractionResult useItem(LocalPlayer player, InteractionHand hand) {
        return replay(player, hand, () -> gameMode().useItem(player, hand));
    }

    private static InteractionResult replay(LocalPlayer player, InteractionHand hand, Supplier<InteractionResult> action) {
        InteractionResult result;
        replaying = true;
        try {
            result = action.get();
        } finally {
            replaying = false;
            Cooldown.markSent();
        }
        if (result instanceof InteractionResult.Success success
            && success.swingSource() == InteractionResult.SwingSource.CLIENT) {
            player.swing(hand);
        }
        return result;
    }

    private static <T> T withShift(LocalPlayer player, boolean shift, Supplier<T> action) {
        Input saved = player.input.keyPresses;
        if (saved.shift() == shift) {
            return action.get();
        }
        sendInput(player, Simulator.withShift(saved, shift));
        try {
            return action.get();
        } finally {
            sendInput(player, saved);
        }
    }

    private static void sendInput(LocalPlayer player, Input input) {
        player.input.keyPresses = input;
        player.setShiftKeyDown(input.shift());
        player.connection.send(new ServerboundPlayerInputPacket(input));
    }

    private static MultiPlayerGameMode gameMode() {
        return Minecraft.getInstance().gameMode;
    }
}
