package com.samuelih.ezroyale.common;

import com.samuelih.ezroyale.client.PingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class PingBroadcastPacket {
    private final PingType type;
    private final Vec3 location;
    private final UUID sender;

    public PingBroadcastPacket(PingType type, Vec3 location, UUID sender) {
        this.type = type;
        this.location = location;
        this.sender = sender;
    }

    public static void encode(PingBroadcastPacket pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.type);
        buf.writeDouble(pkt.location.x);
        buf.writeDouble(pkt.location.y);
        buf.writeDouble(pkt.location.z);
        buf.writeUUID(pkt.sender);
    }

    public static PingBroadcastPacket decode(FriendlyByteBuf buf) {
        PingType type = buf.readEnum(PingType.class);
        Vec3 loc = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        UUID sender = buf.readUUID();
        return new PingBroadcastPacket(type, loc, sender);
    }

    public static void handle(PingBroadcastPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            PingManager.addPing(pkt.type, pkt.location);
        });
        ctx.get().setPacketHandled(true);
    }
}
