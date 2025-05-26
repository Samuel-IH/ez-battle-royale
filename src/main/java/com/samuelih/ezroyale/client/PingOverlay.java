package com.samuelih.ezroyale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "ezroyale", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PingOverlay {
    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Pre event) {
        PoseStack poseStack = event.getGuiGraphics().pose();
        float partialTicks = event.getPartialTick();

        PingManager.renderPings(poseStack, partialTicks);
    }
}
