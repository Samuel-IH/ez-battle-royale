package com.samuelih.ezroyale.common;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class NetworkHandler {
    private static final String PROTOCOL = "1.0";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("ezroyale", "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, PingPacket.class,
                PingPacket::encode,
                PingPacket::decode,
                PingPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(id++, PingBroadcastPacket.class,
                PingBroadcastPacket::encode,
                PingBroadcastPacket::decode,
                PingBroadcastPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                ClipboardPacket.class,
                ClipboardPacket::encode,
                ClipboardPacket::decode,
                ClipboardPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
