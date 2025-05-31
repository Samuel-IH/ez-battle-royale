package com.samuelih.ezroyale.client;

import com.samuelih.ezroyale.EzRoyale;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Disables default client-side AI for battle-royale skeletons (tagged with BR_debug)
 * to prevent client-side goal selector CME crashes.
 */
@Mod.EventBusSubscriber(modid = EzRoyale.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientEventHandler {
    @SubscribeEvent
    public static void onClientEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ClientLevel)) {
            return;
        }
        if (event.getEntity() instanceof Skeleton skeleton
                && skeleton.getPersistentData().getBoolean("BR_debug")) {
            skeleton.setNoAi(true);
        }
    }
}