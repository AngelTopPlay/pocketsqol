package com.example.client;

import com.example.Pocketsqol;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MapHud {
    public static final String ID = "slot_9_map";
    public static final int WIDTH = 132;
    public static final int HEIGHT = 132;
    public static final int INFO_HEIGHT = 36;
    public static final int TOTAL_HEIGHT = HEIGHT + 2 + INFO_HEIGHT;

    private static final boolean DEBUG = true;
    private static final Identifier DUNGEON_DIMENSION = Pocketsqol.id("dungeon");
    private static final int MAP_SIZE = 128;
    private static final int MAP_OFFSET = 2;
    private static final int FACE_SIZE = 12;
    private static final double RELOCK_DISTANCE = 24.0;
    private static final MapRenderState MAP_RENDER_STATE = new MapRenderState();

    private static boolean originLocked;
    private static double originX;
    private static double originZ;
    private static double lastBpp = 1.0;

    private static ItemStack potIcon;
    private static ItemStack goldIcon;

    private MapHud() {}

    public static MapId mapId(ItemStack stack) {
        return stack.get(DataComponents.MAP_ID);
    }

    public static ItemStack findMapStack(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;

        Inventory inventory = mc.player.getInventory();
        ItemStack selected = inventory.getSelectedItem();
        if (mapId(selected) != null) return selected;

        for (ItemStack stack : inventory.getNonEquipmentItems()) {
            if (mapId(stack) != null) return stack;
        }

        ItemStack offhand = mc.player.getOffhandItem();
        return mapId(offhand) == null ? ItemStack.EMPTY : offhand;
    }

    public static MapItemSavedData mapData(Minecraft mc, ItemStack stack) {
        if (mc.level == null) return null;
        MapId mapId = mapId(stack);
        return mapId == null ? null : mc.level.getMapData(mapId);
    }

    public static double[] dungeonBounds(Minecraft mc) {
        if (mc.player == null) return null;
        MapItemSavedData data = mapData(mc, findMapStack(mc));
        if (data == null) return null;

        double bpp = 1 << data.scale;
        lastBpp = bpp;
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        boolean stationary = mc.player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4;
        int expectedRot = Math.round((mc.player.getYRot() + 180.0f) / 22.5f) & 15;

        int count = 0;
        double[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (MapDecoration decoration : data.getDecorations()) {
            if (!decoration.getSpriteLocation().getPath().contains("player")) continue;
            count++;

            double ox = px - (64.0 + decoration.x() / 2.0) * bpp;
            double oz = pz - (64.0 + decoration.y() / 2.0) * bpp;
            int rot = Byte.toUnsignedInt(decoration.rot()) & 15;
            int diff = Math.abs(rot - expectedRot);
            diff = Math.min(diff, 16 - diff);

            double score = originLocked ? Math.hypot(ox - originX, oz - originZ) : diff;
            if (score < bestScore) {
                bestScore = score;
                best = new double[]{ox, oz, diff};
            }
        }

        if (best != null && (count == 1 || best[2] <= 2)) {
            if (!originLocked) {
                originLocked = true;
                originX = best[0];
                originZ = best[1];
            } else {
                double distance = Math.hypot(best[0] - originX, best[1] - originZ);
                if (distance > RELOCK_DISTANCE || (stationary && distance > 0.01)) {
                    originX = best[0];
                    originZ = best[1];
                }
            }
        }

        if (!originLocked) return null;
        double size = MAP_SIZE * bpp;
        return new double[]{originX - 1, originX + size + 1, originZ - 1, originZ + size + 1};
    }

    public static boolean isDungeon(ClientLevel level) {
        return level.dimension().identifier().equals(DUNGEON_DIMENSION);
    }

    public static void clamp(HudConfig.Elem elem, int guiWidth, int guiHeight) {
        elem.x = Math.max(0, Math.min(guiWidth - (int) (WIDTH * elem.scale), elem.x));
        elem.y = Math.max(0, Math.min(guiHeight - (int) (TOTAL_HEIGHT * elem.scale), elem.y));
    }

    public static void draw(GuiGraphicsExtractor graphics, Minecraft mc, ItemStack stack, HudConfig.Elem elem,
                            boolean preview) {
        MapItemSavedData mapData = mapData(mc, stack);
        if (mapData == null && !preview) return;

        clamp(elem, graphics.guiWidth(), graphics.guiHeight());
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) elem.x, (float) elem.y);
        graphics.pose().scale(elem.scale, elem.scale);
        graphics.fill(0, 0, WIDTH, HEIGHT, 0xD0000000);

        if (mapData != null) {
            MapId mapId = mapId(stack);
            mc.getMapRenderer().extractRenderState(mapId, mapData, MAP_RENDER_STATE);
            graphics.pose().pushMatrix();
            graphics.pose().translate(MAP_OFFSET, MAP_OFFSET);
            graphics.map(MAP_RENDER_STATE);
            graphics.pose().popMatrix();
        } else {
            for (int x = 0; x < MAP_SIZE; x += 16) {
                for (int y = 0; y < MAP_SIZE; y += 16) {
                    int color = ((x + y) / 16) % 2 == 0 ? 0xFF353535 : 0xFF454545;
                    graphics.fill(MAP_OFFSET + x, MAP_OFFSET + y, MAP_OFFSET + x + 16, MAP_OFFSET + y + 16, color);
                }
            }
        }

        graphics.outline(1, 1, MAP_SIZE + 2, MAP_SIZE + 2, 0xFFFFC23D);
        graphics.pose().popMatrix();
    }

    public static void drawInfo(GuiGraphicsExtractor graphics, Minecraft mc, HudConfig.Elem elem) {
        clamp(elem, graphics.guiWidth(), graphics.guiHeight());
        if (potIcon == null) {
            potIcon = new ItemStack(Items.DECORATED_POT);
            goldIcon = new ItemStack(Items.GOLD_INGOT);
        }

        int pots = DecoratedPotWaypoints.potCount();
        Map<String, Integer> counts = ScrapTracker.counts(mc);
        long shown = ScrapTracker.displayedShine(ScrapTracker.shineOf(counts));
        long now = System.currentTimeMillis();
        int top = HEIGHT + 2;

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) elem.x, (float) elem.y);
        graphics.pose().scale(elem.scale, elem.scale);

        graphics.item(potIcon, 0, top);
        graphics.text(mc.font, String.valueOf(pots), 19, top + 4, 0xFFFFFFFF, true);

        int shineColor = lerpColor(0xFFC23D, 0xFFFFFF, ScrapTracker.gainFlash());
        String shineText = String.format("%,d", shown);
        graphics.item(goldIcon, 0, top + 17);
        graphics.text(mc.font, shineText, 19, top + 21, 0xFF000000 | shineColor, true);

        int popupX = 19 + mc.font.width(shineText) + 8;
        for (ScrapTracker.Popup popup : ScrapTracker.popups()) {
            float age = (now - popup.startMs()) / (float) ScrapTracker.POPUP_MS;
            if (age < 0.0f || age >= 1.0f) continue;
            int alpha = (int) (255 * (1.0f - age * age));
            if (alpha < 12) continue;
            int rise = (int) (age * 16);
            String text = "+" + String.format("%,d", popup.shine()) + " " + popup.name();
            graphics.text(mc.font, text, popupX, top + 21 - rise,
                    (alpha << 24) | ScrapTracker.color(popup.name()), true);
        }

        int y = top + 38;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            graphics.text(mc.font, entry.getValue() + " " + entry.getKey(), 2, y,
                    0xFF000000 | ScrapTracker.color(entry.getKey()), true);
            y += 10;
        }
        if (!ScrapTracker.isSackKnown()) {
            graphics.text(mc.font, "No Scrap Sack found", 2, y, 0xFF999999, true);
            y += 10;
        }

        if (DEBUG) {
            graphics.text(mc.font, DecoratedPotWaypoints.debugInfo(), 2, y + 4, 0xFF55FF55, true);
            graphics.text(mc.font, debugCalibration(), 2, y + 14, 0xFF55FF55, true);
        }
        graphics.pose().popMatrix();
    }

    private static int lerpColor(int from, int to, float t) {
        int r = (int) (((from >> 16) & 255) * (1.0f - t) + ((to >> 16) & 255) * t);
        int g = (int) (((from >> 8) & 255) * (1.0f - t) + ((to >> 8) & 255) * t);
        int b = (int) ((from & 255) * (1.0f - t) + (to & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static String debugCalibration() {
        if (!originLocked) return "origin: not locked";
        return "origin=" + (int) originX + "," + (int) originZ + " bpp=" + lastBpp;
    }

    public static void drawPlayerFacesOverlay(GuiGraphicsExtractor graphics, Minecraft mc, ItemStack stack,
                                              HudConfig.Elem elem) {
        MapItemSavedData mapData = mapData(mc, stack);
        if (mapData == null || mc.level == null) return;

        clamp(elem, graphics.guiWidth(), graphics.guiHeight());
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) elem.x, (float) elem.y);
        graphics.pose().scale(elem.scale, elem.scale);
        drawPlayerFaces(graphics, mc, mapData);
        graphics.pose().popMatrix();
    }

    private static void drawPlayerFaces(GuiGraphicsExtractor graphics, Minecraft mc, MapItemSavedData mapData) {
        ClientLevel level = mc.level;
        if (level == null) return;

        double blocksPerPixel = 1 << mapData.scale;
        List<AbstractClientPlayer> players = new ArrayList<>();
        if (mc.player != null) {
            players.add(mc.player);
        }
        for (AbstractClientPlayer player : level.players()) {
            if (player != mc.player) players.add(player);
        }

        Set<AbstractClientPlayer> drawnPlayers = new HashSet<>();
        for (MapDecoration decoration : mapData.getDecorations()) {
            if (!decoration.getSpriteLocation().getPath().contains("player")) continue;

            double mapX = 64.0 + decoration.x() / 2.0;
            double mapY = 64.0 + decoration.y() / 2.0;
            AbstractClientPlayer nearest = nearestPlayer(players, mapData, blocksPerPixel, mapX, mapY, drawnPlayers);
            if (nearest != null) {
                float rotation = (Byte.toUnsignedInt(decoration.rot()) & 15) * (float) (Math.PI / 8.0);
                drawPlayerFace(graphics, nearest, mapX, mapY, rotation);
                drawnPlayers.add(nearest);
            }
        }

        for (AbstractClientPlayer player : players) {
            if (drawnPlayers.contains(player)) continue;
            double mapX = 64.0 + (player.getX() - mapData.centerX) / blocksPerPixel;
            double mapY = 64.0 + (player.getZ() - mapData.centerZ) / blocksPerPixel;
            drawPlayerFace(graphics, player, mapX, mapY, (float) Math.toRadians(player.getYRot() + 180.0f));
        }
    }

    private static AbstractClientPlayer nearestPlayer(List<AbstractClientPlayer> players, MapItemSavedData mapData,
                                                       double blocksPerPixel, double mapX, double mapY,
                                                       Set<AbstractClientPlayer> alreadyDrawn) {
        AbstractClientPlayer nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (AbstractClientPlayer player : players) {
            if (alreadyDrawn.contains(player)) continue;
            double playerMapX = 64.0 + (player.getX() - mapData.centerX) / blocksPerPixel;
            double playerMapY = 64.0 + (player.getZ() - mapData.centerZ) / blocksPerPixel;
            double dx = playerMapX - mapX;
            double dy = playerMapY - mapY;
            double distance = dx * dx + dy * dy;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private static void drawPlayerFace(GuiGraphicsExtractor graphics, AbstractClientPlayer player,
                                       double mapX, double mapY, float rotation) {
        if (mapX < 6 || mapX > MAP_SIZE - 6 || mapY < 6 || mapY > MAP_SIZE - 6) return;

        float centerX = MAP_OFFSET + (float) mapX;
        float centerY = MAP_OFFSET + (float) mapY;
        Identifier skin = player.getSkin().body().texturePath();
        int outlineColor = player == Minecraft.getInstance().player ? 0xFFFFFFFF : 0xFFFFC23D;

        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().rotate(rotation);
        graphics.pose().translate(-FACE_SIZE / 2.0f, -FACE_SIZE / 2.0f);

        graphics.fill(-1, -1, FACE_SIZE + 1, FACE_SIZE + 1, 0xD0000000);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, 0, 0, 8, 8, FACE_SIZE, FACE_SIZE, 8, 8, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, 0, 0, 40, 8, FACE_SIZE, FACE_SIZE, 8, 8, 64, 64);
        graphics.outline(-1, -1, FACE_SIZE + 2, FACE_SIZE + 2, outlineColor);

        graphics.pose().popMatrix();
    }
}