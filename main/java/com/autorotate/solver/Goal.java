package com.autorotate.solver;

import net.minecraft.core.Direction;

public record Goal(Aim aim, Direction face, Bias bias, SneakRule sneak) {

    public static Goal aim(Aim aim) {
        return new Goal(aim, null, Bias.NONE, SneakRule.ANY);
    }

    public static Goal face(Direction face) {
        return new Goal(Aim.NONE, face, Bias.NONE, SneakRule.ANY);
    }

    public static Goal of(Aim aim, Direction face, Bias bias) {
        return new Goal(aim, face, bias, SneakRule.ANY);
    }

    public Goal withSneak(SneakRule rule) {
        return new Goal(aim, face, bias, rule);
    }

    public boolean isNothing() {
        return aim.isEmpty() && face == null && bias.isEmpty() && sneak == SneakRule.ANY;
    }
}
