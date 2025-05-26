package com.samuelih.ezroyale.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "ezroyale", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeybindRegistrar {
    public static final KeyMapping PING_KEY = new KeyMapping(
            "key.ezroyale.ping",                     // Translation key (in en_us.json)
            KeyConflictContext.IN_GAME,              // Only works in-game
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,                         // Default key
            "key.categories.misc"                    // Category shown in Controls screen
    );

    @SubscribeEvent
    public static void registerKeybindings(RegisterKeyMappingsEvent event) {
        event.register(PING_KEY);
    }
}
