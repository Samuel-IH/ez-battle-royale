package com.samuelih.ezroyale;

import com.mojang.logging.LogUtils;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = EzRoyale.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class TeamGlow {
    public boolean enabled;
    public int maxDist = 16 * 10; // 10 chunks

    private static final Logger LOGGER = LogUtils.getLogger();

    public TeamGlow(boolean enabled) {
        this.enabled = enabled;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) { return; }
        if (!enabled) { return; }

        event.getServer().getAllLevels().forEach(this::updateGlowForAllPlayers);
    }

    private void updateGlowForAllPlayers(ServerLevel level) {
        List<ServerPlayer> players = level.players();
        for (ServerPlayer player : players) {
            updateGlowForPlayer(player);
        }
    }

    private void updateGlowForPlayer(ServerPlayer player) {
        Scoreboard scoreboard = player.getScoreboard();
        PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (team == null) {
            return;
        }

        List<ServerPlayer> players = player.getLevel().players().stream().filter(p -> p != player).toList();
        for (ServerPlayer p : players) {
            sendGlowingPacket(player, p, scoreboard.getPlayersTeam(p.getScoreboardName()) == team);
        }
    }

    private void sendGlowingPacket(ServerPlayer toPlayer, ServerPlayer glowingPlayer, boolean glowing) {
        if (!enabled) {
            return;
        }

        if (toPlayer.distanceToSqr(glowingPlayer) > maxDist * maxDist) {
            return;
        }

        var accessor = getAccessor();
        if (accessor == null) {
            LOGGER.error("Failed to get DATA_SHARED_FLAGS_ID field");
            enabled = false;
            return;
        }

        // Retrieve the data watcher (synced entity data) from the glowing player
        SynchedEntityData data = glowingPlayer.getEntityData();

        // Modify the glowing flag (bit 6 is the glowing flag)
        byte oldFlags = data.get(accessor);
        byte newFlags;
        if (glowing) {
            newFlags = (byte)(oldFlags | 1 << 6);
        } else {
            newFlags = (byte)(oldFlags & ~(1 << 6));
        }

        // Update the player's flags
        List<SynchedEntityData.DataValue<?>> list = new ArrayList<>();
        list.add(SynchedEntityData.DataValue.create(accessor, newFlags));

        // Create a packet to update the metadata (which includes the glowing flag)
        ClientboundSetEntityDataPacket packet = new ClientboundSetEntityDataPacket(glowingPlayer.getId(), list);

        // Send the packet to the target player
        toPlayer.connection.send(packet);
    }

    private static EntityDataAccessor<Byte> DATA_SHARED_FLAGS_ID = null;
    private EntityDataAccessor<Byte> getAccessor() {
        if (DATA_SHARED_FLAGS_ID != null) {
            return DATA_SHARED_FLAGS_ID;
        }

        try {
            var field = ObfuscationReflectionHelper.findField(Entity.class, "f_19805_"); // Entity.DATA_SHARED_FLAGS_ID
            field.setAccessible(true);

            var value = field.get(null);
            if (value == null) {
                return null;
            }

            //noinspection unchecked
            DATA_SHARED_FLAGS_ID = (EntityDataAccessor<Byte>) value;
            return DATA_SHARED_FLAGS_ID;

        } catch (Exception e) {
            LOGGER.error("Failed to get DATA_SHARED_FLAGS_ID field", e);
        }

        return null;
    }
}
