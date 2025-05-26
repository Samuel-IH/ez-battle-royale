package com.samuelih.ezroyale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PingManager {
    private static final int PING_DURATION_MS = 5000; // Total lifespan
    private static final int FADE_DURATION_MS = 1000; // Last part fades out

    private static final List<Ping> active = new ArrayList<>();

    public static void addPing(Vec3 location) {
        active.add(new Ping(location, System.currentTimeMillis()));

        // Play ping sound
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.level.playLocalSound(
                    location.x,
                    location.y,
                    location.z,
                    SoundEvents.NOTE_BLOCK_PLING.get(),
                    SoundSource.PLAYERS,
                    1.0f,
                    1.0f,
                    false
            );
        }
    }

    public static void renderPings(PoseStack poseStack, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();
        long now = System.currentTimeMillis();

        Iterator<Ping> it = active.iterator();
        while (it.hasNext()) {
            Ping ping = it.next();
            long age = now - ping.createdAt;
            if (age > PING_DURATION_MS) {
                it.remove();
                continue;
            }

            Vec3 screen = ProjectHelper.worldToScreen(ping.location, partialTicks);
            if (screen != null && screen.z > 0) {
                float alpha = 1.0f;
                if (age > (PING_DURATION_MS - FADE_DURATION_MS)) {
                    float fadeProgress = (float)(age - (PING_DURATION_MS - FADE_DURATION_MS)) / FADE_DURATION_MS;
                    alpha = 1.0f - Mth.clamp(fadeProgress, 0.0f, 1.0f);
                }
                drawMarker(poseStack, screen.x, screen.y, alpha);
            }
        }
    }

    private static void drawMarker(PoseStack poseStack, double x, double y, float alpha) {
        GuiGraphics g = new GuiGraphics(Minecraft.getInstance(), Minecraft.getInstance().renderBuffers().bufferSource());
        int color = (int)(alpha * 255) << 24 | 0xEE2222; // Alpha in high bits
        g.drawString(Minecraft.getInstance().font, "!", (int)x, (int)y + 10, color);
    }

    private static class Ping {
        final Vec3 location;
        final long createdAt;

        Ping(Vec3 location, long createdAt) {
            this.location = location;
            this.createdAt = createdAt;
        }
    }
}
