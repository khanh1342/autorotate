package com.autorotate.core;

import com.autorotate.AutoRotate;
import com.autorotate.config.Config;

public final class Debug {

    private Debug() {
    }

    public static void log(String format, Object... args) {
        if (Config.get().debugLog) {
            AutoRotate.LOG.info(format, args);
        }
    }
}
