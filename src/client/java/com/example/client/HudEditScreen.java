package com.example.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class HudEditScreen extends Screen {
    private HudConfig.Elem draggingElem;
    private double dragStartMouseX;
    private double dragStartMouseY;
    private int dragStartX;
    private int dragStartY;

    public HudEditScreen() {
        super(Component.literal("Editar HUD"));
    }

    private HudConfig.Elem mapElem() {
        return HudConfig.get().elem(MapHud.ID, 10, 10);
    }

    private boolean isInside(HudConfig.Elem e, double mouseX, double mouseY) {
        return mouseX >= e.x && mouseX <= e.x + MapHud.WIDTH * e.scale
                && mouseY >= e.y && mouseY <= e.y + MapHud.HEIGHT * e.scale;
    }

    @Override
    protected void init() {
        super.init();
        MapHud.clamp(mapElem(), width, height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        Minecraft mc = Minecraft.getInstance();
        HudConfig.Elem elem = mapElem();

        g.text(mc.font, "Clic y arrastra para mover | Rueda: tamano | Esc: guardar",
                10, 10, 0xFFFFFFFF, true);

        ItemStack stack = MapHud.findMapStack(mc);
        MapHud.draw(g, mc, stack, elem, true);
        MapHud.drawInfo(g, mc, elem);
        MapHud.drawPlayerFacesOverlay(g, mc, stack, elem);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        HudConfig.Elem elem = mapElem();
        if (isInside(elem, event.x(), event.y())) {
            draggingElem = elem;
            dragStartMouseX = event.x();
            dragStartMouseY = event.y();
            dragStartX = elem.x;
            dragStartY = elem.y;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingElem != null) {
            updateDrag(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingElem != null) {
            draggingElem = null;
            HudConfig.get().save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        HudConfig.Elem e = mapElem();
        if (isInside(e, mouseX, mouseY)) {
            e.scale = (float) Math.max(0.5, Math.min(3.0, e.scale + Math.signum(scrollY) * 0.1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void updateDrag(double mouseX, double mouseY) {
        draggingElem.x = Math.max(0, Math.min(width - (int) (MapHud.WIDTH * draggingElem.scale),
                (int) Math.round(dragStartX + mouseX - dragStartMouseX)));
        draggingElem.y = Math.max(0, Math.min(height - (int) (MapHud.TOTAL_HEIGHT * draggingElem.scale),
                (int) Math.round(dragStartY + mouseY - dragStartMouseY)));
    }

    @Override
    public void removed() {
        HudConfig.get().save();
    }
}