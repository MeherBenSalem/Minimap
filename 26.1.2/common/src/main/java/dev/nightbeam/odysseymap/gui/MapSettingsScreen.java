package dev.nightbeam.odysseymap.gui;

import dev.nightbeam.odysseymap.client.RuntimeClientState;
import dev.nightbeam.odysseymap.config.OdysseyConfig;
import dev.nightbeam.odysseymap.config.ConfigValue;
import dev.nightbeam.odysseymap.world.OdysseyMapClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class MapSettingsScreen extends Screen {
    private final Screen parent;
    private int currentTab = 0;

    private final List<Button> tabButtons = new ArrayList<>();
    private final List<List<SettingWidget>> tabWidgets = new ArrayList<>();
    private EditBox borderHexBox;

    public MapSettingsScreen(Screen parent) {
        super(Component.literal("Map Settings"));
        this.parent = parent;
    }

    private interface SettingOption {
        String label();
        String getValueText();
        void onClick();
    }

    private static class SettingWidget {
        final Button button;
        final SettingOption option;

        SettingWidget(Button button, SettingOption option) {
            this.button = button;
            this.option = option;
        }
    }

    @Override
    protected void init() {
        tabButtons.clear();
        tabWidgets.clear();

        int centerX = width / 2;
        int panelTop = Math.max(10, height / 2 - 110);

        // 1. Create Tab Buttons
        for (int i = 0; i < 4; i++) {
            final int tabIdx = i;
            String tabName = switch (i) {
                case 0 -> "Minimap";
                case 1 -> "Markers";
                case 2 -> "Fullscreen";
                case 3 -> "Performance";
                default -> "";
            };
            Button tabBtn = Button.builder(Component.literal(tabName), b -> {
                currentTab = tabIdx;
                updateVisibility();
            }).bounds(centerX - 180 + i * 90, panelTop + 10, 88, 20).build();
            tabButtons.add(tabBtn);
            addRenderableWidget(tabBtn);
        }

        // 2. Define setting options per tab
        List<SettingOption> tab0Options = List.of(
            new BooleanOption("Minimap Enabled", OdysseyConfig.ENABLED),
            new ChoiceIntOption("Minimap Size", OdysseyConfig.MINIMAP_SIZE, new int[]{64, 96, 128, 160, 192, 224, 256, 320, 384, 448, 512}),
            new EnumOption<>("Position", OdysseyConfig.POSITION, OdysseyConfig.ScreenPosition.values()),
            new EnumOption<>("Shape", OdysseyConfig.SHAPE, OdysseyConfig.MinimapShape.values()),
            new ChoiceIntOption("Zoom (Blocks/Px)", OdysseyConfig.ZOOM_BLOCKS_PER_PIXEL, new int[]{1, 2, 3, 4, 5, 6, 8, 10, 12, 16}, () -> {
                RuntimeClientState.resetZoom();
                if (OdysseyMapClient.getTileCache() != null) {
                    OdysseyMapClient.getTileCache().markAllDirty();
                }
            }),
            new EnumOption<>("Rotation Mode", OdysseyConfig.ROTATION_MODE, OdysseyConfig.RotationMode.values()),
            new ChoiceDoubleOption("Transparency", OdysseyConfig.TRANSPARENCY, new double[]{0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0}),
            new BooleanOption("Hide in Screens", OdysseyConfig.HIDE_WHEN_SCREEN_OPEN)
        );

        List<SettingOption> tab1Options = List.of(
            new BooleanOption("Show Player Head", OdysseyConfig.SHOW_PLAYER_HEAD),
            new BooleanOption("Show Players", OdysseyConfig.SHOW_PLAYERS),
            new BooleanOption("Show Waypoints", OdysseyConfig.SHOW_WAYPOINTS),
            new BooleanOption("Show Death Points", OdysseyConfig.SHOW_DEATH),
            new BooleanOption("Show Beds", OdysseyConfig.SHOW_BEDS),
            new BooleanOption("Show Portals", OdysseyConfig.SHOW_PORTALS),
            new BooleanOption("Show Structures", OdysseyConfig.SHOW_STRUCTURES),
            new BooleanOption("Show Entities", OdysseyConfig.SHOW_ENTITIES),
            new BooleanOption("Show Marker Distance", OdysseyConfig.SHOW_MARKER_DISTANCE),
            new BooleanOption("Clamp Markers", OdysseyConfig.STICK_MARKERS_TO_BORDER)
        );

        List<SettingOption> tab2Options = List.of(
            new BooleanOption("Fullscreen Map", OdysseyConfig.MAP_FULLSCREEN_ENABLED),
            new BooleanOption("Show Coordinates", OdysseyConfig.MAP_SHOW_COORDINATES),
            new BooleanOption("Show Waypoints", OdysseyConfig.MAP_SHOW_WAYPOINTS),
            new BooleanOption("Show Player Marker", OdysseyConfig.MAP_SHOW_PLAYER_MARKER),
            new ChoiceIntOption("Default Zoom", OdysseyConfig.FULLSCREEN_DEFAULT_ZOOM, new int[]{1, 2, 3, 4, 5, 6, 8, 10, 12, 16}),
            new BooleanOption("Show Grid", OdysseyConfig.FULLSCREEN_SHOW_GRID),
            new ChoiceIntOption("Max Waypoints", OdysseyConfig.MAP_MAX_WAYPOINTS_RENDERED, new int[]{10, 25, 50, 100, 150, 200, 300, 400, 500}),
            new BooleanOption("Safe Render Mode", OdysseyConfig.MAP_SAFE_RENDER_MODE)
        );

        List<SettingOption> tab3Options = List.of(
            new BooleanOption("Show Compass", OdysseyConfig.SHOW_COMPASS),
            new BooleanOption("Show Coordinates", OdysseyConfig.SHOW_COORDINATES),
            new EnumOption<>("Performance Level", OdysseyConfig.PERFORMANCE_MODE, OdysseyConfig.PerformanceMode.values()),
            new ChoiceIntOption("Columns Per Tick", OdysseyConfig.COLUMNS_PER_TICK, new int[]{32, 64, 128, 256, 512, 1024, 2048, 4096}),
            new ChoiceIntOption("Scan Interval", OdysseyConfig.SCAN_INTERVAL_FRAMES, new int[]{1, 2, 3, 4, 5, 8, 10, 12, 15, 20}),
            new ChoiceIntOption("Border Thickness", OdysseyConfig.BORDER_THICKNESS, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8})
        );

        List<List<SettingOption>> allTabsOptions = List.of(tab0Options, tab1Options, tab2Options, tab3Options);

        // 3. Instantiate buttons
        for (int t = 0; t < allTabsOptions.size(); t++) {
            List<SettingOption> options = allTabsOptions.get(t);
            List<SettingWidget> widgets = new ArrayList<>();
            for (int j = 0; j < options.size(); j++) {
                SettingOption opt = options.get(j);
                int row = j / 2;
                int col = j % 2;
                int x = (col == 0) ? (centerX - 155) : (centerX + 5);
                int y = panelTop + 40 + row * 24;

                Button btn = Button.builder(Component.literal(opt.label() + ": " + opt.getValueText()), b -> {
                    opt.onClick();
                    b.setMessage(Component.literal(opt.label() + ": " + opt.getValueText()));
                }).bounds(x, y, 150, 20).build();

                widgets.add(new SettingWidget(btn, opt));
                addRenderableWidget(btn);
            }
            tabWidgets.add(widgets);
        }

        // 4. Create Border Hex EditBox on Tab 3
        int borderY = panelTop + 40 + 4 * 24 + 10;
        borderHexBox = new EditBox(font, centerX - 150, borderY, 140, 18, Component.empty());
        borderHexBox.setValue(String.format("#%08X", OdysseyConfig.BORDER_COLOR.get()));
        borderHexBox.setMaxLength(9);
        borderHexBox.setResponder(text -> {
            String hex = text.trim();
            if (hex.startsWith("#")) hex = hex.substring(1);
            try {
                long val = Long.parseLong(hex, 16);
                OdysseyConfig.BORDER_COLOR.set((int) val);
                OdysseyConfig.save();
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(borderHexBox);

        // 5. Add Done Button
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(centerX - 75, panelTop + 180, 150, 20).build());

        updateVisibility();
    }

    private void updateVisibility() {
        for (int i = 0; i < tabButtons.size(); i++) {
            tabButtons.get(i).active = (i != currentTab);
        }
        for (int i = 0; i < tabWidgets.size(); i++) {
            boolean visible = (i == currentTab);
            for (SettingWidget sw : tabWidgets.get(i)) {
                sw.button.visible = visible;
            }
        }
        if (borderHexBox != null) {
            borderHexBox.setVisible(currentTab == 3);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractBackground(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        int panelTop = Math.max(10, height / 2 - 110);

        graphics.centeredText(font, title, centerX, panelTop - 8, 0xFFFFFFFF);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // Custom render for Tab 3 (Hex Color input preview)
        if (currentTab == 3 && borderHexBox != null) {
            graphics.text(font, Component.literal("Border Color (Hex ARGB)"), centerX - 150, borderHexBox.getY() - 10, 0xFFAAAAAA, false);
            // Draw Color Swatch Preview
            int px = borderHexBox.getX() + 145;
            int py = borderHexBox.getY() + 1;
            graphics.fill(px, py, px + 16, py + 16, OdysseyConfig.BORDER_COLOR.get());
            graphics.outline(px - 1, py - 1, 18, 18, 0xFFFFFFFF);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Helper implementations of SettingOption
    private static class BooleanOption implements SettingOption {
        private final String label;
        private final ConfigValue<Boolean> configValue;

        BooleanOption(String label, ConfigValue<Boolean> configValue) {
            this.label = label;
            this.configValue = configValue;
        }

        @Override
        public String label() { return label; }

        @Override
        public String getValueText() {
            return configValue.get() ? "ON" : "OFF";
        }

        @Override
        public void onClick() {
            configValue.set(!configValue.get());
            OdysseyConfig.save();
        }
    }

    private static class EnumOption<E extends Enum<E>> implements SettingOption {
        private final String label;
        private final ConfigValue<E> configValue;
        private final E[] values;

        EnumOption(String label, ConfigValue<E> configValue, E[] values) {
            this.label = label;
            this.configValue = configValue;
            this.values = values;
        }

        @Override
        public String label() { return label; }

        @Override
        public String getValueText() {
            return configValue.get().name();
        }

        @Override
        public void onClick() {
            E current = configValue.get();
            int nextIdx = (current.ordinal() + 1) % values.length;
            configValue.set(values[nextIdx]);
            OdysseyConfig.save();
        }
    }

    private static class ChoiceIntOption implements SettingOption {
        private final String label;
        private final ConfigValue<Integer> configValue;
        private final int[] choices;
        private final Runnable onChange;

        ChoiceIntOption(String label, ConfigValue<Integer> configValue, int[] choices) {
            this(label, configValue, choices, null);
        }

        ChoiceIntOption(String label, ConfigValue<Integer> configValue, int[] choices, Runnable onChange) {
            this.label = label;
            this.configValue = configValue;
            this.choices = choices;
            this.onChange = onChange;
        }

        @Override
        public String label() { return label; }

        @Override
        public String getValueText() {
            return String.valueOf(configValue.get());
        }

        @Override
        public void onClick() {
            int current = configValue.get();
            int nextIdx = 0;
            for (int i = 0; i < choices.length; i++) {
                if (choices[i] == current) {
                    nextIdx = (i + 1) % choices.length;
                    break;
                }
            }
            configValue.set(choices[nextIdx]);
            OdysseyConfig.save();
            if (onChange != null) {
                onChange.run();
            }
        }
    }

    private static class ChoiceDoubleOption implements SettingOption {
        private final String label;
        private final ConfigValue<Double> configValue;
        private final double[] choices;

        ChoiceDoubleOption(String label, ConfigValue<Double> configValue, double[] choices) {
            this.label = label;
            this.configValue = configValue;
            this.choices = choices;
        }

        @Override
        public String label() { return label; }

        @Override
        public String getValueText() {
            return String.format("%.2f", configValue.get());
        }

        @Override
        public void onClick() {
            double current = configValue.get();
            int nextIdx = 0;
            for (int i = 0; i < choices.length; i++) {
                if (Math.abs(choices[i] - current) < 0.01) {
                    nextIdx = (i + 1) % choices.length;
                    break;
                }
            }
            configValue.set(choices[nextIdx]);
            OdysseyConfig.save();
        }
    }
}
