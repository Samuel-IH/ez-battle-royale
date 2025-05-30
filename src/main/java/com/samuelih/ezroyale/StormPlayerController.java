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
        public int moneyTicksCounter;
        public int intervalsSurvived;
    }

    private final HashMap<UUID, PlayerData> playerData = new HashMap<>();

    private final GameState gameState;
    private final ShrinkingStorm storm;

    public StormPlayerController(ShrinkingStorm storm, GameState gameState) {
        this.storm = storm;
        this.gameState = gameState;

        gameState.addPhaseChangeListener(this::onChangePhase);
    }

    public void onChangePhase(ServerLevel level, GamePhase newState) {
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

        switch (gameState.getPhase()) {
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

            // if not op, clear inventory
            if (!player.hasPermissions(2)) {
                player.getInventory().clearContent();
            }

            // if not op, and more than 10 blocks away from world spawn, tp to world spawn
            var defaultSpawn = player.level().getSharedSpawnPos();
            if (!player.hasPermissions(2) && player.distanceToSqr(defaultSpawn.getX(), defaultSpawn.getY(), defaultSpawn.getZ()) > 10 * 10) {
                player.teleportTo(defaultSpawn.getX(), defaultSpawn.getY(), defaultSpawn.getZ());
            }

            // if not op, force to survival
            if (!player.hasPermissions(2)) {
                player.setGameMode(GameType.SURVIVAL);
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

            // show countdown
            int ticks = gameState.loadTicks;
            int seconds = ticks / 20;
            Component message = Component.literal("Game starting in " + seconds + " seconds.");
            player.displayClientMessage(message, true);
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
                var respawnPos = storm.getSpawnCenter(level);
                player.teleportTo(respawnPos.x, respawnPos.y, respawnPos.z);

                // show action bar message
                if (hasLivingTeammates(player)) {
                    var remainingTicks = Config.teamRespawnTicks - ticks;
                    var seconds = remainingTicks / 20;
                    Component message = Component.literal("You have died, but have living teammates. You will respawn in " + seconds + " seconds.");
                    player.displayClientMessage(message, true);
                } else {
                    Component message = Component.literal("You have died, you will not respawn.");
                    player.displayClientMessage(message, true);
                    data.waitingForRespawn = false;
                    data.isDead = true;
                }
            } else {
                var teammates = getLivingTeammates(player);
                var damageToShare = 10.0f;
                var damagePerTeammate = damageToShare / teammates.size();

                // can only respawn if all living teammates have 2 + damagePerTeammate health
                boolean canRespawn = teammates.stream().allMatch(p -> p.getHealth() >= 2 + damagePerTeammate);
                if (!canRespawn) {
                    // show action bar message
                    Component message = Component.literal("You have died, but your teammates are too weak to respawn you.");
                    player.displayClientMessage(message, true);
                } else {
                    // respawn player
                    player.setGameMode(GameType.SURVIVAL);
                    launchPlayer(player);
                    data.waitingForRespawn = false;

                    DamageSource damageSource = level.damageSources().fellOutOfWorld();

                    // apply damage to teammates
                    for (ServerPlayer p : teammates) {
                        p.hurt(damageSource, damagePerTeammate);
                    }
                }
            }

            return;
        }

        // Check if the player needs landing check and has landed on the ground
        if (data.needsLandingCheck && player.onGround()) {
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

        // survival-based money payout
        data.moneyTicksCounter++;
        if (data.moneyTicksCounter >= Config.moneyIntervalSeconds * 20) {
            data.moneyTicksCounter = 0;
            data.intervalsSurvived++;
            int k = data.intervalsSurvived;
            int maxIntervals = Math.max(1, (int)(Config.shrinkTime * 60 / Config.moneyIntervalSeconds));
            // clamp
            if (k > maxIntervals) {
                k = maxIntervals;
            }

            double sumSquares = maxIntervals * (maxIntervals + 1) * (2L * maxIntervals + 1) / 6.0;
            double ratio = Config.moneyMaxTotal / sumSquares;
            int amount = (int)Math.floor(ratio * k * k);
            if (amount > 0) {
                player.getInventory().add(new ItemStack(EzRoyale.MONEY.get(), amount));
                Component message = Component.literal("You survived " + k + " rounds so you get " + amount + " cash!");
                player.displayClientMessage(message, true);
            }
        }
    }

    private void launchPlayer(ServerPlayer player) {
        var respawnPos = storm.getSpawnCenter(player.serverLevel());
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
        if (gameState.getPhase() != GamePhase.RUNNING) { return; }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        var data = getPlayerData(player);
        data.waitingForRespawn = true;
        data.waitingForRespawnTicks = 0;
        // reset survival payout counters on death
        data.moneyTicksCounter = 0;
        data.intervalsSurvived = 0;
    }

    private static boolean hasLivingTeammates(ServerPlayer player) {
        Scoreboard scoreboard = player.getScoreboard();
        PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (team == null) {
            return false;
        }

        // loop through all players ecext the caller, if they are on the same team and alive then return true
        List<ServerPlayer> players = player.serverLevel().players().stream().filter(p -> p != player).toList();
        for (ServerPlayer p : players) {
            if (scoreboard.getPlayersTeam(p.getScoreboardName()) == team && !p.isDeadOrDying() && !p.isSpectator()) {
                return true;
            }
        }

        return false;
    }

    private static List<ServerPlayer> getLivingTeammates(ServerPlayer player) {
        Scoreboard scoreboard = player.getScoreboard();
        PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (team == null) {
            return List.of();
        }

        // loop through all players ecext the caller, if they are on the same team and alive then return true
        List<ServerPlayer> players = player.serverLevel().players().stream().filter(p -> p != player).toList();
        return players.stream().filter(p -> scoreboard.getPlayersTeam(p.getScoreboardName()) == team && !p.isDeadOrDying() && !p.isSpectator()).toList();
    }
}
