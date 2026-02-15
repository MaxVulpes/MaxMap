package dev.maxvulpes.maxmap.ui;

import dev.maxvulpes.maxmap.MapCoverageManager;
import dev.maxvulpes.maxmap.MapCoverageState;
import dev.maxvulpes.maxmap.MaxMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CoverageMapRenderer {
    public static final int GRID_MARGIN = 16;
    public static final int GRID_MAX_CELL = 28;
    private static final CachedGridTexture GRID_CACHE = new CachedGridTexture();
    
    // Sist beregnede layout fra render() (brukes til click-hit test for å unngå mismatch)
    // Sist hoverede world grid-celle (settes i render() når tooltip er aktiv)
    private static int LAST_HOVER_WGX = Integer.MIN_VALUE;
    private static int LAST_HOVER_WGZ = Integer.MIN_VALUE;

    public static int[] getLastHoverWorldCell() {
        if (LAST_HOVER_WGX == Integer.MIN_VALUE || LAST_HOVER_WGZ == Integer.MIN_VALUE) return null;
        return new int[]{LAST_HOVER_WGX, LAST_HOVER_WGZ};
    }

    private static void clearLastHoverWorldCell() {
        LAST_HOVER_WGX = Integer.MIN_VALUE;
        LAST_HOVER_WGZ = Integer.MIN_VALUE;
    }
private CoverageMapRenderer() {
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft, Font font, int panelLeft, int panelTop,
                              int panelRight, int panelBottom, int mouseX, int mouseY, boolean drawLegend,
                              boolean enableTooltip) {
                // Bakgrunn:
        // - Config (drawLegend=true): behold full panel-bakgrunn.
        // - Overlay (drawLegend=false): tegn kun bak grid + covered-label (clean/tight).
        if (drawLegend) {
            graphics.fill(panelLeft - 2, panelTop - 2, panelRight + 2, panelBottom + 2, 0xAA000000);
        }


        if (!MapCoverageManager.hasTarget()) {
            graphics.drawCenteredString(font,
                    "Enter coordinates and press Cartograph to see coverage",
                    (panelLeft + panelRight) / 2,
                    (panelTop + panelBottom) / 2,
                    0xFFFFFF);
            return;
        }

        BlockPos start = MapCoverageManager.getTargetStart();
        BlockPos end = MapCoverageManager.getTargetEnd();
        if (start == null || end == null) {
            return;
        }

        MapCoverageManager.CoverageGrid grid = MapCoverageManager.getCoverageGrid(start, end);
        if (grid == null) {
            return;
        }
        int columns = grid.columns();
        int rows = grid.rows();

        int coverageTextHeight = font.lineHeight;
        int legendSpace = drawLegend ? 24 : 0;
        int reservedBottomSpace = coverageTextHeight + 8 + legendSpace;

        int availableWidth = panelRight - panelLeft - GRID_MARGIN;
        int availableHeight = panelBottom - panelTop - GRID_MARGIN - reservedBottomSpace;
        if (availableWidth <= 0 || availableHeight <= 0) {
            return;
        }

        
        double fittedCell = Math.min((double) availableWidth / columns, (double) availableHeight / rows);
        int fitted = (int) Math.floor(Math.min(GRID_MAX_CELL, fittedCell));
        fitted = Math.max(1, fitted);

        // Slider/state: config screen bruker configCellSize, overlay bruker overlayCellSize.
        int desired = drawLegend ? MapCoverageState.configCellSize : MapCoverageState.overlayCellSize;
        desired = Math.max(1, Math.min(GRID_MAX_CELL, desired));

        // Må fortsatt passe i panelet, så clamp til fitted.
        int cellSize = Math.min(desired, fitted);


        int gridWidth = cellSize * columns;
        int gridHeight = cellSize * rows;
        int startX = panelLeft + (availableWidth - gridWidth) / 2;
        int startY = panelTop + (availableHeight - gridHeight) / 2;

        if (!drawLegend) {
            // Overlay: bakgrunn kun rundt grid + covered-label
            int bgLeft = startX - 2;
            int bgTop = startY - 2;

            // textY for overlay er startY + gridHeight + 2 (samme som i label-blokka)
            int labelTop = startY + gridHeight + 2 - 2;
            int labelBottom = startY + gridHeight + 2 + font.lineHeight + 2;

            int bgRight = startX + gridWidth + 2;
            int bgBottom = labelBottom;

            graphics.fill(bgLeft, bgTop, bgRight, bgBottom, 0xAA000000);
        }
clearLastHoverWorldCell();
List<Component> hoveredTooltip = null;
        int mappedCount = grid.mappedCount();
        int totalAreas = grid.totalCount();

        GRID_CACHE.ensureUpToDate(minecraft, grid, cellSize);
        if (GRID_CACHE.hasTexture()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, GRID_CACHE.textureId, startX, startY, 0, 0,
                    GRID_CACHE.width, GRID_CACHE.height, GRID_CACHE.width, GRID_CACHE.height);
        }

        if (enableTooltip
                && mouseX >= startX
                && mouseX < startX + gridWidth
                && mouseY >= startY
                && mouseY < startY + gridHeight) {
            int gx = (mouseX - startX) / cellSize;
            int gz = (mouseY - startY) / cellSize;
            if (gx >= 0 && gx < columns && gz >= 0 && gz < rows) {
                int worldGX = grid.gxMin() + gx;
                int worldGZ = grid.gzMin() + gz;
                                  LAST_HOVER_WGX = worldGX;
                  LAST_HOVER_WGZ = worldGZ;
int xStart = MapCoverageManager.gridIndexToStart(worldGX);
                int zStart = MapCoverageManager.gridIndexToStart(worldGZ);
                boolean mapped = grid.isMappedAt(worldGX, worldGZ);
                Integer mapId = grid.getMapIdAt(worldGX, worldGZ);
                hoveredTooltip = buildCellTooltip(xStart, zStart, mapped, mapId);
            }
        }

        drawPlayerMarker(graphics, minecraft, startX, startY, cellSize, grid.gxMin(), grid.gzMin(), columns, rows);

        double coveragePercent = totalAreas > 0 ? (mappedCount * 100.0) / totalAreas : 0.0;
        String coverageText = String.format("Covered: %d/%d (%.2f%%)", mappedCount, totalAreas, coveragePercent);
        int coverageTextWidth = font.width(coverageText);
        int textX;
        int textY;

        if (drawLegend) {
            textX = panelLeft + 4;
            textY = panelBottom - legendSpace - coverageTextHeight - 4;

            graphics.fill(textX - 2, textY - 2, textX + coverageTextWidth + 2, textY + coverageTextHeight + 2, 0xAA000000);
            graphics.drawString(font, coverageText, textX, textY, 0xFFFFFFFF);
        } else {
            // Overlay: tett label rett under grid (ingen stor bakgrunn som mørkner cellene)
            textX = startX + 2;
            textY = startY + gridHeight + 2;

            // Kun liten, clean bakgrunn bak teksten
            graphics.fill(
                    textX - 2, textY - 2,
                    textX + coverageTextWidth + 2, textY + coverageTextHeight + 2,
                    0xAA000000
            );
            graphics.drawString(font, coverageText, textX, textY, 0xFFFFFFFF);
        }
if (drawLegend) {
            graphics.drawString(font, MapCoverageManager.MAP_TILE_SIZE + "x" + MapCoverageManager.MAP_TILE_SIZE + " block tiles", panelLeft + 4, panelTop + 16, 0xFFFFFF);
            graphics.drawString(font, "Green = mapped", startX, panelBottom - 24, 0x00FF66);
            graphics.drawString(font, "Red = unmapped", startX, panelBottom - 12, 0xFF5555);
        }

        if (enableTooltip && hoveredTooltip != null) {
            graphics.setTooltipForNextFrame(font, hoveredTooltip, Optional.empty(), mouseX, mouseY);
        }
    }

    private static void drawPlayerMarker(GuiGraphics graphics, Minecraft minecraft, int startX, int startY, int cellSize,
                                         int gxMin, int gzMin, int columns, int rows) {
        if (minecraft == null) {
            return;
        }

        Player player = minecraft.player;
        if (player == null) {
            return;
        }

        int playerGX = MapCoverageManager.toGridIndex((int) Math.floor(player.getX()));
        int playerGZ = MapCoverageManager.toGridIndex((int) Math.floor(player.getZ()));
        int relativeGX = playerGX - gxMin;
        int relativeGZ = playerGZ - gzMin;

        if (relativeGX < 0 || relativeGX >= columns || relativeGZ < 0 || relativeGZ >= rows) {
            return;
        }

        int iconSize = Math.max(6, Math.min(cellSize, 16));
        int drawX = startX + relativeGX * cellSize + (cellSize - iconSize) / 2;
        int drawY = startY + relativeGZ * cellSize + (cellSize - iconSize) / 2;

        var poseStack = graphics.pose();
        poseStack.pushMatrix();
        poseStack.translate(drawX, drawY);
        float scale = iconSize / 16.0f;
        poseStack.scale(scale, scale);
        graphics.renderItem(new ItemStack(Items.COMPASS), 0, 0);
        poseStack.popMatrix();
    }

    public static List<Component> buildCellTooltip(int xStart, int zStart, boolean mapped, Integer mapId) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(String.format("X: %d to %d", xStart, xStart + MapCoverageManager.MAP_TILE_SIZE - 1)));
        tooltip.add(Component.literal(String.format("Z: %d to %d", zStart, zStart + MapCoverageManager.MAP_TILE_SIZE - 1)));

        if (mapped) {
            String status = "Mapped";
            if (mapId != null) {
                status += String.format(" (Map ID: %d)", mapId);
            }
            tooltip.add(Component.literal(status));
        } else {
            tooltip.add(Component.literal("Not mapped"));
        }
        return tooltip;
    }

    private static final class CachedGridTexture {
        private static final int COLOR_MAPPED = 0xAA00CC66;
        private static final int COLOR_UNMAPPED = 0xAACC1111;
        private static final int COLOR_OUTLINE = 0x66000000;

        private Identifier textureId;
        private DynamicTexture texture;
        private int width;
        private int height;
        private int cellSize;
        private MapCoverageManager.CoverageGrid grid;

        private boolean needsRebuild(MapCoverageManager.CoverageGrid grid, int cellSize) {
            return this.grid != grid || this.cellSize != cellSize;
        }

        private boolean needsTextureResize(MapCoverageManager.CoverageGrid grid, int cellSize) {
            int nextWidth = grid.columns() * cellSize;
            int nextHeight = grid.rows() * cellSize;
            return nextWidth != width || nextHeight != height;
        }

        private boolean hasTexture() {
            return texture != null && textureId != null && width > 0 && height > 0;
        }

        private void ensureUpToDate(Minecraft minecraft, MapCoverageManager.CoverageGrid grid, int cellSize) {
            if (minecraft == null || grid == null) {
                return;
            }
            boolean rebuild = needsRebuild(grid, cellSize);
            boolean resize = needsTextureResize(grid, cellSize);
            if (!rebuild && !resize) {
                return;
            }

            this.grid = grid;
            this.cellSize = cellSize;
            this.width = grid.columns() * cellSize;
            this.height = grid.rows() * cellSize;

            NativeImage image = new NativeImage(width, height, false);
            fillImage(image, grid, cellSize);

            if (texture == null || resize) {
                if (texture != null) {
                    texture.close();
                }
                texture = new DynamicTexture(() -> "coverage_grid", image);
                textureId = Identifier.fromNamespaceAndPath(MaxMap.MOD_ID, "coverage_grid");
                minecraft.getTextureManager().register(textureId, texture);
            } else {
                texture.setPixels(image);
            }
            texture.upload();
        }

        private void fillImage(NativeImage image, MapCoverageManager.CoverageGrid grid, int cellSize) {
            int mappedColor = toAbgr(COLOR_MAPPED);
            int unmappedColor = toAbgr(COLOR_UNMAPPED);
            int outlineColor = toAbgr(cellSize == 3 ? 0xAA000000 : COLOR_OUTLINE);

            for (int gz = 0; gz < grid.rows(); gz++) {
                for (int gx = 0; gx < grid.columns(); gx++) {
                    boolean mapped = grid.isMappedAt(grid.gxMin() + gx, grid.gzMin() + gz);
                    int fillColor = mapped ? mappedColor : unmappedColor;
                    int pixelStartX = gx * cellSize;
                    int pixelStartY = gz * cellSize;

                    for (int y = 0; y < cellSize; y++) {
                        int pixelY = pixelStartY + y;
                        for (int x = 0; x < cellSize; x++) {
                            int pixelX = pixelStartX + x;
                            boolean isOutline = (cellSize >= 4 || cellSize == 3)
                                    && (x == 0 || y == 0 || x == cellSize - 1 || y == cellSize - 1);
                            int color = isOutline ? outlineColor : fillColor;
                            image.setPixelABGR(pixelX, pixelY, color);
                        }
                    }
                }
            }
        }

        private static int toAbgr(int argb) {
            int a = (argb >> 24) & 0xFF;
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            return (a << 24) | (b << 16) | (g << 8) | r;
        }
    }

    public record HitCell(int gx, int gz, int centerX, int centerZ) {}

    // Used by config screen to find which cell mouse is over
    
    /**
     * Hit-test mot samme grid-layout som render().
     * Returnerer cellen i world-grid coords, eller null hvis musa ikke er over grid.
     */
    /**
     * Hit-test som bruker NØYAKTIG samme layout-matte som render().
     * Returnerer world grid-cell (gx/gz) + senterkoordinater (centerX/centerZ), eller null.
     */
    public static HitCell hitTestCell(Minecraft minecraft, Font font,
                                      int panelLeft, int panelTop,
                                      int panelRight, int panelBottom,
                                      double mouseX, double mouseY,
                                      boolean drawLegend) {

        if (minecraft == null || font == null) return null;
        if (!MapCoverageManager.hasTarget()) return null;

        BlockPos start = MapCoverageManager.getTargetStart();
        BlockPos end = MapCoverageManager.getTargetEnd();
        if (start == null || end == null) return null;

        MapCoverageManager.CoverageGrid grid = MapCoverageManager.getCoverageGrid(start, end);
        if (grid == null) return null;

        int columns = grid.columns();
        int rows = grid.rows();

        int coverageTextHeight = font.lineHeight;
        int legendSpace = drawLegend ? 24 : 0;
        int reservedBottomSpace = coverageTextHeight + 8 + legendSpace;

        int availableWidth = panelRight - panelLeft - GRID_MARGIN;
        int availableHeight = panelBottom - panelTop - GRID_MARGIN - reservedBottomSpace;
        if (availableWidth <= 0 || availableHeight <= 0) return null;

        double fittedCell = Math.min((double) availableWidth / columns, (double) availableHeight / rows);
        int fitted = (int) Math.floor(Math.min(GRID_MAX_CELL, fittedCell));
        fitted = Math.max(1, fitted);

        int desired = drawLegend ? MapCoverageState.configCellSize : MapCoverageState.overlayCellSize;
        desired = Math.max(1, Math.min(GRID_MAX_CELL, desired));

        int cellSize = Math.min(desired, fitted);

        int gridWidth = cellSize * columns;
        int gridHeight = cellSize * rows;

        int startX = panelLeft + (availableWidth - gridWidth) / 2;
        int startY = panelTop + (availableHeight - gridHeight) / 2;

        if (mouseX < startX || mouseX >= startX + gridWidth ||
            mouseY < startY || mouseY >= startY + gridHeight) {
            return null;
        }

        int gx = (int) ((mouseX - startX) / cellSize);
        int gz = (int) ((mouseY - startY) / cellSize);

        if (gx < 0 || gx >= columns || gz < 0 || gz >= rows) return null;

        int worldGX = grid.gxMin() + gx;
        int worldGZ = grid.gzMin() + gz;

        int xStart = MapCoverageManager.gridIndexToStart(worldGX);
        int zStart = MapCoverageManager.gridIndexToStart(worldGZ);
        int centerX = xStart + (MapCoverageManager.MAP_TILE_SIZE / 2);
        int centerZ = zStart + (MapCoverageManager.MAP_TILE_SIZE / 2);

        return new HitCell(worldGX, worldGZ, centerX, centerZ);
    }


    }
