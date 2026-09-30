package com.autorotate.mixin;

import com.autorotate.core.MoveFix;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "travel", at = @At("HEAD"))
    private void autorotate$beforeTravel(Vec3 movement, CallbackInfo ci) {
        MoveFix.enter((Player) (Object) this);
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void autorotate$afterTravel(Vec3 movement, CallbackInfo ci) {
        MoveFix.exit((Player) (Object) this);
    }
}
