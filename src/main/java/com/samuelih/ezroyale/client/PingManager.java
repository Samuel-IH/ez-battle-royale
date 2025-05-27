package com.samuelih.ezroyale.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.samuelih.ezroyale.common.PingType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Mod.EventBusSubscriber(modid = "ezroyale", value = Dist.CLIENT)
public class PingManager {
    public static final ResourceLocation PING_LINE_TEX = ResourceLocation.fromNamespaceAndPath("money", "textures/item/ping_line.png");

    public static final int PING_DURATION_MS = 5000;
    public static final int FADE_DURATION_MS = 1000;

    private static final List<Ping> active = new ArrayList<>();

    public static void addPing(PingType type, Vec3 location) {
        var now = System.currentTimeMillis();

        var playSound = true;

        // collapse multiple pings
        for (Ping ping : active) {
            if (ping.location.distanceToSqr(location) < 2.25) {

                if (!(
                        ping.type == type ||
                        (type == PingType.GENERIC && ping.type == PingType.WARNING) // generic pings can collapse into warnings
                )) {
                    continue;
                }

                // Collapse the old ping
                active.remove(ping);
                playSound = false; // don't play sound for collapsed pings

                // if the new ping is generic, we collapse them into warnings (panic mode)
                if (type == PingType.GENERIC) {
                    type = PingType.WARNING;

                    if (ping.type == PingType.GENERIC) {
                        // If the old ping was generic, we've collapsed two generic pings into one warning
                        // so we need to play the warning sound
                        playSound = true;
                    }
                }
                break;
            }
        }

        active.add(new Ping(type, location, System.currentTimeMillis()));

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && playSound) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(type.getSound(), 1.0f));
        }
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();

        Iterator<Ping> it = active.iterator();
        while (it.hasNext()) {
            Ping ping = it.next();
            long age = System.currentTimeMillis() - ping.createdAt;
            if (age > PING_DURATION_MS) {
                it.remove();
                continue;
            }

            renderPing(ping, poseStack);
        }
    }

    private static void renderPing(Ping ping, PoseStack poseStack)
    {
        var location = ping.location;

        var camRot = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        Vec3 lookVec = camPos.subtract(location);
        float yaw = (float) Math.atan2(lookVec.x, lookVec.z);

        poseStack.pushPose();
        poseStack.translate(location.x - camPos.x, location.y - camPos.y, location.z - camPos.z);

        float distance = (float) camPos.distanceTo(location);
        float fov = Minecraft.getInstance().options.fov().get();
        float baseScale = 0.25f;
        float fovRadians = (float) Math.toRadians(fov);
        float perspectiveFactor = (float) Math.tan(fovRadians / 2.0);
        float scale = baseScale * distance * perspectiveFactor;

        float r = ping.type.getR();
        float g = ping.type.getG();
        float b = ping.type.getB();

        poseStack.pushPose();
        renderTorus(PING_LINE_TEX, poseStack, 0.005f * scale, 0.5f, 32, 32, 1f, r, g, b);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(new Quaternionf().rotationY(yaw + (float)Math.PI));
        var lineThickness = 0.01f;
        renderIcon(PING_LINE_TEX, poseStack, lineThickness / -2f, 0.25f, lineThickness, 0.5f, 0, 1, 1, 0, 1, r, g, b);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(camRot);
        renderIcon(ping.type.getTexture(), poseStack, -0.25f, 1f, 0.5f, 0.5f, 0, 1, 1, 0, 1, r, g, b);
        poseStack.popPose();

        poseStack.popPose();
    }

    private static void renderIcon(ResourceLocation icon, PoseStack poseStack, float x, float y, float w, float h, float u0, float u1, float v0, float v1, float alpha, float r, float g, float b)
    {
        Matrix4f matrix = poseStack.last().pose();

        Minecraft.getInstance().getTextureManager().getTexture(icon).setFilter(false, false);
        RenderSystem.setShaderTexture(0, icon);

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bufferbuilder.vertex(matrix, x,         (y + h),    0).uv(u0, v1).color(r, g, b, alpha).endVertex();
        bufferbuilder.vertex(matrix, (x + w),   (y + h),    0).uv(u1, v1).color(r, g, b, alpha).endVertex();
        bufferbuilder.vertex(matrix, (x + w),   y,          0).uv(u1, v0).color(r, g, b, alpha).endVertex();
        bufferbuilder.vertex(matrix, x,         y,          0).uv(u0, v0).color(r, g, b, alpha).endVertex();
        BufferUploader.drawWithShader(bufferbuilder.end());
    }

    private static void renderTorus(ResourceLocation texture, PoseStack poseStack, float minorRadius, float majorRadius, int rings, int loops, float alpha, float r, float g, float b)
    {
        Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().getTexture(texture).setFilter(false, false);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int i = 0; i < rings; i++) {
            float theta1 = (float) (2 * Math.PI * i / rings);
            float theta2 = (float) (2 * Math.PI * (i + 1) / rings);

            float cos1 = (float) Math.cos(theta1);
            float sin1 = (float) Math.sin(theta1);
            float cos2 = (float) Math.cos(theta2);
            float sin2 = (float) Math.sin(theta2);

            for (int j = 0; j < loops; j++) {
                float phi1 = (float) (2 * Math.PI * j / loops);
                float phi2 = (float) (2 * Math.PI * (j + 1) / loops);

                float cosPhi1 = (float) Math.cos(phi1);
                float sinPhi1 = (float) Math.sin(phi1);
                float cosPhi2 = (float) Math.cos(phi2);
                float sinPhi2 = (float) Math.sin(phi2);

                float x1 = (majorRadius + minorRadius * cosPhi1) * cos1;
                float y1 = minorRadius * sinPhi1;
                float z1 = (majorRadius + minorRadius * cosPhi1) * sin1;

                float x2 = (majorRadius + minorRadius * cosPhi1) * cos2;
                float y2 = minorRadius * sinPhi1;
                float z2 = (majorRadius + minorRadius * cosPhi1) * sin2;

                float x3 = (majorRadius + minorRadius * cosPhi2) * cos2;
                float y3 = minorRadius * sinPhi2;
                float z3 = (majorRadius + minorRadius * cosPhi2) * sin2;

                float x4 = (majorRadius + minorRadius * cosPhi2) * cos1;
                float y4 = minorRadius * sinPhi2;
                float z4 = (majorRadius + minorRadius * cosPhi2) * sin1;

                float u1 = (float)i / rings;
                float u2 = (float)(i + 1) / rings;
                float v1 = (float)j / loops;
                float v2 = (float)(j + 1) / loops;

                buffer.vertex(matrix, x1, y1, z1).uv(u1, v1).color(r, g, b, alpha).endVertex();
                buffer.vertex(matrix, x2, y2, z2).uv(u2, v1).color(r, g, b, alpha).endVertex();
                buffer.vertex(matrix, x3, y3, z3).uv(u2, v2).color(r, g, b, alpha).endVertex();
                buffer.vertex(matrix, x4, y4, z4).uv(u1, v2).color(r, g, b, alpha).endVertex();
            }
        }

        BufferUploader.drawWithShader(buffer.end());
    }

    @SubscribeEvent
    public static void onRenderNameplate(RenderNameTagEvent event) {
        event.setContent(Component.empty());
        event.getPoseStack().scale(0f, 0f, 0f); // Hide nameplates
    }

    public static class Ping {
        final Vec3 location;
        final long createdAt;
        final PingType type;

        Ping(PingType type, Vec3 location, long createdAt) {
            this.type = type;
            this.location = location;
            this.createdAt = createdAt;
        }
    }
}
