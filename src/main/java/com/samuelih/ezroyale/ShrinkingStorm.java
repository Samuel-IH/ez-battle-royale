package com.samuelih.ezroyale;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;

public class ShrinkingStorm {
    private Vec3 spawnCenter;
    private Vec3 targetPos;

    private static final double BORDER_MOVEMENT_SPEED = 0.05;

    public void reset(ServerLevel level) {
        level.getWorldBorder().setSize(999999);
        level.getWorldBorder().setCenter(0, 0);
        level.setWeatherParameters(0, 240000, false, false);
    }

    public void prepare(ServerLevel level, Vec3 center) {
        spawnCenter = center;

        // randomize the center by a bit, to make it so that you can't know for sure where the center is until late game
        var randDist = Math.random() * Config.maxRandDistFromCenter * Config.maxWorldBorderSize;
        var randAngle = Math.random() * 2 * Math.PI;
        targetPos = new Vec3(center.x + randDist * Math.cos(randAngle), 0, center.z + randDist * Math.sin(randAngle));

        level.getWorldBorder().setSize(Config.maxWorldBorderSize);
        level.getWorldBorder().setCenter(targetPos.x, targetPos.z);

        level.setWeatherParameters(0, 240000, true, true);
    }

    public void start(ServerLevel level) {
        level.getWorldBorder().lerpSizeBetween(Config.maxWorldBorderSize, Config.minWorldBorderSize, (int)(Config.shrinkTime * 60 * 1000));
    }

    // if the spawnCenter is not inside the storm's boundaries, it will return the storm's center
    public Vec3 getSpawnCenter(ServerLevel level) {
        var centerX = level.getWorldBorder().getCenterX();
        var centerZ = level.getWorldBorder().getCenterZ();
        var center = new Vec3(centerX, spawnCenter.y, centerZ);
        var size = level.getWorldBorder().getSize();

        var dist = center.distanceTo(spawnCenter);
        if (dist > size / 2) {
            return center;
        }

        return spawnCenter;
    }

    public void tickStorm(ServerLevel level) {
        tryMoveWorldBorderRandomly(level);
        zapPlayers(level);
    }

    private void zapPlayers(ServerLevel level) {
        // find all players outside the border
        level.players().stream().filter(p -> !level.getWorldBorder().isWithinBounds(p.position().x, p.position().z)).forEach(p -> {
            // roll random die to see if they get zapped
            if (Math.random() > 0.01) { return; }

            var lightning = EntityType.LIGHTNING_BOLT.create(level);
            var position = p.position();

            if (lightning != null) {
                lightning.setPos(position.x, position.y, position.z);

                // Disable any default damage or fire caused by the lightning
                lightning.setVisualOnly(true);

                // Add the lightning entity to the world
                level.addFreshEntity(lightning);
            }
        });
    }

    private void tryMoveWorldBorderRandomly(ServerLevel level) {
        WorldBorder worldBorder = level.getWorldBorder();

        // Check if the world border has reached or is near the minimum size
        if (!(worldBorder.getSize() <= Config.minWorldBorderSize + 1)) { return; }

        // Make the border move randomly
        moveWorldBorderRandomly(worldBorder);
    }

    // Move the world border randomly once it reaches the minimum size
    private void moveWorldBorderRandomly(WorldBorder worldBorder) {
        // Random movement logic
        double currentX = worldBorder.getCenterX();
        double currentZ = worldBorder.getCenterZ();

        // get distance from next shift point
        double dist = Math.sqrt(Math.pow(currentX - targetPos.x, 2) + Math.pow(currentZ - targetPos.z, 2));

        // if close enough, set new shift point
        if (dist < 1) {
            var randDist = Math.random() * Config.maxRandDistFromCenter * Config.maxWorldBorderSize;
            var randAngle = Math.random() * 2 * Math.PI;
            targetPos = new Vec3(currentX + randDist * Math.cos(randAngle), 0, currentZ + randDist * Math.sin(randAngle));
        }

        // Adjust to move towards the next shift point, at a maximum speed of BORDER_MOVEMENT_SPEED
        double deltaX = Math.min(BORDER_MOVEMENT_SPEED, targetPos.x - currentX);
        deltaX = Math.max(-BORDER_MOVEMENT_SPEED, deltaX);
        double deltaZ = Math.min(BORDER_MOVEMENT_SPEED, targetPos.z - currentZ);
        deltaZ = Math.max(-BORDER_MOVEMENT_SPEED, deltaZ);

        // Set the new world border center
        worldBorder.setCenter(currentX + deltaX, currentZ + deltaZ);
        worldBorder.setSize(Config.minWorldBorderSize);
    }
}
