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
            if (sender == null || sender.level() == null) return;

            // Optionally validate sender (cooldown, distance, permissions, etc.)

            // Relay to other players (see below)
            relayPingToRelevantPlayers(sender, msg.type, msg.location);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void relayPingToRelevantPlayers(ServerPlayer sender, PingType type, Vec3 location) {
        ServerLevel level = sender.serverLevel();

        // Naive version: send to all players in the same dimension
        for (ServerPlayer target : level.players()) {
            //if (target == sender) continue;

            // Optionally add distance checks, visibility checks, team checks, etc.
            NetworkHandler.CHANNEL.sendTo(
                    new PingBroadcastPacket(type, location, sender.getUUID()),
                    target.connection.connection,
                    NetworkDirection.PLAY_TO_CLIENT);
        }
    }
}
