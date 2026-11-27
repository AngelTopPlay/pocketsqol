package com.example.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScrapTracker {
    public static final long POPUP_MS = 1600L;
    private static final long FLASH_MS = 700L;

    public record Popup(String name, long shine, long startMs) {}

    private static final Pattern LORE_LINE = Pattern.compile("^\\s*([\\d,]+)\\s+(.+?)\\s*$");

    private static final Map<String, Integer> PRICES = new LinkedHashMap<>();
    private static final Map<String, Integer> COLORS = new HashMap<>();
    private static final Map<String, Integer> sack = new HashMap<>();
    private static final Map<String, Integer> lastTotals = new HashMap<>();
    private static final List<Popup> popups = new ArrayList<>();

    private static boolean sackKnown;
    private static boolean baselineValid;
    private static long lastGainMs;
    private static double displayShine = -1;
    private static long lastFrameNanos;

    static {
        PRICES.put("Void Scrap", 4000);
        PRICES.put("Gilded Scrap", 2000);
        PRICES.put("Sealed Lockbox", 1200);
        PRICES.put("Rune Plate", 640);
        PRICES.put("Iron Scrap", 480);
        PRICES.put("Rusty Scrap", 120);

        COLORS.put("Void Scrap", 0xAA55FF);
        COLORS.put("Gilded Scrap", 0xFFAA00);
        COLORS.put("Sealed Lockbox", 0xDDDDDD);
        COLORS.put("Rune Plate", 0x55AAFF);
        COLORS.put("Iron Scrap", 0xFFFFFF);
        COLORS.put("Rusty Scrap", 0xFF8844);
    }

    private ScrapTracker() {}

    public static boolean isSackKnown() {
        return sackKnown;
    }

    public static int color(String name) {
        return COLORS.getOrDefault(name, 0xFFFFFF);
    }

    public static List<Popup> popups() {
        return popups;
    }

    public static float gainFlash() {
        long age = System.currentTimeMillis() - lastGainMs;
        return age >= FLASH_MS ? 0.0f : 1.0f - age / (float) FLASH_MS;
    }

    public static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        popups.removeIf(popup -> now - popup.startMs() > POPUP_MS);

        if (mc.player == null) {
            sack.clear();
            lastTotals.clear();
            sackKnown = false;
            baselineValid = false;
            displayShine = -1;
            return;
        }

        ItemStack sackStack = findSack(mc);
        if (sackStack == null) {
            sack.clear();
            lastTotals.clear();
            sackKnown = false;
            baselineValid = false;
            return;
        }

        Map<String, Integer> counts = new HashMap<>();
        ItemLore lore = sackStack.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                Matcher matcher = LORE_LINE.matcher(line.getString());
                if (!matcher.matches()) continue;
                String key = priceKey(matcher.group(2));
                if (key == null) continue;
                try {
                    counts.merge(key, Integer.parseInt(matcher.group(1).replace(",", "")), Integer::sum);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        sack.clear();
        sack.putAll(counts);
        sackKnown = true;

        Map<String, Integer> totals = counts(mc);
        if (baselineValid) {
            for (Map.Entry<String, Integer> entry : totals.entrySet()) {
                int delta = entry.getValue() - lastTotals.getOrDefault(entry.getKey(), 0);
                if (delta > 0) {
                    popups.add(new Popup(entry.getKey(), (long) delta * PRICES.get(entry.getKey()), now));
                    lastGainMs = now;
                }
            }
        }
        lastTotals.clear();
        lastTotals.putAll(totals);
        baselineValid = true;
    }

    public static Map<String, Integer> counts(Minecraft mc) {
        Map<String, Integer> merged = new HashMap<>(sack);
        if (mc.player != null) {
            Inventory inventory = mc.player.getInventory();
            for (ItemStack stack : inventory.getNonEquipmentItems()) {
                addScrap(merged, stack);
            }
            addScrap(merged, mc.player.getOffhandItem());
        }

        Map<String, Integer> ordered = new LinkedHashMap<>();
        for (String key : PRICES.keySet()) {
            int count = merged.getOrDefault(key, 0);
            if (count > 0) ordered.put(key, count);
        }
        return ordered;
    }

    public static long shineOf(Map<String, Integer> counts) {
        long total = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            total += (long) entry.getValue() * PRICES.get(entry.getKey());
        }
        return total;
    }

    public static long displayedShine(long target) {
        long now = System.nanoTime();
        if (displayShine < 0) {
            displayShine = target;
            lastFrameNanos = now;
            return target;
        }

        double dt = Math.min(0.25, (now - lastFrameNanos) / 1.0E9);
        lastFrameNanos = now;
        double diff = target - displayShine;
        if (Math.abs(diff) < 1.0) {
            displayShine = target;
        } else {
            displayShine += diff * Math.min(1.0, dt * 6.0);
        }
        return Math.round(displayShine);
    }

    private static ItemStack findSack(Minecraft mc) {
        Inventory inventory = mc.player.getInventory();
        for (ItemStack stack : inventory.getNonEquipmentItems()) {
            if (isSack(stack)) return stack;
        }
        ItemStack offhand = mc.player.getOffhandItem();
        return isSack(offhand) ? offhand : null;
    }

    private static boolean isSack(ItemStack stack) {
        return !stack.isEmpty() && stack.getHoverName().getString().contains("Scrap Sack");
    }

    private static void addScrap(Map<String, Integer> counts, ItemStack stack) {
        if (stack.isEmpty()) return;
        String key = priceKey(stack.getHoverName().getString());
        if (key != null) counts.merge(key, stack.getCount(), Integer::sum);
    }

    private static String priceKey(String name) {
        for (String key : PRICES.keySet()) {
            if (name.contains(key)) return key;
        }
        return null;
    }
}