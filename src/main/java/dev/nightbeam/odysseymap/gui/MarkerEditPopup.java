package dev.nightbeam.odysseymap.gui;

import dev.nightbeam.odysseymap.marker.Marker;
import dev.nightbeam.odysseymap.marker.MarkerStorage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Lightweight inline editor for waypoint labels (optional extension point).
 */
public class MarkerEditPopup {
    private MarkerEditPopup() {}

    public static void applyLabel(Marker marker, String newLabel) {
        marker.setLabel(newLabel);
        MarkerStorage.save();
    }
}
