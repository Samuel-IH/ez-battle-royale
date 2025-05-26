package com.samuelih.ezroyale.common;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PingPacket {
    public final PingType type;
    public final Vec3 location;

    public PingPacket(PingType type, Vec3 location) {
        this.type = type;
        this.location = location;
    }

    public static void encode(PingPacket pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.type);
        buf.writeDouble(pkt.location.x);
        buf.writeDouble(pkt.location.y);
        buf.writeDouble(pkt.location.z);
    }

    public static PingPacket decode(FriendlyByteBuf buf) {
        PingType type = buf.readEnum(PingType.class);
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        return new PingPacket(type, new Vec3(x, y, z));
    }

    public static void handle(PingPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            // Relay to other players (see below)
            relayPingToRelevantPlayers(sender, msg.type, msg.location);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void relayPingToRelevantPlayers(ServerPlayer sender, PingType type, Vec3 location) {
        ServerLevel level = sender.serverLevel();

        // First and foremost, always relay to the player who sent the ping. They need to see their own pings.
        NetworkHandler.CHANNEL.sendTo(
                new PingBroadcastPacket(type, location, sender.getUUID()),
                sender.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT);

        // No team == no broadcasting
        if (sender.getTeam() == null) return;

        // Now, send to other players that are on the same team, and in the same dimension
        level.players().stream()
                .filter(player -> player != sender && player.getTeam() == sender.getTeam() && player.level() == level)
                .forEach(player -> {
                    NetworkHandler.CHANNEL.sendTo(
                            new PingBroadcastPacket(type, location, sender.getUUID()),
                            player.connection.connection,
                            NetworkDirection.PLAY_TO_CLIENT);
                });
    }
}
