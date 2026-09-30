package com.autorotate;

import com.autorotate.config.Config;
import com.autorotate.core.Executor;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AutoRotate implements ClientModInitializer {

    public static final String MOD_ID = "autorotate";
    public static final Logger LOG = LoggerFactory.getLogger("AutoRotate");

    private static KeyMapping toggleKey;

    public static KeyMapping toggleKey() {
        return toggleKey;
    }

    @Override
    public void onInitializeClient() {
        Config.load();
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        toggleKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.autorotate.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, category));
        ClientTickEvents.END_CLIENT_TICK.register(AutoRotate::onEndTick);
    }

    private static void onEndTick(Minecraft minecraft) {
        while (toggleKey.consumeClick()) {
            toggle(minecraft);
        }
        Executor.tick(minecraft);
    }

    private static void toggle(Minecraft minecraft) {
        Config config = Config.get();
        config.enabled = !config.enabled;
        Config.save();
        if (minecraft.player != null) {
            String key = config.enabled ? "msg.autorotate.on" : "msg.autorotate.off";
            minecraft.player.displayClientMessage(Component.translatable(key), true);
        }
    }
}
