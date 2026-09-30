package com.autorotate.config;

import com.autorotate.AutoRotate;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(AutoRotate.MOD_ID + ".json");

    private static Config instance = new Config();

    public boolean enabled = true;
    public boolean controlClickedFace = true;
    public boolean strictSchematic = true;
    public boolean noBlockInteraction = true;
    public boolean deferToBlockInteraction = true;
    public boolean autoSwitchHotbar = true;
    public boolean swapFromInventory = true;
    public int swapSettleTicks = 2;
    public boolean iceForWater = true;
    public boolean autoWaterlog = true;
    public boolean stackLayers = true;
    public boolean respectLayerRange = false;
    public boolean adjustDiodes = true;
    public int headSettleTicks = 2;
    public int maxSpoofWaitTicks = 3;
    public double maxAimStepDegrees = 0.0;
    public boolean moveFix = true;
    public int maxAimTicks = 12;
    public int placeCooldownMs = 0;
    public boolean onePlacePerTick = true;
    public boolean debugLog = false;
    public boolean realAim = false;
    public int realAimSettleTicks = 3;

    public static Config get() {
        return instance;
    }

    public static void load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                Config loaded = GSON.fromJson(reader, Config.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (IOException | JsonParseException e) {
                AutoRotate.LOG.warn("Could not read {}, using defaults", PATH, e);
            }
        }
        instance.sanitize();
        save();
    }

    public static void apply() {
        instance.sanitize();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            AutoRotate.LOG.warn("Could not write {}", PATH, e);
        }
    }

    private void sanitize() {
        headSettleTicks = Math.max(0, headSettleTicks);
        maxSpoofWaitTicks = Math.max(1, maxSpoofWaitTicks);
        maxAimStepDegrees = Math.max(0.0, maxAimStepDegrees);
        maxAimTicks = Math.max(1, maxAimTicks);
        placeCooldownMs = Math.max(0, placeCooldownMs);
        swapSettleTicks = Math.max(0, swapSettleTicks);
        realAimSettleTicks = Math.max(0, realAimSettleTicks);
    }
}
