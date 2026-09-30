package com.autorotate.core;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;

public final class Notify {

    private Notify() {
    }

    public static InteractionResult refuse(LocalPlayer player, String reason, Object... args) {
        Component message = Component.translatable("msg.autorotate." + reason, args);
        player.displayClientMessage(message, true);
        Debug.log("Refused: {}", message.getString());
        return InteractionResult.CONSUME;
    }

    public static Component position(BlockPos pos) {
        return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }
}
