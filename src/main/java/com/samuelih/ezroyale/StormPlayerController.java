package com.samuelih.ezroyale;

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
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = EzRoyale.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class StormPlayerController {
    private static class PlayerData {
        public boolean needsLandingCheck;
        public boolean waitingForRespawn;
        public int waitingForRespawnTicks;
        public boolean isDead;
    }

    private final HashMap<UUID, PlayerData> playerData = new HashMap<>();

    private BrGameState state = BrGameState.SETUP;
    private final ShrinkingStorm storm;

    public StormPlayerController(ShrinkingStorm storm) {
        this.storm = storm;
    }

    public void changeState(ServerLevel level, BrGameState newState) {
        state = newState;

        switch (newState) {
            case SETUP:
                playerData.clear();
                break;
            case LOADING:
                // beginLoading(level);
                break;
            case RUNNING:
                level.players().forEach(this::launchPlayer);
                break;
            case ENDING:
                // beginEnding();
                break;
        }
    }

    public void tick(ServerLevel level) {
        var players = level.players();

        switch (state) {
            case SETUP:
                tickSetup(players);
                break;
            case LOADING:
                tickLoading(level, players);
                break;
            case RUNNING:
                tickRunning(level, players);
                break;
            case ENDING:
                // tickEnding(level, players);
                break;
        }
    }

    private void tickSetup(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            // update health to max
            player.setHealth(player.getMaxHealth());

            // update hunger to max
            player.getFoodData().setFoodLevel(20);

            // clear inventory
            player.getInventory().clearContent();

            // if not op, and more than 10 blocks away from world spawn, tp to world spawn
            var defaultSpawn = player.getLevel().getSharedSpawnPos();
            if (!player.hasPermissions(2) && player.distanceToSqr(defaultSpawn.getX(), defaultSpawn.getY(), defaultSpawn.getZ()) > 10 * 10) {
                player.teleportTo(defaultSpawn.getX(), defaultSpawn.getY(), defaultSpawn.getZ());
            }
        }
    }

    private void tickLoading(ServerLevel level, List<ServerPlayer> players) {
        var spawnCenter = storm.getSpawnCenter(level);
        for (ServerPlayer player : players) {
            // tp to spawn
            player.teleportTo(spawnCenter.x, spawnCenter.y, spawnCenter.z);

            // make survival
            player.setGameMode(GameType.SURVIVAL);

            // update health to max
            player.setHealth(player.getMaxHealth());

            // update hunger to max
            player.getFoodData().setFoodLevel(20);

            // clear inventory
            player.getInventory().clearContent();
        }
    }

    private void tickRunning(ServerLevel level, List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            tickPlayer(level, player);
        }
    }

    @SuppressWarnings("unused")
    private void tickPlayer(ServerLevel level, ServerPlayer player) {
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
            // remove elytra
            ItemStack chestplate = player.getInventory().armor.get(2);
            if (chestplate.getItem() == Items.ELYTRA) {
                player.getInventory().armor.set(2, ItemStack.EMPTY);
            }

            // remove damage resistance and give temporary damage resistance
            player.removeEffect(MobEffects.DAMAGE_RESISTANCE);  // Remove damage resistance effect
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, 255, false, false));

            // action bar
            Component message = Component.literal("You have landed, good luck out there!");
            player.displayClientMessage(message, true);

            data.needsLandingCheck = false;
        }
    }

    private void launchPlayer(ServerPlayer player) {
        var respawnPos = storm.getSpawnCenter(player.getLevel());
        player.teleportTo(respawnPos.x, respawnPos.y, respawnPos.z);

        // equip Elytra
        ItemStack elytra = new ItemStack(Items.ELYTRA);
        elytra.enchant(Enchantments.BINDING_CURSE, 1);  // Add Curse of Binding
        player.getInventory().armor.set(2, elytra);
        player.startFallFlying();

        // apply damage resistance
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255, false, false));

        // start watching for landing
        getPlayerData(player).needsLandingCheck = true;

        // set xp to a very large amount (basically limitless enchanting)
        player.setExperienceLevels(9999);

        Component message = Component.literal("Fly to a good spot!");
        player.displayClientMessage(message, true);
    }

    private PlayerData getPlayerData(ServerPlayer player) {
        UUID uuid = player.getUUID();
        return playerData.computeIfAbsent(uuid, k -> new PlayerData());
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (state != BrGameState.RUNNING) { return; }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        var playerData = getPlayerData(player);
        playerData.waitingForRespawn = true;
        playerData.waitingForRespawnTicks = 0;
    }

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
}
