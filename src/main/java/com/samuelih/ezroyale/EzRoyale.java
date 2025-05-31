package com.samuelih.ezroyale;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import com.samuelih.ezroyale.common.ClipboardPacket;
import com.samuelih.ezroyale.common.NetworkHandler;
import com.samuelih.ezroyale.common.BattleRoyaleAI;
import com.samuelih.ezroyale.ConfigPresets;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
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

    public static final DeferredRegister<Item> MONEY_ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, "money");

    public static final RegistryObject<Item> MONEY =
            MONEY_ITEMS.register("money", () -> new Item(new Item.Properties()));

    public static void register(IEventBus bus) {
        MONEY_ITEMS.register(bus);
    }

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

    public EzRoyale(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        TeamGlow teamGlow = new TeamGlow(true);
        MinecraftForge.EVENT_BUS.register(teamGlow);
        MinecraftForge.EVENT_BUS.register(teamBuilder);
        MinecraftForge.EVENT_BUS.register(playerController);
        MinecraftForge.EVENT_BUS.register(new ChestLootHandler());

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Register our mod's item(s)
        register(modEventBus);

        gameState.addPhaseChangeListener(this::onChangePhase);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        NetworkHandler.register();
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
                        .requires(source -> source.hasPermission(2))
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
                                .then(Commands.argument("preset", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                ConfigPresets.getPresetNames(), builder))
                                        .executes(context -> startRoyalePreset(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "preset")))
                                )
                        )
                        .then(Commands.literal("save")
                                .then(Commands.argument("preset", StringArgumentType.word())
                                        .executes(context -> {
                                            ConfigPresets.savePreset(
                                                    context.getSource(),
                                                    StringArgumentType.getString(context, "preset"),
                                                    context.getSource().getPosition());
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("stop")
                                .executes(context -> {
                                    ResetGame(context.getSource().getLevel());
                                    Component message = Component.literal("Battle stopped!");
                                    context.getSource().sendSuccess(() -> message, true);
                                    return 1;
                                }))
                        .then(Commands.literal("summon_ai")
                                .executes(context -> summonAI(context.getSource())))
        );

        dispatcher.register(
                Commands.literal("dump_loot")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ServerLevel level = player.serverLevel();
                            BlockPos blockBelow = player.blockPosition();
                            BlockEntity blockEntity = level.getBlockEntity(blockBelow);

                            if (!(blockEntity instanceof Container container)) {
                                context.getSource().sendFailure(Component.literal("Block is not a container"));
                                return 0;
                            }

                            JsonArray entries = new JsonArray();

                            for (int i = 0; i < container.getContainerSize(); i++) {
                                ItemStack stack = container.getItem(i);
                                if (stack.isEmpty()) continue;

                                JsonObject entry = new JsonObject();
                                entry.addProperty("type", "minecraft:item");
                                entry.addProperty("name", ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());

                                if (stack.hasTag()) {
                                    CompoundTag tag = stack.getTag();
                                    String snbt = tag.toString(); // already SNBT
                                    JsonObject func = new JsonObject();
                                    func.addProperty("function", "set_nbt");
                                    func.addProperty("tag", snbt);

                                    JsonArray functions = new JsonArray();
                                    functions.add(func);

                                    entry.add("functions", functions);
                                }

                                entries.add(entry);
                            }

                            String result = new GsonBuilder().setPrettyPrinting().create().toJson(entries);

                            // Send to clipboard (client-side only — do this via a packet)
                            NetworkHandler.CHANNEL.sendTo(
                                    new ClipboardPacket(result),
                                    player.connection.connection,
                                    NetworkDirection.PLAY_TO_CLIENT);

                            context.getSource().sendSuccess(() -> Component.literal("Copied " + entries.size() + " entries to clipboard."), false);
                            return 1;
                        })
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

        source.sendSuccess(() -> Component.literal("Battle started!"), true);
        return Command.SINGLE_SUCCESS;
    }

    private int startRoyalePreset(CommandSourceStack source, String presetName) {
        ConfigPresets.Preset preset = ConfigPresets.loadPreset(presetName);
        if (preset == null) {
            source.sendFailure(Component.literal("Preset '" + presetName + "' not found"));
            return 0;
        }
        preset.apply();
        return startRoyale(source, preset.position);
    }
    
    private int summonAI(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            source.sendFailure(Component.literal("Failed to create AI zombie"));
            return 0;
        }
        zombie.setCustomName(Component.literal("AI"));
        zombie.setCustomNameVisible(true);
        BattleRoyaleAI.applyAI(zombie, gameState, storm);
        zombie.moveTo(player.getX(), player.getY(), player.getZ(), 0F, 0F);
        level.addFreshEntity(zombie);
        source.sendSuccess(() -> Component.literal("Summoned AI zombie"), true);
        return 1;
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
