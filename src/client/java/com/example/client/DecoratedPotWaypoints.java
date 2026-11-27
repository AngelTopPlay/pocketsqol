package com.example.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DecoratedPotWaypoints {
    private static final boolean ENABLED = true;
    private static final int SCAN_INTERVAL_TICKS = 20;

    private static final int COLOR_PRIMARY = 0x3DFFB0;
    private static final int COLOR_SECONDARY = 0xFFC23D;
    private static final int COLOR_OUTLINE = 0xFFFFFF;
    private static final int COLOR_TARGETED = 0xFFFFFF;

    private static final int SECONDARY_COUNT = 3;
    private static final double MAX_RANGE = 64.0;
    private static final double SECONDARY_RANGE = 32.0;
    private static final double OUTLINE_RANGE = 14.0;
    private static final double BEAM_MIN_DISTANCE = 6.0;
    private static final double TRACER_MIN_DISTANCE = 4.0;
    private static final double BEAM_HEIGHT = 24.0;
    private static final double FALLBACK_RADIUS = 64.0;

    private static final Set<BlockPos> positions = new HashSet<>();
    private static volatile List<BlockPos> renderPositions = List.of();
    private static volatile double[] currentBounds;
    private static String debugSource = "-";
    private static String debugPlayer = "-";

    private static ClientLevel currentLevel;
    private static int ticksUntilScan;

    private record Marker(BlockPos pos, double distanceSquared) {}

    private DecoratedPotWaypoints() {}

    public static String debugInfo() {
        return "src=" + debugSource + " p=" + debugPlayer;
    }

    private static double[] resolveBounds(Minecraft mc) {
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        debugPlayer = (int) px + "," + (int) pz;

        double[] mapBounds = MapHud.dungeonBounds(mc);
        if (mapBounds != null && px >= mapBounds[0] - 6 && px < mapBounds[1] + 6
                && pz >= mapBounds[2] - 6 && pz < mapBounds[3] + 6) {
            debugSource = "calibrated";
            return mapBounds;
        }

        debugSource = mapBounds == null ? "radius(no lock)" : "radius(outside)";
        return new double[]{px - FALLBACK_RADIUS, px + FALLBACK_RADIUS, pz - FALLBACK_RADIUS, pz + FALLBACK_RADIUS};
    }

    public static int potCount() {
        return renderPositions.size();
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (!ENABLED || level == null || mc.player == null) {
            clear();
            return;
        }

        if (currentLevel != level) {
            positions.clear();
            currentLevel = level;
            ticksUntilScan = 0;
        }

        double[] bounds = resolveBounds(mc);
        currentBounds = bounds;

        boolean changed = positions.removeIf(pos -> !inBounds(pos, bounds) || !isPotAt(level, pos));
        if (--ticksUntilScan <= 0) {
            changed |= scanLoadedChunks(mc, level, bounds);
            ticksUntilScan = SCAN_INTERVAL_TICKS;
        }
        if (changed) publishPositions();
    }

    public static void render(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (!ENABLED || mc.level == null || mc.player == null) return;

        double[] bounds = currentBounds;
        if (bounds == null) return;

        BlockPos targeted = null;
        List<BlockPos> candidates = renderPositions;
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && inBounds(hit.getBlockPos(), bounds) && isPotAt(mc.level, hit.getBlockPos())) {
            targeted = hit.getBlockPos().immutable();
            if (!candidates.contains(targeted)) {
                candidates = new ArrayList<>(candidates);
                candidates.add(targeted);
            }
        }
        if (candidates.isEmpty()) return;

        Vec3 camera = context.levelState().cameraRenderState.pos;
        double maxRangeSquared = MAX_RANGE * MAX_RANGE;

        List<Marker> sorted = new ArrayList<>();
        for (BlockPos pos : candidates) {
            double distanceSquared = pos.distToCenterSqr(camera);
            if (distanceSquared <= maxRangeSquared || pos.equals(targeted)) {
                sorted.add(new Marker(pos, distanceSquared));
            }
        }
        if (sorted.isEmpty()) return;
        sorted.sort(Comparator.comparingDouble(Marker::distanceSquared));

        double time = (System.currentTimeMillis() % 2000L) / 2000.0 * Math.PI * 2.0;
        double pulse = 0.5 + 0.5 * Math.sin(time);
        int others = sorted.size() - 1;

        try (Gizmos.TemporaryCollection ignored =
                     context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
            for (int i = 0; i < sorted.size(); i++) {
                Marker marker = sorted.get(i);
                BlockPos pos = marker.pos();
                double distance = Math.sqrt(marker.distanceSquared());

                if (i == 0) {
                    drawPrimary(pos, distance, pulse, others);
                    if (distance > TRACER_MIN_DISTANCE) drawTracer(mc, camera, pos);
                } else if (i <= SECONDARY_COUNT && distance <= SECONDARY_RANGE) {
                    drawSecondary(pos, distance);
                } else if (distance <= OUTLINE_RANGE) {
                    drawOutline(pos);
                }

                if (pos.equals(targeted)) {
                    drawBox(pos, COLOR_TARGETED, 255, 40, 3.0f, 0.04);
                }
            }
        }
    }

    private static void drawPrimary(BlockPos pos, double distance, double pulse, int others) {
        drawBox(pos, COLOR_PRIMARY, 255, (int) (45 + 50 * pulse), 3.0f, 0.02);

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;

        double beamFade = clamp((distance - BEAM_MIN_DISTANCE) / 10.0, 0.0, 1.0);
        if (beamFade > 0.0) {
            int fill = argb((int) ((30 + 25 * pulse) * beamFade), COLOR_PRIMARY);
            Gizmos.cuboid(
                    new AABB(x - 0.1, y, z - 0.1, x + 0.1, y + BEAM_HEIGHT, z + 0.1),
                    GizmoStyle.strokeAndFill(0x00000000, 1.0f, fill))
                    .setAlwaysOnTop();
            Gizmos.line(new Vec3(x, y, z), new Vec3(x, y + BEAM_HEIGHT, z),
                    argb((int) (200 * beamFade), COLOR_PRIMARY), 2.0f)
                    .setAlwaysOnTop();
        }

        float titleScale = (float) clamp(distance * 0.045, 0.5, 1.6);
        float subScale = titleScale * 0.8f;
        double labelY = y + 0.9 + titleScale * 0.35;

        Gizmos.billboardText("Decorated Pot", new Vec3(x, labelY + titleScale * 0.35, z),
                        TextGizmo.Style.forColorAndCentered(argb(255, COLOR_PRIMARY)).withScale(titleScale))
                .setAlwaysOnTop();
        Gizmos.billboardText(String.format("%.0fm", distance), new Vec3(x, labelY, z),
                        TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale(subScale))
                .setAlwaysOnTop();
        if (others > 0) {
            Gizmos.billboardText("+" + others + " more", new Vec3(x, labelY - subScale * 0.35, z),
                            TextGizmo.Style.forColorAndCentered(0xAAAAAAAA).withScale(subScale * 0.8f))
                    .setAlwaysOnTop();
        }
    }

    private static void drawSecondary(BlockPos pos, double distance) {
        double fade = clamp(1.0 - distance / SECONDARY_RANGE, 0.35, 1.0);
        drawBox(pos, COLOR_SECONDARY, (int) (230 * fade), (int) (35 * fade), 2.0f, 0.02);

        float scale = (float) clamp(distance * 0.03, 0.4, 1.0);
        Gizmos.billboardText(String.format("%.0fm", distance),
                        new Vec3(pos.getX() + 0.5, pos.getY() + 1.5 + scale * 0.2, pos.getZ() + 0.5),
                        TextGizmo.Style.forColorAndCentered(argb((int) (255 * fade), COLOR_SECONDARY))
                                .withScale(scale))
                .setAlwaysOnTop();
    }

    private static void drawOutline(BlockPos pos) {
        drawBox(pos, COLOR_OUTLINE, 110, 0, 1.2f, 0.01);
    }

    private static void drawBox(BlockPos pos, int rgb, int strokeAlpha, int fillAlpha, float width, double expand) {
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        Gizmos.cuboid(
                new AABB(x - expand, y - expand, z - expand, x + 1 + expand, y + 1 + expand, z + 1 + expand),
                GizmoStyle.strokeAndFill(argb(strokeAlpha, rgb), width, argb(fillAlpha, rgb)))
                .setAlwaysOnTop();
    }

    private static void drawTracer(Minecraft mc, Vec3 camera, BlockPos target) {
        Vec3 look = mc.player.getViewVector(1.0f);
        Vec3 start = camera.add(look.scale(2.0)).add(0.0, -0.4, 0.0);
        Vec3 end = new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        Gizmos.line(start, end, argb(170, COLOR_PRIMARY), 1.8f).setAlwaysOnTop();
    }

    private static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean inBounds(BlockPos pos, double[] bounds) {
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        return x >= bounds[0] && x < bounds[1] && z >= bounds[2] && z < bounds[3];
    }

    private static boolean scanLoadedChunks(Minecraft mc, ClientLevel level, double[] bounds) {
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
                    if (!blockEntity.isRemoved() && inBounds(pos, bounds) && isPotBlockEntity(blockEntity)) {
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
        currentBounds = null;
        currentLevel = null;
        ticksUntilScan = 0;
    }
}