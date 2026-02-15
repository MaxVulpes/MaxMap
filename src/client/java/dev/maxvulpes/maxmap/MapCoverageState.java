package dev.maxvulpes.maxmap;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public record MapCoverageState(PositionData targetStart,
                               PositionData targetEnd,
                               boolean overlayEnabled,
                               Set<Long> mappedAreas,
                               Set<Long> forcedMappedAreas,
                               Map<Long, Integer> mapIds) {
    public static int configCellSize = 5;

    // Cell size used by on-screen overlay (separate from config screen)
    public static int overlayCellSize = 3;


    public MapCoverageState() {
        this(null, null, false, new HashSet<>(), new HashSet<>(), new HashMap<>());
    }

    public MapCoverageState {
        mappedAreas = mappedAreas == null ? new HashSet<>() : new HashSet<>(mappedAreas);
        forcedMappedAreas = forcedMappedAreas == null ? new HashSet<>() : new HashSet<>(forcedMappedAreas);
        mapIds = mapIds == null ? new HashMap<>() : new HashMap<>(mapIds);
    }

    public BlockPos targetStartPos() {
        return targetStart != null ? targetStart.toBlockPos() : null;
    }

    public BlockPos targetEndPos() {
        return targetEnd != null ? targetEnd.toBlockPos() : null;
    }

    public Set<Long> mappedAreasCopy() {
        return new HashSet<>(mappedAreas);
    }

    public Set<Long> forcedMappedAreasCopy() {
        return new HashSet<>(forcedMappedAreas);
    }

    public Map<Long, Integer> mapIdsCopy() {
        return new HashMap<>(mapIds);
    }

    public static MapCoverageState snapshotFrom(BlockPos start, BlockPos end, boolean overlayEnabled,
                                                Set<Long> mappedAreas, Set<Long> forcedMappedAreas,
                                                Map<Long, Integer> mapIds) {
        return new MapCoverageState(PositionData.fromBlockPos(start), PositionData.fromBlockPos(end),
                overlayEnabled, mappedAreas, forcedMappedAreas, mapIds);
    }

    public record PositionData(int x, int z) {
        static PositionData fromBlockPos(BlockPos pos) {
            return pos == null ? null : new PositionData(pos.getX(), pos.getZ());
        }

        BlockPos toBlockPos() {
            return new BlockPos(x, 0, z);
        }
    }
}
