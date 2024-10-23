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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(EzRoyale.MODID)
public class EzRoyale
{
    private static boolean isInSetup = true;// point to shift the storm to

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

    @SuppressWarnings("unused")
    private final TeamGlow teamGlow = new TeamGlow(true);
    private final TeamBuilder teamBuilder = new TeamBuilder();
    private final ShrinkingStorm storm = new ShrinkingStorm();

    private void ResetGame(ServerLevel level) {
        playerData.clear();
        isInSetup = true;

        // reset world border
        if (level != null) {
            storm.reset(level);
        }
    }

    public EzRoyale()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
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

        ResetGame(world);

        isInSetup = false;

        // clear and survival all players
        source.getServer().getPlayerList().getPlayers().forEach(p -> {
            p.setGameMode(GameType.SURVIVAL);
            // clear inventory
            p.getInventory().clearContent();
        });

        atPosition = new Vec3(atPosition.x, 400, atPosition.z);
        storm.start(world, atPosition);

        source.getServer().getPlayerList().getPlayers().forEach(this::launchPlayer);

        world.setWeatherParameters(0, 240000, true, true);

        source.sendSuccess(Component.literal("Rampage started!"), true);
        return Command.SINGLE_SUCCESS;
    }

    private void launchPlayer(ServerPlayer player) {
        var respawnPos = storm.getSpawnCenter(player.getLevel());
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
                    var respawnPos = storm.getSpawnCenter(player.getLevel());
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

        storm.tickStorm(overworld);
    }
}
