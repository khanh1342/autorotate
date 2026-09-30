package com.autorotate.mixin;

import com.autorotate.core.Executor;
import com.autorotate.core.RotationSession;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void autorotate$turn(double yaw, double pitch, CallbackInfo ci) {
        if (!((Object) this instanceof LocalPlayer)) {
            return;
        }
        RotationSession session = Executor.session();
        if (session != null && session.absorbTurn(yaw, pitch)) {
            ci.cancel();
        }
    }
}
