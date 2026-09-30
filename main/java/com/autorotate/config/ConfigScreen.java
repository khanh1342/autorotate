package com.autorotate.config;

import com.autorotate.AutoRotate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class ConfigScreen extends Screen {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int INVALID = 0xFFFF5555;
    private static final int MAX_CONTENT_WIDTH = 360;
    private static final int FIELD_WIDTH = 110;
    private static final int TOP = 58;

    private enum Tab {
        GENERAL,
        HOTBAR,
        ROTATION,
        BUILDING;

        private Component title() {
            return Component.translatable("autorotate.config.tab." + name().toLowerCase(Locale.ROOT));
        }
    }

    private sealed interface Setting permits Toggle, Whole, Decimal {
        String name();
    }

    private record Toggle(String name, Function<Config, Boolean> getter, BiConsumer<Config, Boolean> setter)
        implements Setting {
    }

    private record Whole(String name, Function<Config, Integer> getter, BiConsumer<Config, Integer> setter,
                         int min, int max) implements Setting {
    }

    private record Decimal(String name, Function<Config, Double> getter, BiConsumer<Config, Double> setter,
                           double min, double max) implements Setting {
    }

    private record Label(Component text, int x, int y) {
    }

    private final Screen parent;
    private final List<Label> labels = new ArrayList<>();
    private Tab tab = Tab.GENERAL;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("autorotate.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        labels.clear();
        int contentWidth = Math.min(MAX_CONTENT_WIDTH, width - 40);
        int left = (width - contentWidth) / 2;
        addTabs(left, contentWidth);

        List<Setting> settings = settings(tab);
        int rowHeight = Math.max(21, Math.min(24, (height - TOP - 70) / Math.max(1, settings.size())));
        int y = TOP;
        for (Setting setting : settings) {
            addSetting(setting, left, contentWidth, y);
            y += rowHeight;
        }

        int half = contentWidth / 2 - 2;
        int bottom = height - 28;
        addRenderableWidget(Button.builder(Component.translatable("autorotate.config.reset"), button -> resetTab())
            .bounds(left, bottom, half, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
            .bounds(left + contentWidth - half, bottom, half, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, WHITE);
        for (Label label : labels) {
            graphics.drawString(font, label.text(), label.x(), label.y(), WHITE);
        }
        Component hint = Component.translatable("autorotate.config.hint", AutoRotate.toggleKey().getTranslatedKeyMessage());
        graphics.drawCenteredString(font, hint, width / 2, height - 42, GRAY);
    }

    @Override
    public void onClose() {
        Config.apply();
        minecraft.setScreen(parent);
    }

    private void addTabs(int left, int contentWidth) {
        Tab[] tabs = Tab.values();
        int tabWidth = (contentWidth - (tabs.length - 1) * 4) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab target = tabs[i];
            MutableComponent text = target.title().copy()
                .withStyle(target == tab ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
            addRenderableWidget(Button.builder(text, button -> switchTo(target))
                .bounds(left + i * (tabWidth + 4), 30, tabWidth, 20).build());
        }
    }

    private void switchTo(Tab target) {
        Config.apply();
        tab = target;
        rebuildWidgets();
    }

    private void addSetting(Setting setting, int left, int contentWidth, int y) {
        Config config = Config.get();
        Tooltip tooltip = Tooltip.create(Component.translatable("autorotate.config." + setting.name() + ".tooltip"));
        switch (setting) {
            case Toggle toggle -> addRenderableWidget(Button.builder(toggleText(toggle), button -> {
                    toggle.setter().accept(config, !toggle.getter().apply(config));
                    button.setMessage(toggleText(toggle));
                })
                .bounds(left, y, contentWidth, 20).tooltip(tooltip).build());
            case Whole whole -> addField(whole.name(), String.valueOf(whole.getter().apply(config)), left, contentWidth, y,
                tooltip, text -> applyWhole(whole, text));
            case Decimal decimal -> addField(decimal.name(), formatDecimal(decimal.getter().apply(config)), left,
                contentWidth, y, tooltip, text -> applyDecimal(decimal, text));
        }
    }

    private void addField(String name, String value, int left, int contentWidth, int y, Tooltip tooltip,
                          Function<String, Boolean> apply) {
        Component label = Component.translatable("autorotate.config." + name);
        labels.add(new Label(label, left + 4, y + 6));
        EditBox box = new EditBox(font, left + contentWidth - FIELD_WIDTH, y, FIELD_WIDTH, 20, label);
        box.setMaxLength(8);
        box.setValue(value);
        box.setFilter(text -> text.matches("[0-9.]*"));
        box.setResponder(text -> box.setTextColor(apply.apply(text) ? WHITE : INVALID));
        box.setTooltip(tooltip);
        addRenderableWidget(box);
    }

    private static boolean applyWhole(Whole whole, String text) {
        try {
            int value = Integer.parseInt(text);
            if (value < whole.min() || value > whole.max()) {
                return false;
            }
            whole.setter().accept(Config.get(), value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean applyDecimal(Decimal decimal, String text) {
        try {
            double value = Double.parseDouble(text);
            if (value < decimal.min() || value > decimal.max()) {
                return false;
            }
            decimal.setter().accept(Config.get(), value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void resetTab() {
        Config defaults = new Config();
        Config config = Config.get();
        for (Setting setting : settings(tab)) {
            switch (setting) {
                case Toggle toggle -> toggle.setter().accept(config, toggle.getter().apply(defaults));
                case Whole whole -> whole.setter().accept(config, whole.getter().apply(defaults));
                case Decimal decimal -> decimal.setter().accept(config, decimal.getter().apply(defaults));
            }
        }
        Config.apply();
        rebuildWidgets();
    }

    private static Component toggleText(Toggle toggle) {
        boolean on = toggle.getter().apply(Config.get());
        Component state = Component.literal(on ? "[ ON ]" : "[ OFF ]")
            .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
        return Component.translatable("autorotate.config." + toggle.name()).append(": ").append(state);
    }

    private static String formatDecimal(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static List<Setting> settings(Tab tab) {
        return switch (tab) {
            case GENERAL -> List.of(
                new Toggle("enabled", c -> c.enabled, (c, v) -> c.enabled = v),
                new Toggle("strictSchematic", c -> c.strictSchematic, (c, v) -> c.strictSchematic = v),
                new Toggle("noBlockInteraction", c -> c.noBlockInteraction, (c, v) -> c.noBlockInteraction = v),
                new Toggle("deferToBlockInteraction", c -> c.deferToBlockInteraction, (c, v) -> c.deferToBlockInteraction = v),
                new Toggle("controlClickedFace", c -> c.controlClickedFace, (c, v) -> c.controlClickedFace = v),
                new Toggle("debugLog", c -> c.debugLog, (c, v) -> c.debugLog = v));
            case HOTBAR -> List.of(
                new Toggle("autoSwitchHotbar", c -> c.autoSwitchHotbar, (c, v) -> c.autoSwitchHotbar = v),
                new Toggle("swapFromInventory", c -> c.swapFromInventory, (c, v) -> c.swapFromInventory = v),
                new Whole("swapSettleTicks", c -> c.swapSettleTicks, (c, v) -> c.swapSettleTicks = v, 0, 20),
                new Toggle("adjustDiodes", c -> c.adjustDiodes, (c, v) -> c.adjustDiodes = v),
                new Whole("placeCooldownMs", c -> c.placeCooldownMs, (c, v) -> c.placeCooldownMs = v, 0, 5000),
                new Toggle("onePlacePerTick", c -> c.onePlacePerTick, (c, v) -> c.onePlacePerTick = v));
            case ROTATION -> List.of(
                new Toggle("moveFix", c -> c.moveFix, (c, v) -> c.moveFix = v),
                new Toggle("realAim", c -> c.realAim, (c, v) -> c.realAim = v),
                new Whole("headSettleTicks", c -> c.headSettleTicks, (c, v) -> c.headSettleTicks = v, 0, 20),
                new Whole("maxSpoofWaitTicks", c -> c.maxSpoofWaitTicks, (c, v) -> c.maxSpoofWaitTicks = v, 1, 40),
                new Decimal("maxAimStepDegrees", c -> c.maxAimStepDegrees, (c, v) -> c.maxAimStepDegrees = v, 0.0, 180.0),
                new Whole("maxAimTicks", c -> c.maxAimTicks, (c, v) -> c.maxAimTicks = v, 1, 100),
                new Whole("realAimSettleTicks", c -> c.realAimSettleTicks, (c, v) -> c.realAimSettleTicks = v, 0, 20));
            case BUILDING -> List.of(
                new Toggle("autoWaterlog", c -> c.autoWaterlog, (c, v) -> c.autoWaterlog = v),
                new Toggle("iceForWater", c -> c.iceForWater, (c, v) -> c.iceForWater = v),
                new Toggle("stackLayers", c -> c.stackLayers, (c, v) -> c.stackLayers = v),
                new Toggle("respectLayerRange", c -> c.respectLayerRange, (c, v) -> c.respectLayerRange = v));
        };
    }
}
