package com.samuelih.ezroyale;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CreativeModeTabEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkConstants;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(EzRoyale.MODID)
public class EzRoyale
{
    private static boolean isInSetup = true;
    private static Vec3 respawnPos = new Vec3(0, 500, 0);
    private static Vec3 nextShiftPoint = new Vec3(0, 0, 0);// point to shift the storm to

    // maps
    private static class PlayerData {
        public boolean needsLandingCheck;
        public boolean waitingForRespawn;
        public int waitingForRespawnTicks;
        public boolean isDead;
    }

    private static final HashMap<UUID, PlayerData> playerData = new HashMap<>();

    // Define mod id in a common place for everything to reference
    public static final String MODID = "ezroyale";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "ezroyale" namespace
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "ezroyale" namespace
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    // Creates a new Block with the id "ezroyale:example_block", combining the namespace and path
    public static final RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block", () -> new Block(BlockBehaviour.Properties.of(Material.STONE)));
    // Creates a new BlockItem with the id "ezroyale:example_block", combining the namespace and path
    public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM = ITEMS.register("example_block", () -> new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties()));

    @SuppressWarnings("unused")
    private final TeamGlow teamGlow = new TeamGlow(true);
    private final TeamBuilder teamBuilder = new TeamBuilder();

    private static void ResetGame(ServerLevel level) {
        playerData.clear();
        isInSetup = true;
        respawnPos = new Vec3(0, 500, 0);

        // reset world border
        if (level != null) {
            level.getWorldBorder().setSize(30000000);
        }
    }

    public EzRoyale()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (a, b) -> true));
    }

    private PlayerData getPlayerData(ServerPlayer player) {
        UUID uuid = player.getUUID();
        return playerData.computeIfAbsent(uuid, k -> new PlayerData());
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    private void addCreative(CreativeModeTabEvent.BuildContents event)
    {
        if (event.getTab() == CreativeModeTabs.BUILDING_BLOCKS)
            event.accept(EXAMPLE_BLOCK_ITEM);
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
                                    Component message = Component.literal("Rampage stopped!");
                                    context.getSource().sendSuccess(message, true);
                                    return 1;
                                }))
        );
    }

    // Equip Elytra with Curse of Binding, apply damage resistance, and set NBT flag
    private int startRoyale(CommandSourceStack source, Vec3 atPosition) {
        ServerLevel world = source.getLevel();
        WorldBorder border = world.getWorldBorder();

        ResetGame(world);

        isInSetup = false;

        // clear and survival all players
        source.getServer().getPlayerList().getPlayers().forEach(p -> {
            p.setGameMode(GameType.SURVIVAL);
            // clear inventory
            p.getInventory().clearContent();
        });

        // get random position in circle around caller with radius of MAX_RAND_DIST_FROM_CENTER * MAX_WORLD_BORDER_SIZE\
        var randDist = Math.random() * Config.maxRandDistFromCenter * Config.maxWorldBorderSize;
        var randAngle = Math.random() * 2 * Math.PI;
        var targetPos = new Vec3(atPosition.x + randDist * Math.cos(randAngle), 0, atPosition.z + randDist * Math.sin(randAngle));
        nextShiftPoint = targetPos;

        // set world border center to caller position
        border.setCenter(targetPos.x, targetPos.z);
        border.setSize(Config.maxWorldBorderSize);  // set world border size to 1000 blocks

        // begin shrinkage
        border.lerpSizeBetween(Config.maxWorldBorderSize, Config.minWorldBorderSize, (int)(Config.shrinkTime * 60 * 1000));  // shrink world border to 100 blocks over 100 seconds

        respawnPos = new Vec3(atPosition.x, 400, atPosition.z);
        source.getServer().getPlayerList().getPlayers().forEach(this::launchPlayer);

        world.setWeatherParameters(0, 240000, true, true);

        source.sendSuccess(Component.literal("Rampage started!"), true);
        return Command.SINGLE_SUCCESS;
    }

    private void launchPlayer(ServerPlayer player) {
        player.teleportTo(respawnPos.x, respawnPos.y, respawnPos.z);
        equipElytra(player);
        applyDamageResistance(player);
        setLandingCheck(player);
        player.startFallFlying();

        // set xp to a very large amount (basically limitless enchanting)
        player.setExperienceLevels(9999);

        Component message = Component.literal("The match has started! Fly to a good spot!");
        player.displayClientMessage(message, true);
    }

    // Equip Elytra with Curse of Binding
    private void equipElytra(ServerPlayer player) {
        ItemStack elytra = new ItemStack(Items.ELYTRA);
        elytra.enchant(Enchantments.BINDING_CURSE, 1);  // Add Curse of Binding

        // Equip in chestplate slot
        player.getInventory().armor.set(2, elytra);
    }

    // Apply damage resistance to prevent damage during flight
    private void applyDamageResistance(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255, false, false));
    }

    // Set custom NBT tag to track landing
    private void setLandingCheck(ServerPlayer player) {
        getPlayerData(player).needsLandingCheck = true;
    }

    // Check if the player has landed and remove the Elytra, resistance effect, and NBT tag
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            if (isInSetup) {
                if (!player.hasPermissions(2)) {
                    // tp player to 0,0,0
                    player.teleportTo(0, 500, 0);
                }
                return;
            }

            var data = getPlayerData(player);

            if (data.isDead) {
                // player is dead, force them to spectator
                player.setGameMode(GameType.SPECTATOR);
                return;
            }

            if (data.waitingForRespawn) {
                int ticks = data.waitingForRespawnTicks;
                ticks++;
                data.waitingForRespawnTicks = ticks;

                if (ticks < Config.teamRespawnTicks) {
                    // set to spectator
                    player.setGameMode(GameType.SPECTATOR);
                    player.teleportTo(respawnPos.x, respawnPos.y, respawnPos.z);

                    // show action bar message
                    if (hasLivingTeammates(player)) {
                        var remainingTicks = Config.teamRespawnTicks - ticks;
                        Component message = Component.literal("You have died, but have living teammates. You will respawn in " + remainingTicks + " ticks.");
                        player.displayClientMessage(message, true);
                    } else {
                        Component message = Component.literal("You have died, you will not respawn.");
                        player.displayClientMessage(message, true);
                        data.waitingForRespawn = false;
                        data.isDead = true;
                    }
                } else {
                    // respawn player
                    player.setGameMode(GameType.SURVIVAL);
                    launchPlayer(player);
                    data.waitingForRespawn = false;

                    DamageSource damageSource = player.getLevel().damageSources().outOfWorld();

                    // get all players on the same team, and damage them 10 dmg
                    Scoreboard scoreboard = player.getScoreboard();
                    PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
                    if (team != null) {
                        List<ServerPlayer> players = player.getLevel().players().stream().filter(p -> p != player).toList();
                        for (ServerPlayer p : players) {
                            if (scoreboard.getPlayersTeam(p.getScoreboardName()) == team) {
                                p.hurt(damageSource, 10);
                            }
                        }
                    }
                }

                return;
            }

            // Check if the player needs landing check and has landed on the ground
            if (data.needsLandingCheck && player.isOnGround()) {
                removeElytra(player);
                removeDamageResistance(player);
                data.needsLandingCheck = false;

                Component message = Component.literal("You have landed, good luck out there!");

                // Send the message to the player's action bar (the second argument `true` means it's an action bar message)
                player.displayClientMessage(message, true);

            }
        }
    }

    // Remove the Elytra from the player's chestplate slot
    private void removeElytra(ServerPlayer player) {
        ItemStack chestplate = player.getInventory().armor.get(2);  // Chestplate slot
        if (chestplate.getItem() == Items.ELYTRA) {
            player.getInventory().armor.set(2, ItemStack.EMPTY);  // Remove Elytra
        }
    }

    // Remove the damage resistance effect
    private void removeDamageResistance(ServerPlayer player) {
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);  // Remove damage resistance effect
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 255, false, false));
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (isInSetup) { return; }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        var playerData = getPlayerData(player);
        playerData.waitingForRespawn = true;
        playerData.waitingForRespawnTicks = 0;
    }

    // Get living teammates for a player
    private static boolean hasLivingTeammates(ServerPlayer player) {
        Scoreboard scoreboard = player.getScoreboard();
        PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (team == null) {
            return false;
        }

        // loop through all players ecext the caller, if they are on the same team and alive then return true
        List<ServerPlayer> players = player.getLevel().players().stream().filter(p -> p != player).toList();
        for (ServerPlayer p : players) {
            if (scoreboard.getPlayersTeam(p.getScoreboardName()) == team && !p.isDeadOrDying() && !p.isSpectator()) {
                return true;
            }
        }

        return false;
    }


    private static final double BORDER_MOVEMENT_SPEED = 0.05;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            return;  // Skip the end phase
        }

        teamBuilder.allowSwitching = isInSetup;

        // Access the overworld (or whichever world you want to manipulate)
        ServerLevel overworld = event.getServer().getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) {
            return;  // Skip if the overworld is not loaded
        }

        if (isInSetup) {
            return;  // Skip if the game is in setup
        }

        tryMoveWorldBorderRandomly(overworld);
    }


    private void tryMoveWorldBorderRandomly(ServerLevel overworld) {
        WorldBorder worldBorder = overworld.getWorldBorder();

        // Check if the world border has reached or is near the minimum size
        if (!(worldBorder.getSize() <= Config.minWorldBorderSize + 1)) { return; }

        // Make the border move randomly
        moveWorldBorderRandomly(worldBorder);
    }

    // Move the world border randomly once it reaches the minimum size
    private void moveWorldBorderRandomly(WorldBorder worldBorder) {
        // Random movement logic
        double currentX = worldBorder.getCenterX();
        double currentZ = worldBorder.getCenterZ();

        // get distance from next shift point
        double dist = Math.sqrt(Math.pow(currentX - nextShiftPoint.x, 2) + Math.pow(currentZ - nextShiftPoint.z, 2));

        // if close enough, set new shift point
        if (dist < 1) {
            var randDist = Math.random() * Config.maxRandDistFromCenter * Config.maxWorldBorderSize;
            var randAngle = Math.random() * 2 * Math.PI;
            nextShiftPoint = new Vec3(currentX + randDist * Math.cos(randAngle), 0, currentZ + randDist * Math.sin(randAngle));
        }

        // Adjust to move towards the next shift point, at a maximum speed of BORDER_MOVEMENT_SPEED
        double deltaX = Math.min(BORDER_MOVEMENT_SPEED, nextShiftPoint.x - currentX);
        deltaX = Math.max(-BORDER_MOVEMENT_SPEED, deltaX);
        double deltaZ = Math.min(BORDER_MOVEMENT_SPEED, nextShiftPoint.z - currentZ);
        deltaZ = Math.max(-BORDER_MOVEMENT_SPEED, deltaZ);

        // Set the new world border center
        worldBorder.setCenter(currentX + deltaX, currentZ + deltaZ);
        worldBorder.setSize(Config.minWorldBorderSize);

    }
}
