package com.samuelih.ezroyale.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class ProjectHelper {
    public static Vec3 worldToScreen(Vec3 worldPos, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();

        Vec3 camPos = camera.getPosition();
        Vec3 rel = worldPos.subtract(camPos);

        Vector3f look = camera.getLookVector();
        Vector3f up = camera.getUpVector();

        Vec3 forward = new Vec3(look.x(), look.y(), look.z());
        Vec3 upVec = new Vec3(up.x(), up.y(), up.z());
        Vec3 right = forward.cross(upVec).normalize();


        double x = rel.dot(right);
        double y = rel.dot(upVec);
        double z = rel.dot(forward);

        if (z <= 0.1) return null;

        double screenWidth = mc.getWindow().getGuiScaledWidth();
        double screenHeight = mc.getWindow().getGuiScaledHeight();
        double aspect = screenWidth / screenHeight;
        double fov = Math.toRadians(mc.options.fov().get());
        double scale = screenHeight / (2.0 * Math.tan(fov / 2.0));

        double screenX = screenWidth / 2.0 + x * scale / z;
        double screenY = screenHeight / 2.0 - y * scale / z;

        return new Vec3(screenX, screenY, z);
    }
}



