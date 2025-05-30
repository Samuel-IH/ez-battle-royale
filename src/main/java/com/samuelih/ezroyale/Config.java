package com.samuelih.ezroyale;

import com.mojang.brigadier.CommandDispatcher;
import com.samuelih.ezroyale.config.ConfigCommandMapper;
import com.samuelih.ezroyale.config.DoubleConfigMapper;
import com.samuelih.ezroyale.config.IntegerConfigMapper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

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

    public static final ForgeConfigSpec.IntValue MONEY_INTERVAL_SECONDS_CONFIG = BUILDER
            .comment("The time in seconds between giving players money while the storm is running.")
            .defineInRange("moneyIntervalSeconds", 60, 1, 999999);

    public static final ForgeConfigSpec.IntValue MONEY_MAX_TOTAL_CONFIG = BUILDER
            .comment("The maximum total amount of money a player can earn from survival payouts over a single storm.")
            .defineInRange("moneyMaxTotal", 200, 0, 999999);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static double maxWorldBorderSize;
    public static double minWorldBorderSize;
    public static double maxRandDistFromCenter;
    public static int teamRespawnTicks;
    public static double shrinkTime;
    public static int moneyIntervalSeconds;
    public static int moneyMaxTotal;

    private static ConfigCommandMapper[] configMappers;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        configMappers = new ConfigCommandMapper[] {
                new DoubleConfigMapper("maxWorldBorderSize", MAX_WORLD_BORDER_SIZE_CONFIG, MAX_WORLD_BORDER_SIZE_CONFIG::set, 0, 999999),
                new DoubleConfigMapper("minWorldBorderSize", MIN_WORLD_BORDER_SIZE_CONFIG, MIN_WORLD_BORDER_SIZE_CONFIG::set, 0, 999),
                new DoubleConfigMapper("maxRandDistFromCenter", MAX_RAND_DIST_FROM_CENTER_CONFIG, MAX_RAND_DIST_FROM_CENTER_CONFIG::set, 0, 1),
                new IntegerConfigMapper("teamRespawnTicks", TEAM_RESPAWN_TICKS_CONFIG, TEAM_RESPAWN_TICKS_CONFIG::set, 0, 999999),
                new DoubleConfigMapper("shrinkTime", SHRINK_TIME_CONFIG, SHRINK_TIME_CONFIG::set, 0, 999999),
                new IntegerConfigMapper("moneyIntervalSeconds", MONEY_INTERVAL_SECONDS_CONFIG, MONEY_INTERVAL_SECONDS_CONFIG::set, 1, 999999),
                new IntegerConfigMapper("moneyMaxTotal", MONEY_MAX_TOTAL_CONFIG, MONEY_MAX_TOTAL_CONFIG::set, 0, 999999)
        };

        for (ConfigCommandMapper mapper : configMappers) {
            mapper.onConfigChanged = () -> {
                SPEC.save();
                updateValues();
            };
        }

        updateValues();
    }

    public static void updateValues() {
        maxWorldBorderSize = MAX_WORLD_BORDER_SIZE_CONFIG.get();
        minWorldBorderSize = MIN_WORLD_BORDER_SIZE_CONFIG.get();
        maxRandDistFromCenter = MAX_RAND_DIST_FROM_CENTER_CONFIG.get();
        teamRespawnTicks = TEAM_RESPAWN_TICKS_CONFIG.get();
        shrinkTime = SHRINK_TIME_CONFIG.get();
        moneyIntervalSeconds = MONEY_INTERVAL_SECONDS_CONFIG.get();
        moneyMaxTotal = MONEY_MAX_TOTAL_CONFIG.get();
    }

    // Register commands for config
    public static void registerConfigCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (ConfigCommandMapper mapper : configMappers) {
            mapper.registerConfigCommands(dispatcher);
        }
    }
}
