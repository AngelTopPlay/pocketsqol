package com.example.client;

import com.example.Pocketsqol;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DecoratedPotWaypoints {
    private static final Identifier DUNGEON_DIMENSION = Pocketsqol.id("dungeon");
    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final int WAYPOINT_COLOR = 0xFFFFC23D;
    private static final int WAYPOINT_FILL = 0x33FFC23D;
    private static final float WAYPOINT_LINE_WIDTH = 2.5f;
    private static final double MAX_WAYPOINT_DISTANCE_SQUARED = 20 * 20;

    private static final Set<BlockPos> positions = new HashSet<>();
    private static volatile List<BlockPos> renderPositions = List.of();

    private static ClientLevel currentLevel;
    private static int ticksUntilScan;

    private DecoratedPotWaypoints() {}

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !isDungeon(level)) {
            clear();
            return;
        }

        if (currentLevel != level) {
            positions.clear();
            currentLevel = level;
            ticksUntilScan = 0;
        }

        boolean changed = positions.removeIf(pos -> !isPotAt(level, pos));
        if (--ticksUntilScan <= 0) {
            changed |= scanLoadedChunks(mc, level);
            ticksUntilScan = SCAN_INTERVAL_TICKS;
        }
        if (changed) publishPositions();
    }

    public static void render(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !isDungeon(mc.level)) return;

        List<BlockPos> markers = renderPositions;
        BlockPos targetedPot = null;
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && isPotAt(mc.level, hit.getBlockPos())) {
            targetedPot = hit.getBlockPos().immutable();
            if (!markers.contains(targetedPot)) {
                markers = new ArrayList<>(markers);
                markers.add(targetedPot);
            }
        }
        if (markers.isEmpty()) return;

        try (Gizmos.TemporaryCollection ignored =
                     context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
            Vec3 camera = context.levelState().cameraRenderState.pos;
            for (BlockPos pos : markers) {
                if (pos.distToCenterSqr(camera) > MAX_WAYPOINT_DISTANCE_SQUARED) continue;

                double x = pos.getX();
                double y = pos.getY();
                double z = pos.getZ();
                if (!pos.equals(targetedPot)) {
                    Vec3 target = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                    var hit = mc.level.clip(new ClipContext(
                            camera, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
                    if (hit.getType() != HitResult.Type.MISS
                            && !hit.getBlockPos().equals(pos)) {
                        continue;
                    }
                }

                Gizmos.cuboid(
                        new AABB(x + 0.25, y + 1.04, z + 0.25, x + 0.75, y + 1.16, z + 0.75),
                        GizmoStyle.strokeAndFill(WAYPOINT_COLOR, WAYPOINT_LINE_WIDTH, WAYPOINT_FILL))
                        .setAlwaysOnTop();
            }
        }
    }

    private static boolean isDungeon(ClientLevel level) {
        return level.dimension().identifier().equals(DUNGEON_DIMENSION);
    }

    private static boolean scanLoadedChunks(Minecraft mc, ClientLevel level) {
        int centerX = mc.player.blockPosition().getX() >> 4;
        int centerZ = mc.player.blockPosition().getZ() >> 4;
        int radius = mc.options.renderDistance().get() + 2;
        boolean foundNewPot = false;

        for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
            for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    BlockPos pos = blockEntity.getBlockPos();
                    if (!blockEntity.isRemoved() && isPotBlockEntity(blockEntity)) {
                        foundNewPot |= positions.add(pos.immutable());
                    }
                }
            }
        }
        return foundNewPot;
    }

    private static boolean isPotAt(ClientLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        if (isDecoratedPot(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()))) return true;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity != null && !blockEntity.isRemoved() && isPotBlockEntity(blockEntity);
    }

    private static boolean isPotBlockEntity(BlockEntity blockEntity) {
        return blockEntity instanceof DecoratedPotBlockEntity
                || isDecoratedPot(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()))
                || isDecoratedPot(BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()));
    }

    private static boolean isDecoratedPot(Identifier id) {
        return id != null && id.getPath().contains("decorated_pot");
    }

    private static void publishPositions() {
        renderPositions = List.copyOf(positions);
    }

    private static void clear() {
        positions.clear();
        renderPositions = List.of();
        currentLevel = null;
        ticksUntilScan = 0;
    }
}
