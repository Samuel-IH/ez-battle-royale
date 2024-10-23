package com.samuelih.ezroyale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.samuelih.ezroyale.config.ConfigCommandMapper;
import com.samuelih.ezroyale.config.DoubleConfigMapper;
import com.samuelih.ezroyale.config.IntegerConfigMapper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

import java.util.function.BiConsumer;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@Mod.EventBusSubscriber(modid = EzRoyale.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.DoubleValue MAX_WORLD_BORDER_SIZE_CONFIG = BUILDER
            .comment("The maximum size of the world border, the size of the border when the game starts.")
            .defineInRange("maxWorldBorderSize", 500.0, 0, 999999);

    public static final ForgeConfigSpec.DoubleValue MIN_WORLD_BORDER_SIZE_CONFIG = BUILDER
            .comment("The minimum size of the world border, the size of the border when the game ends.")
            .defineInRange("minWorldBorderSize", 50.0, 0, 999);

    public static final ForgeConfigSpec.DoubleValue MAX_RAND_DIST_FROM_CENTER_CONFIG = BUILDER
            .comment("The maximum random distance from the center that the shrink target can be offset, as a fraction of the world border size.")
            .defineInRange("maxRandDistFromCenter", 0.25, 0, 1);

    public static final ForgeConfigSpec.IntValue TEAM_RESPAWN_TICKS_CONFIG = BUILDER
            .comment("The time in ticks to wait for respawn. 1 second = 20 ticks.")
            .defineInRange("teamRespawnTicks", 600, 0, 999999);

    public static final ForgeConfigSpec.DoubleValue SHRINK_TIME_CONFIG = BUILDER
            .comment("The time in minutes to shrink from MAX_WORLD_BORDER_SIZE to MIN_WORLD_BORDER_SIZE.")
            .defineInRange("shrinkTime", 10.0, 0, 999999);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static double maxWorldBorderSize;
    public static double minWorldBorderSize;
    public static double maxRandDistFromCenter;
    public static int teamRespawnTicks;
    public static double shrinkTime;

    private static class ConfigEntry {
        public String name;
        public ArgumentType<?> argType;
        public BiConsumer<CommandContext<CommandSourceStack>, String> parser;
    }

    private static ConfigEntry[] configEntries;

    private static ConfigCommandMapper[] configMappers;

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        configMappers = new ConfigCommandMapper[] {
                new DoubleConfigMapper("maxWorldBorderSize", MAX_WORLD_BORDER_SIZE_CONFIG, MAX_WORLD_BORDER_SIZE_CONFIG::set, 0, 999999),
                new DoubleConfigMapper("minWorldBorderSize", MIN_WORLD_BORDER_SIZE_CONFIG, MIN_WORLD_BORDER_SIZE_CONFIG::set, 0, 999),
                new DoubleConfigMapper("maxRandDistFromCenter", MAX_RAND_DIST_FROM_CENTER_CONFIG, MAX_RAND_DIST_FROM_CENTER_CONFIG::set, 0, 1),
                new IntegerConfigMapper("teamRespawnTicks", TEAM_RESPAWN_TICKS_CONFIG, TEAM_RESPAWN_TICKS_CONFIG::set, 0, 999999),
                new DoubleConfigMapper("shrinkTime", SHRINK_TIME_CONFIG, SHRINK_TIME_CONFIG::set, 0, 999999)
        };

        for (ConfigCommandMapper mapper : configMappers) {
            mapper.onConfigChanged = () -> {
                SPEC.save();
                updateValues();
            };
        }

        updateValues();
    }

    private static void updateValues() {
        maxWorldBorderSize = MAX_WORLD_BORDER_SIZE_CONFIG.get();
        minWorldBorderSize = MIN_WORLD_BORDER_SIZE_CONFIG.get();
        maxRandDistFromCenter = MAX_RAND_DIST_FROM_CENTER_CONFIG.get();
        teamRespawnTicks = TEAM_RESPAWN_TICKS_CONFIG.get();
        shrinkTime = SHRINK_TIME_CONFIG.get();
    }

    // Register commands for config
    public static void registerConfigCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (ConfigCommandMapper mapper : configMappers) {
            mapper.registerConfigCommands(dispatcher);
        }
    }
}
