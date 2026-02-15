package dev.maxvulpes.maxmap;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class AutoMapController {
    private static int inventoryFullWarnTicks = 0;

    public static int getInventoryFullWarnTicks() {
        return inventoryFullWarnTicks;
    }

    // Very fast so you don't skip red cells while moving
    private static final int COOLDOWN_TICKS = 1;

    private static int cooldown = 0;

    private static int lastGX = Integer.MIN_VALUE;
    private static int lastGZ = Integer.MIN_VALUE;

    // Track hotbar filled-map presence before we used the empty map,
    // so we can detect the newly created FILLED_MAP even if it doesn't land
    // in the same slot.
    private static final boolean[] hotbarHadFilledMap = new boolean[9];

    private static boolean pending = false;
    private static int pendingWaitTicks = 0;

    private static int createdFilledHotbarSlot = -1;

    // Hold/inspect briefly so the map starts updating before moving
    private static int inspectTicks = 0;
    private static int inspectPrevSelected = -1;

    private AutoMapController() {}

    public static void tick(Minecraft client) {
        if (inventoryFullWarnTicks > 0) inventoryFullWarnTicks--;

        if (client == null || client.player == null || client.level == null || client.gameMode == null) return;

        if (client.level.dimension() != Level.OVERWORLD) {
            clearPending(client);
            tickCooldown();
            return;
        }

        if (!MapCoverageManager.isAutoMapEnabled()) {
            clearPending(client);
            tickCooldown();
            return;
        }

        if (pending) {
            handlePending(client);
            tickCooldown();
            return;
        }

        BlockPos start = MapCoverageManager.getTargetStart();
        BlockPos end = MapCoverageManager.getTargetEnd();
        if (start == null || end == null) {
            tickCooldown();
            return;
        }

        BlockPos pos = client.player.blockPosition();
        int worldGX = MapCoverageManager.toGridIndex(pos.getX());
        int worldGZ = MapCoverageManager.toGridIndex(pos.getZ());

        // entering a new grid cell => try immediately
        if (worldGX != lastGX || worldGZ != lastGZ) {
            lastGX = worldGX;
            lastGZ = worldGZ;
            cooldown = 0;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        MapCoverageManager.CoverageGrid grid = MapCoverageManager.getCoverageGrid(start, end);
        if (grid == null) return;

        int gxMin = grid.gxMin();
        int gzMin = grid.gzMin();
        int gxMax = gxMin + grid.columns() - 1;
        int gzMax = gzMin + grid.rows() - 1;

        if (worldGX < gxMin || worldGX > gxMax || worldGZ < gzMin || worldGZ > gzMax) return;
        if (grid.isMappedAt(worldGX, worldGZ)) return;

        // Need an empty main-inventory slot (9..35)
        int emptyMain = findEmptyMainInventoryIndex(client);
        if (emptyMain < 0) { warnInventoryFull(); return; }

        // Need empty map in hotbar
        int emptyMapHotbar = findEmptyMapInHotbar(client);
        if (emptyMapHotbar < 0) return;

        // Snapshot hotbar FILLED_MAP presence before using
        for (int i = 0; i < 9; i++) {
            hotbarHadFilledMap[i] = client.player.getInventory().getItem(i).is(Items.FILLED_MAP);
        }

        int prevSelected = client.player.getInventory().getSelectedSlot();
        client.player.getInventory().setSelectedSlot(emptyMapHotbar);

        ItemStack inHand = client.player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!inHand.is(Items.MAP)) {
            client.player.getInventory().setSelectedSlot(prevSelected);
            cooldown = COOLDOWN_TICKS;
            return;
        }

        client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        client.player.getInventory().setSelectedSlot(prevSelected);

        pending = true;
        pendingWaitTicks = 0;
        createdFilledHotbarSlot = -1;
        inspectTicks = 0;
        inspectPrevSelected = -1;

        cooldown = COOLDOWN_TICKS;
    }

    private static void handlePending(Minecraft client) {
        if (client.player == null || client.gameMode == null) {
            clearPending(client);
            return;
        }

        int emptyMain = findEmptyMainInventoryIndex(client);
        if (emptyMain < 0) {
            clearPending(client);
            return;
        }

        // Step 1: detect where the newly created filled map appeared (hotbar)
        if (createdFilledHotbarSlot < 0) {
            createdFilledHotbarSlot = findNewFilledMapInHotbar(client);
            if (createdFilledHotbarSlot < 0) {
                pendingWaitTicks++;
                if (pendingWaitTicks > 40) clearPending(client);
                return;
            }
        }

        // Step 2: inspect/hold it briefly to start updates
        if (inspectTicks == 0) {
            inspectPrevSelected = client.player.getInventory().getSelectedSlot();
            client.player.getInventory().setSelectedSlot(createdFilledHotbarSlot);
            inspectTicks = 15; // short, but enough to kick map updates
            return;
        }

        inspectTicks--;
        if (inspectTicks > 0) return;

        if (inspectPrevSelected >= 0) {
            client.player.getInventory().setSelectedSlot(inspectPrevSelected);
        }

        // Step 3: move deterministically (PICKUP -> PICKUP)
        int containerId = client.player.inventoryMenu.containerId;

        int hotbarSlotId = 36 + createdFilledHotbarSlot; // hotbar: 36..44
        int mainSlotId = emptyMain;                      // main inv: 9..35

        // Pickup from hotbar
        client.gameMode.handleInventoryMouseClick(containerId, hotbarSlotId, 0, ClickType.PICKUP, client.player);
        // Place into main
        client.gameMode.handleInventoryMouseClick(containerId, mainSlotId, 0, ClickType.PICKUP, client.player);

        // Safety: if cursor still holds something, put it back to the original hotbar slot
        if (!client.player.containerMenu.getCarried().isEmpty()) {
            client.gameMode.handleInventoryMouseClick(containerId, hotbarSlotId, 0, ClickType.PICKUP, client.player);
        }

        clearPending(client);
    }

    private static int findNewFilledMapInHotbar(Minecraft client) {
        for (int i = 0; i < 9; i++) {
            boolean now = client.player.getInventory().getItem(i).is(Items.FILLED_MAP);
            if (now && !hotbarHadFilledMap[i]) return i;
        }
        return -1;
    }

    private static int findEmptyMainInventoryIndex(Minecraft client) {
        for (int i = 9; i <= 35; i++) {
            if (client.player.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    private static int findEmptyMapInHotbar(Minecraft client) {
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getItem(i).is(Items.MAP)) return i;
        }
        return -1;
    }

    private static void tickCooldown() {
        if (cooldown > 0) cooldown--;
    }

    private static void clearPending(Minecraft client) {
        pending = false;
        pendingWaitTicks = 0;
        createdFilledHotbarSlot = -1;

        if (client != null && client.player != null && inspectPrevSelected >= 0) {
            client.player.getInventory().setSelectedSlot(inspectPrevSelected);
        }
        inspectTicks = 0;
        inspectPrevSelected = -1;
    }

    private static void warnInventoryFull() {
        // Only start the timer once so the warning can fade out
        if (inventoryFullWarnTicks <= 0) {
            inventoryFullWarnTicks = 80; // ~4 seconds
        }
        // Back off while full so we do not keep re-triggering constantly
        cooldown = Math.max(cooldown, 10);
    }
}
