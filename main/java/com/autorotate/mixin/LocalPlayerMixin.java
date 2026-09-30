package com.autorotate.mixin;

import com.autorotate.access.LastSentPosition;
import com.autorotate.core.Executor;
import com.autorotate.core.BlockBreaker;
import com.autorotate.core.MoveFix;
import com.autorotate.core.RotationSession;
import com.autorotate.core.ServerRotation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin implements LastSentPosition {

    @Shadow
    private float yRotLast;

    @Shadow
    private float xRotLast;

    @Shadow
    private double xLast;

    @Shadow
    private double yLast;

    @Shadow
    private double zLast;

    @Override
    public Vec3 autorotate$lastSentPosition() {
        return new Vec3(xLast, yLast, zLast);
    }

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V", shift = At.Shift.AFTER))
    private void autorotate$afterInput(CallbackInfo ci) {
        MoveFix.remapInput((LocalPlayer) (Object) this);
    }

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void autorotate$beforeSend(CallbackInfo ci) {
        RotationSession session = Executor.session();
        if (session != null) {
            session.beforeSend((LocalPlayer) (Object) this);
        } else {
            BlockBreaker.beforeSend((LocalPlayer) (Object) this);
        }
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void autorotate$afterSend(CallbackInfo ci) {
        BlockBreaker.afterSend((LocalPlayer) (Object) this, yRotLast, xRotLast);
        ServerRotation.onSent(yRotLast, xRotLast);
        RotationSession session = Executor.session();
        if (session != null) {
            session.afterSend((LocalPlayer) (Object) this, yRotLast, xRotLast);
        }
    }
}
