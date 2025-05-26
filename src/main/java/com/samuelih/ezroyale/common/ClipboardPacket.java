package com.samuelih.ezroyale.common;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClipboardPacket {
    private final String content;

    public ClipboardPacket(String content) {
        this.content = content;
    }

    public static void encode(ClipboardPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.content, Short.MAX_VALUE);
    }

    public static ClipboardPacket decode(FriendlyByteBuf buf) {
        return new ClipboardPacket(buf.readUtf(Short.MAX_VALUE));
    }

    public static void handle(ClipboardPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(pkt.content);
        });
        ctx.get().setPacketHandled(true);
    }
}
