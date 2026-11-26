package com.example.client;

import com.example.Pocketsqol;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class PocketsqolClient implements ClientModInitializer {
    private static final Identifier MAP_HUD_ELEMENT = Pocketsqol.id("slot_9_map");
    private static KeyMapping editKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(Pocketsqol.id("main"));
        editKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.pocketsqol.edit", InputConstants.KEY_U, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (editKey.consumeClick()) {
                mc.setScreenAndShow(new HudEditScreen());
            }
            DecoratedPotWaypoints.tick(mc);
        });

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                MAP_HUD_ELEMENT,
                (graphics, delta) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (!shouldShowMap(mc)) return;

                    HudConfig.Elem elem = mapElem();
                    if (elem.visible) {
                        MapHud.draw(graphics, mc, MapHud.findMapStack(mc), elem, false);
                    }
                });

        HudElementRegistry.attachElementAfter(
                MAP_HUD_ELEMENT,
                Pocketsqol.id("map_player_heads"),
                (graphics, delta) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (!shouldShowMap(mc)) return;

                    HudConfig.Elem elem = mapElem();
                    if (elem.visible) {
                        MapHud.drawPlayerFacesOverlay(graphics, mc, MapHud.findMapStack(mc), elem);
                    }
                });

        LevelRenderEvents.BEFORE_GIZMOS.register(DecoratedPotWaypoints::render);
    }

    private static boolean shouldShowMap(Minecraft mc) {
        return mc.player != null && mc.level != null && !mc.gui.hud.isHidden()
                && MapHud.isDungeon(mc.level);
    }

    private static HudConfig.Elem mapElem() {
        return HudConfig.get().elem(MapHud.ID, 10, 10);
    }
}
