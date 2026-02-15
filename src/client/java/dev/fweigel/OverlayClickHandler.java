package dev.fweigel;

import dev.fweigel.ui.MapOverlayRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public final class OverlayClickHandler {
    private static boolean lastLeft = false;
    private static boolean lastRight = false;

    private OverlayClickHandler() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null) return;
            if (!MapCoverageManager.isOverlayEnabled()) {
                lastLeft = false;
                lastRight = false;
                return;
            }

            boolean left = client.mouseHandler.isLeftPressed();
            boolean right = client.mouseHandler.isRightPressed();

            // edge detect: kun når knappen går false->true
            if (left && !lastLeft) {
                MapOverlayRenderer.handleClick(client, client.mouseHandler.xpos(), client.mouseHandler.ypos(), 0);
            }
            if (right && !lastRight) {
                MapOverlayRenderer.handleClick(client, client.mouseHandler.xpos(), client.mouseHandler.ypos(), 1);
            }

            lastLeft = left;
            lastRight = right;
        });
    }
}
