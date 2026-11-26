package com.example;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Pocketsqol {
    public static final String MOD_ID = "pocketsqol";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Pocketsqol() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
