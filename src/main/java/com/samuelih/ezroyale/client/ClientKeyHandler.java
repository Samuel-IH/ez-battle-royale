package com.samuelih.ezroyale.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

@Mod.EventBusSubscriber(modid = "ezroyale", value = Dist.CLIENT)
public class ClientKeyHandler {

    private static final double PING_RANGE = 256.0D;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        if (KeybindRegistrar.PING_KEY.consumeClick()) {
            Vec3 from = mc.player.getEyePosition(1.0F);
            Vec3 to = from.add(mc.player.getLookAngle().scale(PING_RANGE));
            Level level = mc.level;

            // Do block raytrace
            HitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            Vec3 blockHitVec = blockHit.getLocation();

            // Check entity hit manually
            AABB bounds = mc.player.getBoundingBox().expandTowards(mc.player.getLookAngle().scale(PING_RANGE)).inflate(1.0);
            EntityHitResult entityHit = null;
            double closestDistance = PING_RANGE * PING_RANGE;

            for (Entity entity : level.getEntities(mc.player, bounds, EntitySelector.NO_SPECTATORS)) {
                AABB aabb = entity.getBoundingBox().inflate(0.3);
                Optional<Vec3> optional = aabb.clip(from, to);

                if (optional.isPresent()) {
                    double distance = from.distanceToSqr(optional.get());
                    if (distance < closestDistance) {
                        entityHit = new EntityHitResult(entity, optional.get());
                        closestDistance = distance;
                    }
                }
            }

            if (entityHit != null) {
                PingManager.addPing(entityHit.getLocation());
            } else if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
                PingManager.addPing(blockHitVec);
            }
        }
    }
}