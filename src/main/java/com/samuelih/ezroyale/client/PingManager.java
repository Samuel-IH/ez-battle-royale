package com.samuelih.ezroyale.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
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

        // search to see if there's any existing pings within 1 meter, within the last half second
        if (type == PingType.GENERIC) {
            for (Ping ping : active) {
                if (ping.type == type && ping.location.distanceToSqr(location) < 1.0 && (now - ping.createdAt) < 500) {
                    // Remove the old ping
                    active.remove(ping);
                    // Change the new one to WARNING
                    type = PingType.WARNING;
                    break;
                }
            }
        }

        active.add(new Ping(type, location, System.currentTimeMillis()));

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
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

        // Compute direction from object to camera (relative horizontal vector)
        Vec3 lookVec = camPos.subtract(location); // worldPos = position of object you're rendering at
        float yaw = (float) Math.atan2(lookVec.x, lookVec.z); // rotation around Y

        poseStack.pushPose();
        poseStack.translate(location.x - camPos.x, location.y - camPos.y, location.z - camPos.z);

        // scale based on FoV and distance to camera to keep size consistent
        float distance = (float) camPos.distanceTo(location);
        float fov = Minecraft.getInstance().options.fov().get(); // degrees
        float baseScale = 0.25f; // arbitrary "normal" size at distance = 1
        // Convert vertical FOV in degrees to radians
        float fovRadians = (float) Math.toRadians(fov);
        // Perspective projection scale factor (tan(fov / 2))
        float perspectiveFactor = (float) Math.tan(fovRadians / 2.0);
        // Final scale
        float scale = baseScale * distance * perspectiveFactor;

        // Draw the base, it's rotated so it lies flat on the ground
        poseStack.pushPose();
        renderTorus(PING_LINE_TEX, poseStack, 0.005f * scale, 0.5f, 32, 32, 1f); // Draw the torus around the base
        poseStack.popPose();

        // Draw the vertical line. This one stands up straight, does face the camera, but this one is X/Z locked (only y billboard)
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(new Quaternionf().rotationY(yaw + (float)Math.PI)); // Y-only billboard
        var lineThickness = 0.01f;
        renderIcon(PING_LINE_TEX, poseStack, lineThickness / -2f, 0, lineThickness, 1, 0, 1, 1, 0, 1);
        poseStack.popPose();

        // Draw the ping icon, this one copies the camera's rotation so it always faces the camera, even vertically!
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(camRot);
        //poseStack.translate(0, 0.5f, 0); // Offset slightly above the base
        renderIcon(ping.type.getTexture(), poseStack, -0.5f, 1, 1, 1, 0, 1, 1, 0, 1);
        poseStack.popPose();

        poseStack.popPose();
    }

    private static void renderIcon(ResourceLocation icon, PoseStack poseStack, float x, float y, float w, float h, float u0, float u1, float v0, float v1, float alpha)
    {
        Matrix4f matrix = poseStack.last().pose();

        Minecraft.getInstance().getTextureManager().getTexture(icon).setFilter(false, false);
        RenderSystem.setShaderTexture(0, icon);

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bufferbuilder.vertex(matrix, x,			(y + h),	0).uv(u0, v1).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
        bufferbuilder.vertex(matrix, (x + w),	(y + h),	0).uv(u1, v1).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
        bufferbuilder.vertex(matrix, (x + w),	y,		0).uv(u1, v0).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
        bufferbuilder.vertex(matrix, x,			y,		0).uv(u0, v0).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
        BufferUploader.drawWithShader(bufferbuilder.end());
    }

    private static void renderTorus(ResourceLocation texture, PoseStack poseStack, float minorRadius, float majorRadius, int rings, int loops, float alpha)
    {
        Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().getTexture(texture).setFilter(false, false);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int i = 0; i < rings; i++)
        {
            float theta1 = (float) (2 * Math.PI * i / rings);
            float theta2 = (float) (2 * Math.PI * (i + 1) / rings);

            float cos1 = (float) Math.cos(theta1);
            float sin1 = (float) Math.sin(theta1);
            float cos2 = (float) Math.cos(theta2);
            float sin2 = (float) Math.sin(theta2);

            for (int j = 0; j < loops; j++)
            {
                float phi1 = (float) (2 * Math.PI * j / loops);
                float phi2 = (float) (2 * Math.PI * (j + 1) / loops);

                float cosPhi1 = (float) Math.cos(phi1);
                float sinPhi1 = (float) Math.sin(phi1);
                float cosPhi2 = (float) Math.cos(phi2);
                float sinPhi2 = (float) Math.sin(phi2);

                // Four corners of the quad
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

                // Texture UVs are rough (tiled uniformly)
                float u1 = (float)i / rings;
                float u2 = (float)(i + 1) / rings;
                float v1 = (float)j / loops;
                float v2 = (float)(j + 1) / loops;

                buffer.vertex(matrix, x1, y1, z1).uv(u1, v1).color(1f, 1f, 1f, alpha).endVertex();
                buffer.vertex(matrix, x2, y2, z2).uv(u2, v1).color(1f, 1f, 1f, alpha).endVertex();
                buffer.vertex(matrix, x3, y3, z3).uv(u2, v2).color(1f, 1f, 1f, alpha).endVertex();
                buffer.vertex(matrix, x4, y4, z4).uv(u1, v2).color(1f, 1f, 1f, alpha).endVertex();
            }
        }

        BufferUploader.drawWithShader(buffer.end());
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
