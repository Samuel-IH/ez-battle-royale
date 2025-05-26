package com.samuelih.ezroyale.client;

import com.samuelih.ezroyale.common.NetworkHandler;
import com.samuelih.ezroyale.common.PingPacket;
import com.samuelih.ezroyale.common.PingType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

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
            if (level == null) return;
            BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
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
                addEntityPing(entityHit);
            } else if (blockHit.getType() != HitResult.Type.MISS) {
                addBlockPing(level, blockHit);
            }
        }
    }

    private static void addBlockPing(Level level, BlockHitResult blockHit) {
        var blockPos = blockHit.getBlockPos();
        var state = level.getBlockState(blockPos);

        if (state.is(Blocks.BARREL)) {
            sendPing(PingType.GENERIC, blockHit.getLocation());
        } else if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) {
            sendPing(PingType.LOOT, blockHit.getLocation());
        } else {
            sendPing(PingType.GENERIC, blockHit.getLocation());
        }
    }

    private static void addEntityPing(EntityHitResult entityHit) {
        Entity entity = entityHit.getEntity();
        Vec3 entityPos = entity.position();

        if (!(entity instanceof ItemEntity itemEntity)) {
            sendPing(PingType.GENERIC, entityPos);
            return;
        }

        ItemStack stack = itemEntity.getItem();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());

        if (itemId != null) {
            String namespace = itemId.getNamespace();
            String path = itemId.getPath();

            if (namespace.equals("money") && path.equals("money")) {
                sendPing(PingType.MONEY, entityPos);
            } else if (namespace.equals("gun") && path.equals("gun")) {
                sendPing(PingType.GUN, entityPos);
            } else if (namespace.equals("ammo") && path.equals("ammo")) {
                sendPing(PingType.AMMO, entityPos);
            } else {
                sendPing(PingType.GENERIC, entityPos);
            }
        }
    }

    private static void sendPing(PingType type, Vec3 location) {
        NetworkHandler.CHANNEL.sendToServer(new PingPacket(type, location));
    }
}