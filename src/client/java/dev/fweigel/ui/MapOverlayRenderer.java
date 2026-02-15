package dev.fweigel.ui;

import dev.fweigel.MapCoverageManager;
import dev.fweigel.AutoMapController;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import dev.fweigel.MapCoverageState;

public final class MapOverlayRenderer {
    private MapOverlayRenderer() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!MapCoverageManager.isOverlayEnabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getWindow() == null) {
            return;
        }

        
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        // Tight + anchored overlay (top-right)
        int outerMargin = 8;
        int desired = Math.max(1, Math.min(CoverageMapRenderer.GRID_MAX_CELL, MapCoverageState.overlayCellSize));

        // CoverageMapRenderer reserverer alltid litt plass nederst til "Covered: ..."
        int reservedBottomSpace = minecraft.font.lineHeight + 8;

        int columns = 0;
        int rows = 0;
        if (MapCoverageManager.hasTarget()) {
            BlockPos start = MapCoverageManager.getTargetStart();
            BlockPos end = MapCoverageManager.getTargetEnd();
            if (start != null && end != null) {
                var grid = MapCoverageManager.getCoverageGrid(start, end);
                if (grid != null) {
                    columns = grid.columns();
                    rows = grid.rows();
                }
            }
        }

        // Hvis vi ikke har target/grid ennå, hold panelet lite (renderer viser teksten selv)
        int wantedWidth;
        int wantedHeight;
        if (columns > 0 && rows > 0) {
            wantedWidth = desired * columns + CoverageMapRenderer.GRID_MARGIN + 8;
            wantedHeight = desired * rows + CoverageMapRenderer.GRID_MARGIN + reservedBottomSpace + 8;
        } else {
            wantedWidth = 240;
            wantedHeight = 90;
        }

        int panelWidth = Math.min(wantedWidth, screenWidth - outerMargin * 2);
        int panelHeight = Math.min(wantedHeight, screenHeight - outerMargin * 2);

        int panelRight = screenWidth - outerMargin;
        int panelLeft = panelRight - panelWidth;
        int panelTop = outerMargin;
        int panelBottom = panelTop + panelHeight;
CoverageMapRenderer.render(graphics, minecraft, minecraft.font, panelLeft, panelTop, panelRight, panelBottom, -1, -1, false, false);

        // --- Auto-map HUD status ---
        if (dev.fweigel.MapCoverageManager.isAutoMapEnabled()) {
            // small indicator near overlay (top-right)
            graphics.drawString(minecraft.font,
                    net.minecraft.network.chat.Component.literal("A"),
                    panelRight - 10,
                    panelTop + 6,
                    0xFF00FF00,
                    false);
        }

        if (dev.fweigel.AutoMapController.getInventoryFullWarnTicks() > 0) {
            String msg = "Auto-map paused: Inventory full";
            int x = (screenWidth - minecraft.font.width(msg)) / 2;
            int y = screenHeight - 60; // above hotbar
            graphics.drawString(minecraft.font,
                    net.minecraft.network.chat.Component.literal(msg),
                    x,
                    y,
                    0xFFFF0000,
                    false);
        }
    }

    // Click handler for overlay: raw mouse pixels -> GUI coords -> hitTest
    public static void handleClick(Minecraft client, double rawMouseX, double rawMouseY, int button) {
        if (client == null || client.getWindow() == null) return;

        int winW = client.getWindow().getScreenWidth();
        int winH = client.getWindow().getScreenHeight();
        int guiW = client.getWindow().getGuiScaledWidth();
        int guiH = client.getWindow().getGuiScaledHeight();

        double mx = rawMouseX * (double) guiW / (double) winW;
        double my = rawMouseY * (double) guiH / (double) winH;

        int panelMargin = CoverageMapRenderer.GRID_MARGIN;
        int panelWidth = Math.min(360, guiW - panelMargin * 2);
        int panelHeight = Math.min(300, guiH - panelMargin * 2);

        int panelRight = guiW - panelMargin;
        int panelLeft = panelRight - panelWidth;
        int panelTop = panelMargin;
        int panelBottom = Math.min(guiH - panelMargin, panelTop + panelHeight);

        var hit = CoverageMapRenderer.hitTestCell(
                client,
                client.font,
                panelLeft, panelTop, panelRight, panelBottom,
                mx, my,
                false
        );
        if (hit == null) return;

        if (button == 0) {
            MapCoverageManager.toggleMapped(hit.gx(), hit.gz());
        } else if (button == 1) {
            MapCoverageManager.toggleForcedMapped(hit.gx(), hit.gz());
        }
    }

}
