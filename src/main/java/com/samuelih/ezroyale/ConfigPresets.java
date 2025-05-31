package com.samuelih.ezroyale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLPaths;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

/**
 * Manages saving and loading battle royale configuration presets.
 */
public class ConfigPresets {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PRESETS_FILE =
            FMLPaths.CONFIGDIR.get().resolve(EzRoyale.MODID).resolve("presets.json");

    private static synchronized JsonObject loadAll() {
        try {
            if (Files.exists(PRESETS_FILE)) {
                String text = Files.readString(PRESETS_FILE);
                JsonElement elem = JsonParser.parseString(text);
                if (elem.isJsonObject()) {
                    return elem.getAsJsonObject();
                }
            }
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("Failed to read presets file, starting fresh", e);
        }
        return new JsonObject();
    }

    private static synchronized void saveAll(JsonObject root) throws IOException {
        Path dir = PRESETS_FILE.getParent();
        if (dir != null && !Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        Files.writeString(PRESETS_FILE, GSON.toJson(root));
    }

    /** Saves the current config and position as a named preset. */
    public static void savePreset(CommandSourceStack source, String name, Vec3 pos) {
        JsonObject root = loadAll();
        JsonObject preset = new JsonObject();
        JsonObject posJson = new JsonObject();
        posJson.addProperty("x", pos.x);
        posJson.addProperty("y", pos.y);
        posJson.addProperty("z", pos.z);
        preset.add("position", posJson);
        JsonObject cfg = new JsonObject();
        cfg.addProperty("maxWorldBorderSize", Config.maxWorldBorderSize);
        cfg.addProperty("minWorldBorderSize", Config.minWorldBorderSize);
        cfg.addProperty("maxRandDistFromCenter", Config.maxRandDistFromCenter);
        cfg.addProperty("teamRespawnTicks", Config.teamRespawnTicks);
        cfg.addProperty("shrinkTime", Config.shrinkTime);
        cfg.addProperty("moneyIntervalSeconds", Config.moneyIntervalSeconds);
        cfg.addProperty("moneyMaxTotal", Config.moneyMaxTotal);
        preset.add("config", cfg);
        root.add(name, preset);
        try {
            saveAll(root);
            source.sendSuccess(() -> Component.literal("Saved preset '" + name + "'."), true);
        } catch (IOException e) {
            LOGGER.error("Failed to save preset file", e);
            source.sendFailure(Component.literal("Failed to save preset: " + e.getMessage()));
        }
    }

    /** Applies and returns the preset for the given name, or null if missing. */
    public static Preset loadPreset(String name) {
        JsonObject root = loadAll();
        if (!root.has(name) || !root.get(name).isJsonObject()) {
            return null;
        }
        JsonObject preset = root.getAsJsonObject(name);
        
        JsonObject posJson = preset.getAsJsonObject("position");
        Vec3 pos = new Vec3(
                posJson.get("x").getAsDouble(),
                posJson.get("y").getAsDouble(),
                posJson.get("z").getAsDouble()
        );
        
        JsonObject cfg = preset.getAsJsonObject("config");
        Preset p = new Preset(name, pos);
        p.maxWorldBorderSize    = cfg.get("maxWorldBorderSize").getAsDouble();
        p.minWorldBorderSize    = cfg.get("minWorldBorderSize").getAsDouble();
        p.maxRandDistFromCenter = cfg.get("maxRandDistFromCenter").getAsDouble();
        p.teamRespawnTicks      = cfg.get("teamRespawnTicks").getAsInt();
        p.shrinkTime            = cfg.get("shrinkTime").getAsDouble();
        p.moneyIntervalSeconds  = cfg.get("moneyIntervalSeconds").getAsInt();
        p.moneyMaxTotal         = cfg.get("moneyMaxTotal").getAsInt();
        return p;
    }

    /** Returns the set of defined preset names. */
    public static Set<String> getPresetNames() {
        return Collections.unmodifiableSet(loadAll().keySet());
    }

    /** Represents a loaded preset, with methods to apply it. */
    public static class Preset {
        public final String name;
        public final Vec3 position;
        private double maxWorldBorderSize;
        private double minWorldBorderSize;
        private double maxRandDistFromCenter;
        private int teamRespawnTicks;
        private double shrinkTime;
        private int moneyIntervalSeconds;
        private int moneyMaxTotal;

        private Preset(String name, Vec3 pos) {
            this.name = name;
            this.position = pos;
        }

        /** Applies this preset to the running config. */
        public void apply() {
            Config.MAX_WORLD_BORDER_SIZE_CONFIG.set(maxWorldBorderSize);
            Config.MIN_WORLD_BORDER_SIZE_CONFIG.set(minWorldBorderSize);
            Config.MAX_RAND_DIST_FROM_CENTER_CONFIG.set(maxRandDistFromCenter);
            Config.TEAM_RESPAWN_TICKS_CONFIG.set(teamRespawnTicks);
            Config.SHRINK_TIME_CONFIG.set(shrinkTime);
            Config.MONEY_INTERVAL_SECONDS_CONFIG.set(moneyIntervalSeconds);
            Config.MONEY_MAX_TOTAL_CONFIG.set(moneyMaxTotal);
            Config.SPEC.save();
            Config.updateValues();
        }
    }
}