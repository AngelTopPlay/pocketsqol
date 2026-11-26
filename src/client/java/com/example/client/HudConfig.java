package com.example.client;

import com.example.Pocketsqol;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class HudConfig {
    public static final class Elem {
        public int x;
        public int y;
        public float scale = 1.0f;
        public boolean visible = true;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static HudConfig instance;

    private Map<String, Elem> elems = new HashMap<>();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("pocketsqol-hud.json");
    }

    public static HudConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static HudConfig load() {
        Path path = path();
        if (!Files.exists(path)) return new HudConfig();

        try {
            HudConfig config = GSON.fromJson(Files.readString(path), HudConfig.class);
            if (config == null) return new HudConfig();
            if (config.elems == null) config.elems = new HashMap<>();
            return config;
        } catch (IOException | JsonParseException e) {
            Pocketsqol.LOGGER.error("Could not read HUD config at {}", path, e);
            return new HudConfig();
        }
    }

    public Elem elem(String id, int defX, int defY) {
        return elems.computeIfAbsent(id, k -> {
            Elem e = new Elem();
            e.x = defX;
            e.y = defY;
            return e;
        });
    }

    public void save() {
        try {
            Files.writeString(path(), GSON.toJson(this));
        } catch (IOException e) {
            Pocketsqol.LOGGER.error("Could not save HUD config at {}", path(), e);
        }
    }
}