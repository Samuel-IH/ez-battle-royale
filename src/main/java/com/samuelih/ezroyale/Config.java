package com.samuelih.ezroyale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        maxWorldBorderSize = MAX_WORLD_BORDER_SIZE_CONFIG.get();
        minWorldBorderSize = MIN_WORLD_BORDER_SIZE_CONFIG.get();
        maxRandDistFromCenter = MAX_RAND_DIST_FROM_CENTER_CONFIG.get();
        teamRespawnTicks = TEAM_RESPAWN_TICKS_CONFIG.get();
        shrinkTime = SHRINK_TIME_CONFIG.get();
    }

    // Register commands for config
    public static void registerConfigCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("ezroyale")
                        .requires(source -> source.hasPermission(2)) // Only allow ops to run this command
                        .then(Commands.literal("config")
                                .then(Commands.literal("maxWorldBorderSize")
                                        .executes(ctx -> {
                                            ctx.getSource().sendSuccess(
                                                    Component.literal("Current maxWorldBorderSize: " + maxWorldBorderSize),
                                                    false
                                            );
                                            return 1;
                                        })))
        );
        dispatcher.register(
                Commands.literal("ezroyale")
                        .requires(source -> source.hasPermission(2)) // Only allow ops to run this command
                        .then(Commands.literal("config")
                                .then(Commands.literal("maxWorldBorderSize")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 999999))
                                        .executes(ctx -> {
                                            maxWorldBorderSize = DoubleArgumentType.getDouble(ctx, "value");
                                            MAX_WORLD_BORDER_SIZE_CONFIG.set(maxWorldBorderSize);
                                            SPEC.save();
                                            ctx.getSource().sendSuccess(
                                                    Component.literal("Set maxWorldBorderSize to " + maxWorldBorderSize),
                                                    false
                                            );
                                            return 1;
                                        }))))
        );

        dispatcher.register(
                Commands.literal("ezroyale")
                        .requires(source -> source.hasPermission(2)) // Only allow ops to run this command
                        .then(Commands.literal("config")
                                .then(Commands.literal("minWorldBorderSize")
                                        .executes(ctx -> {
                                            ctx.getSource().sendSuccess(
                                                    Component.literal("Current minWorldBorderSize: " + minWorldBorderSize),
                                                    false
                                            );
                                            return 1;
                                        })))
        );
        dispatcher.register(
                Commands.literal("ezroyale")
                        .requires(source -> source.hasPermission(2)) // Only allow ops to run this command
                        .then(Commands.literal("config")
                                .then(Commands.literal("minWorldBorderSize")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 999999))
                                        .executes(ctx -> {
                                            minWorldBorderSize = DoubleArgumentType.getDouble(ctx, "value");
                                            MIN_WORLD_BORDER_SIZE_CONFIG.set(minWorldBorderSize);
                                            SPEC.save();
                                            ctx.getSource().sendSuccess(
                                                    Component.literal("Set minWorldBorderSize to " + minWorldBorderSize),
                                                    false
                                            );
                                            return 1;
                                        }))))
        );

        dispatcher.register(
                Commands.literal("ezroyale")
                        .requires(source -> source.hasPermission(2)) // Only allow ops to run this command
                        .then(Commands.literal("config")
                                .then(Commands.literal("maxRandDistFromCenter")
                                        .executes(ctx -> {
                                            ctx.getSource().sendSuccess(
                                                    Component.literal("Current maxRandDistFromCenter: " + maxRandDistFromCenter),
                                                    false
                                            );
                                            return 1;
                                        })))
        );
        dispatcher.register(
                Commands.literal("ezroyale")
                        .requires(source -> source.hasPermission(2)) // Only allow ops to run this command
                        .then(Commands.literal("config")
                                .then(Commands.literal("maxRandDistFromCenter")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 1))
                                        .executes(ctx -> {
                                            maxRandDistFromCenter = DoubleArgumentType.getDouble(ctx, "value");
                                            MAX_RAND_DIST_FROM_CENTER_CONFIG.set(maxRandDistFromCenter);
                                            SPEC.save();
                                            ctx.getSource().sendSuccess(
                                                    Component.literal("Set maxRandDistFromCenter to " + maxRandDistFromCenter),
                                                    false
                                            );
                                            return 1;
                                        }))))
        );

        dispatcher.register(
                Commands.literal("ezroyale")
                        .requires(source -> source.hasPermission(2)) // Only allow ops to run this command
                        .then(Commands.literal("config")
                                .then(Commands.literal("teamRespawnTicks")
                                        .executes(ctx -> {
                                            ctx.getSource().sendSuccess(
                                                    Component.literal("Current teamRespawnTicks: " + teamRespawnTicks),
                                                    false
                                            );
                                            return 1;
                                        })))
        );
        dispatcher.register(
                Commands.literal("ezroyale")
                        .requires(source -> source.hasPermission(2)) // Only allow ops to run this command
                        .then(Commands.literal("config")
                                .then(Commands.literal("teamRespawnTicks")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 999999))
                                        .executes(ctx -> {
                                            teamRespawnTicks = IntegerArgumentType.getInteger(ctx, "value");
                                            TEAM_RESPAWN_TICKS_CONFIG.set(teamRespawnTicks);
                                            SPEC.save();
                                            ctx.getSource().sendSuccess(
                                                    Component.literal("Set teamRespawnTicks to " + teamRespawnTicks),
                                                    false
                                            );
                                            return 1;
                                        }))))
        );

        dispatcher.register(
                Commands.literal("ezroyale")
                        .requires(source -> source.hasPermission(2)) // Only allow ops to run this command
                        .then(Commands.literal("config")
                                .then(Commands.literal("shrinkTime")
                                        .executes(ctx -> {
                                            ctx.getSource().sendSuccess(
                                                    Component.literal("Current shrinkTime: " + shrinkTime),
                                                    false
                                            );
                                            return 1;
                                        })))
        );
        dispatcher.register(
                Commands.literal("ezroyale")
                        .requires(source -> source.hasPermission(2)) // Only allow ops to run this command
                        .then(Commands.literal("config")
                                .then(Commands.literal("shrinkTime")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 999999))
                                        .executes(ctx -> {
                                            shrinkTime = DoubleArgumentType.getDouble(ctx, "value");
                                            SHRINK_TIME_CONFIG.set(shrinkTime);
                                            SPEC.save();
                                            ctx.getSource().sendSuccess(
                                                    Component.literal("Set shrinkTime to " + shrinkTime),
                                                    false
                                            );
                                            return 1;
                                        }))))
        );
    }
}
