package com.autorotate.solver;

import java.util.List;

public enum SneakRule {
    ANY,
    ON,
    OFF,
    EITHER;

    public List<Boolean> options(boolean supportInteractive, boolean playerShift) {
        return switch (this) {
            case ANY -> List.of(supportInteractive || playerShift);
            case ON -> List.of(true);
            case OFF -> supportInteractive ? List.of() : List.of(false);
            case EITHER -> supportInteractive ? List.of(true) : List.of(playerShift, !playerShift);
        };
    }
}
