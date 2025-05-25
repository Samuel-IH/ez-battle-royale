package com.samuelih.ezroyale;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.Random;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(EzRoyale.MODID)
public class EzRoyale
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "ezroyale";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    private final GameState gameState = new GameState();

    private final TeamBuilder teamBuilder = new TeamBuilder();
    private final ShrinkingStorm storm = new ShrinkingStorm();
    private final StormPlayerController playerController = new StormPlayerController(storm, gameState);

    private void ResetGame(ServerLevel level) {
        gameState.setPhase(level, GamePhase.SETUP);

        // reset world border
        if (level != null) {
            storm.reset(level);
        }
    }

    private void onChangePhase(ServerLevel level, GamePhase newPhase) {
        teamBuilder.allowSwitching = newPhase == GamePhase.SETUP;

        if (newPhase == GamePhase.RUNNING) {
            storm.start(level);
        }
    }

    public EzRoyale()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        TeamGlow teamGlow = new TeamGlow(true);
        MinecraftForge.EVENT_BUS.register(teamGlow);
        MinecraftForge.EVENT_BUS.register(teamBuilder);
        MinecraftForge.EVENT_BUS.register(playerController);
        MinecraftForge.EVENT_BUS.register(new TeamWeapons());
        MinecraftForge.EVENT_BUS.register(new ChestLootHandler());

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        gameState.addPhaseChangeListener(this::onChangePhase);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");

    }

    // Subscribe to the command registration event
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        registerCommands(event.getDispatcher());
        Config.registerConfigCommands(event.getDispatcher());
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("ezroyale")
                        .requires(source -> source.hasPermission(2)) // Only allow ops to run this command
                        .then(Commands.literal("start")
                                .then(Commands.literal("here")
                                        .executes(context -> {
                                            var caller = context.getSource().getPlayerOrException();
                                            return startRoyale(context.getSource(), caller.position());
                                        }))
                                .then(Commands.literal("random")
                                        .executes(context -> {
                                            var rand = new Random();
                                            var radius = 99999;
                                            var x = rand.nextInt(radius * 2) - radius;
                                            var z = rand.nextInt(radius * 2) - radius;
                                            return startRoyale(context.getSource(), new Vec3(x, 0, z));
                                        }))
                        )
                        .requires(source -> source.hasPermission(2)) // Only allow ops to run this command
                        .then(Commands.literal("stop")
                                .executes(context -> {
                                    ResetGame(context.getSource().getLevel());
                                    Component message = Component.literal("Battle stopped!");
                                    context.getSource().sendSuccess(message, true);
                                    return 1;
                                }))
        );
    }

    private int startRoyale(CommandSourceStack source, Vec3 atPosition) {
        ChestLootHandler.startNewLootRound();
        ServerLevel world = source.getLevel();

        ResetGame(world);
        gameState.setPhase(world, GamePhase.LOADING);

        atPosition = new Vec3(atPosition.x, 400, atPosition.z);
        storm.prepare(world, atPosition);

        gameState.loadTicks = 20 * 10;

        source.sendSuccess(Component.literal("Battle started!"), true);
        return Command.SINGLE_SUCCESS;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) { return; }

        teamBuilder.allowSwitching = gameState.getPhase() == GamePhase.SETUP;

        var level = event.getServer().getLevel(ServerLevel.OVERWORLD);
        if (level == null) { return; }

        if (gameState.getPhase() == GamePhase.LOADING) {
            gameState.loadTicks--;
            if (gameState.loadTicks <= 0) {
                gameState.setPhase(level, GamePhase.RUNNING);
            }
        }

        playerController.tick(level);

        if (gameState.getPhase() == GamePhase.RUNNING) {
            storm.tickStorm(level);
        }
    }
}
