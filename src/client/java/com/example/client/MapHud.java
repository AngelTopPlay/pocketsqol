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
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MapHud {
    public static final String ID = "slot_9_map";
    public static final int WIDTH = 132;
    public static final int HEIGHT = 132;

    private static final Identifier DUNGEON_DIMENSION = Pocketsqol.id("dungeon");
    private static final int MAP_SIZE = 128;
    private static final int MAP_OFFSET = 2;
    private static final MapRenderState MAP_RENDER_STATE = new MapRenderState();

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

    public static boolean isDungeon(ClientLevel level) {
        return level.dimension().identifier().equals(DUNGEON_DIMENSION);
    }

    public static void clamp(HudConfig.Elem elem, int guiWidth, int guiHeight) {
        elem.x = Math.max(0, Math.min(guiWidth - (int) (WIDTH * elem.scale), elem.x));
        elem.y = Math.max(0, Math.min(guiHeight - (int) (HEIGHT * elem.scale), elem.y));
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

    public static void drawPlayerFacesOverlay(GuiGraphicsExtractor graphics, Minecraft mc, ItemStack stack, HudConfig.Elem elem) {MapItemSavedData mapData = mapData(mc, stack);
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
            drawPlayerFace(graphics, player, mapX, mapY, (float) Math.toRadians(player.getYRot()));
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

        int size = 12;
        int x = MAP_OFFSET + (int) Math.round(mapX) - size / 2;
        int y = MAP_OFFSET + (int) Math.round(mapY) - size / 2;
        Identifier skin = player.getSkin().body().texturePath();
        graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xD0000000);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + size / 2.0f, y + size / 2.0f);
        graphics.pose().rotate(rotation);
        graphics.pose().translate(-size / 2.0f, -size / 2.0f);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, 0, 0, 8, 8, size, size, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, 0, 0, 40, 8, size, size, 64, 64);
        graphics.pose().popMatrix();
        graphics.outline(x - 1, y - 1, size + 2, size + 2,
                player == Minecraft.getInstance().player ? 0xFFFFFFFF : 0xFFFFC23D);
    }
}
