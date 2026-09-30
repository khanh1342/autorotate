package com.autorotate.mixin;

import com.autorotate.core.Actions;
import com.autorotate.core.BlockBreaker;
import com.autorotate.core.Cooldown;
import com.autorotate.core.Placer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void autorotate$useItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                      CallbackInfoReturnable<InteractionResult> cir) {
        if (Actions.isReplaying() || player == null || hit == null) {
            return;
        }
        InteractionResult result = Placer.onUseItemOn(player, hand, hit);
        if (result != null) {
            cir.setReturnValue(result);
        } else {
            Cooldown.markSent();
        }
    }

    @Inject(method = "stopDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void autorotate$keepBreaking(CallbackInfo ci) {
        if (BlockBreaker.isBreaking() && !BlockBreaker.isStopping()) {
            ci.cancel();
        }
    }
}
