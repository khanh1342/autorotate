package com.autorotate.mixin;

import com.autorotate.core.MoveFix;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "jumpFromGround", at = @At("HEAD"))
    private void autorotate$beforeJump(CallbackInfo ci) {
        MoveFix.enter((LivingEntity) (Object) this);
    }

    @Inject(method = "jumpFromGround", at = @At("RETURN"))
    private void autorotate$afterJump(CallbackInfo ci) {
        MoveFix.exit((LivingEntity) (Object) this);
    }
}
