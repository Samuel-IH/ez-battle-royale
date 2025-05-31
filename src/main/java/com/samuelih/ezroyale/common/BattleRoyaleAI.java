package com.samuelih.ezroyale.common;

import com.mojang.logging.LogUtils;
import com.samuelih.ezroyale.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.EnumSet;

public class BattleRoyaleAI {
    private static final int CHEST_SCAN_RADIUS = 32;
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation GUN_ID = ResourceLocation.fromNamespaceAndPath("tacz", "modern_kinetic_gun");
    public static final Item GUN_ITEM = ForgeRegistries.ITEMS.getValue(GUN_ID);

    public static void applyAI(Skeleton skeleton, GameState gameState, ShrinkingStorm storm) {
        skeleton.goalSelector.removeAllGoals(goal -> true);
        skeleton.targetSelector.removeAllGoals(goal -> true);

        skeleton.setPersistenceRequired();

        skeleton.goalSelector.addGoal(0, new AvoidStormGoal(skeleton, storm));
        skeleton.goalSelector.addGoal(1, new MoveToContainerGoal(skeleton, gameState));
        skeleton.targetSelector.addGoal(0,
            new NearestAttackableTargetGoal<>(skeleton, Player.class, true));
        skeleton.goalSelector.addGoal(2,
            new MeleeAttackGoal(skeleton, 1.0D, true));
        skeleton.goalSelector.addGoal(3, new EquipDiamondArmorGoal(skeleton, gameState));
        skeleton.goalSelector.addGoal(4, new TrendToCenterGoal(skeleton, gameState, storm));
    }

    public static void debug(Skeleton skeleton, String message) {
        LOGGER.info("[AI] {}", message);
        CompoundTag data = skeleton.getPersistentData();
        if (data.getBoolean("BR_debug")) {
            ServerLevel world = (ServerLevel) skeleton.level();
            world.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("[AI DEBUG] " + message), false);
        }
    }

    private static class AvoidStormGoal extends Goal {
        private final Skeleton skeleton;
        private final ShrinkingStorm storm;

        AvoidStormGoal(Skeleton skeleton, ShrinkingStorm storm) {
            this.skeleton = skeleton;
            this.storm = storm;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            Level world = skeleton.level();
            boolean result = world instanceof ServerLevel &&
                !world.getWorldBorder().isWithinBounds(
                    skeleton.getX(), skeleton.getZ());
            debug(skeleton, "AvoidStormGoal.canUse=" + result);
            return result;
        }

        @Override
        public void start() {
            debug(skeleton, "AvoidStormGoal.start");
            updatePath();
        }

        @Override
        public void tick() {
            debug(skeleton, "AvoidStormGoal.tick");
            updatePath();
        }

        private void updatePath() {
            ServerLevel world = (ServerLevel) skeleton.level();
            Vec3 spawnCenter = storm.getSpawnCenter(world);
            BlockPos safe = new BlockPos(
                (int) spawnCenter.x, (int) spawnCenter.y, (int) spawnCenter.z);
            debug(skeleton, "AvoidStormGoal.updatePath -> " + safe);
            skeleton.getNavigation().moveTo(
                safe.getX(), safe.getY(), safe.getZ(), 1.0D);
        }
    }

    private static class MoveToContainerGoal extends Goal {
        private final Skeleton skeleton;
        private final GameState gameState;
        private BlockPos targetPos;

        MoveToContainerGoal(Skeleton skeleton, GameState gameState) {
            this.skeleton = skeleton;
            this.gameState = gameState;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            debug(skeleton, "MoveToContainerGoal.canUse");
            if (gameState.getPhase() != GamePhase.RUNNING) {
                debug(skeleton, "MoveToContainerGoal.canUse: not RUNNING");
                return false;
            }
            Level world = skeleton.level();
            BlockPos center = skeleton.blockPosition();
            double bestDist = Double.MAX_VALUE;
            BlockPos bestPos = null;
            for (int dx = -CHEST_SCAN_RADIUS; dx <= CHEST_SCAN_RADIUS; dx++) {
                for (int dz = -CHEST_SCAN_RADIUS; dz <= CHEST_SCAN_RADIUS; dz++) {
                    BlockPos pos = center.offset(dx, 0, dz);
                    BlockEntity be = world.getBlockEntity(pos);
                    if (be instanceof RandomizableContainerBlockEntity container
                        && hasValuable((ServerLevel)world, container)) {
                        double dist = center.distSqr(pos);
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestPos = pos;
                        }
                    }
                }
            }
            if (bestPos != null && skeleton.getNavigation().createPath(bestPos, 1) != null) {
                debug(skeleton, "MoveToContainerGoal.canUse: found " + bestPos);
                targetPos = bestPos;
                return true;
            }
            debug(skeleton, "MoveToContainerGoal.canUse: no target");
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            boolean cont = targetPos != null && !skeleton.getNavigation().isDone();
            debug(skeleton, "MoveToContainerGoal.canContinueToUse=" + cont);
            return cont;
        }

        @Override
        public void start() {
            debug(skeleton, "MoveToContainerGoal.start -> " + targetPos);
            skeleton.getNavigation().moveTo(
                targetPos.getX() + 0.5,
                targetPos.getY() + 1,
                targetPos.getZ() + 0.5,
                1.0D);
        }

        @Override
        public void tick() {
            debug(skeleton, "MoveToContainerGoal.tick target=" + targetPos);
            if (targetPos != null && skeleton.blockPosition().closerThan(targetPos, 1.5D)) {
                Level world = skeleton.level();
                BlockEntity be = world.getBlockEntity(targetPos);
                if (be instanceof RandomizableContainerBlockEntity container) {
                    pickUpValuables(skeleton, container);
                    container.setChanged();
                }
                debug(skeleton, "MoveToContainerGoal.tick arrived at " + targetPos);
                targetPos = null;
            }
        }

        @Override
        public void stop() {
            debug(skeleton, "MoveToContainerGoal.stop");
            targetPos = null;
        }

        private boolean hasValuable(ServerLevel level, RandomizableContainerBlockEntity container) {
            ChestLootHandler.handleLootIfNeeded(level, container);
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                Item item = stack.getItem();
                if (item == EzRoyale.MONEY.get() || item == Items.DIAMOND) {
                    return true;
                }
                if (skeleton.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && item == GUN_ITEM) {
                    return true;
                }
            }
            return false;
        }

        private void pickUpValuables(Skeleton skeleton, Container container) {
            CompoundTag data = skeleton.getPersistentData();
            int moneyCount = data.getInt("BR_money");
            int diamondCount = data.getInt("BR_diamonds");
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                Item item = stack.getItem();
                if (skeleton.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && item == GUN_ITEM) {
                    ItemStack gun = container.removeItem(i, 1);
                    skeleton.setItemSlot(EquipmentSlot.MAINHAND, gun);
                    debug(skeleton, "pickUpValuables: equipped gun " + gun);
                    continue;
                }
                int count = stack.getCount();
                if (item == EzRoyale.MONEY.get()) {
                    moneyCount += count;
                    container.removeItem(i, count);
                } else if (item == Items.DIAMOND) {
                    diamondCount += count;
                    container.removeItem(i, count);
                }
            }
            data.putInt("BR_money", moneyCount);
            data.putInt("BR_diamonds", diamondCount);
            debug(skeleton, String.format("pickUpValuables: money=%d, diamonds=%d", moneyCount, diamondCount));
        }
    }

    private static class EquipDiamondArmorGoal extends Goal {
        private final Skeleton skeleton;
        private final GameState gameState;

        EquipDiamondArmorGoal(Skeleton skeleton, GameState gameState) {
            this.skeleton = skeleton;
            this.gameState = gameState;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            boolean can = gameState.getPhase() == GamePhase.RUNNING
                && skeleton.getPersistentData().getInt("BR_diamonds") > 0
                && (skeleton.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                    || skeleton.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                    || skeleton.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                    || skeleton.getItemBySlot(EquipmentSlot.FEET).isEmpty());
            debug(skeleton, "EquipDiamondArmorGoal.canUse=" + can);
            return can;
        }

        @Override
        public void start() {
            CompoundTag data = skeleton.getPersistentData();
            int diamonds = data.getInt("BR_diamonds");
            debug(skeleton, "EquipDiamondArmorGoal.start diamonds=" + diamonds);
            if (skeleton.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && diamonds >= 5) {
                skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                data.putInt("BR_diamonds", diamonds - 5);
                debug(skeleton, "EquipDiamondArmorGoal.equipped HEAD");
                return;
            }
            if (skeleton.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && diamonds >= 8) {
                skeleton.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                data.putInt("BR_diamonds", diamonds - 8);
                debug(skeleton, "EquipDiamondArmorGoal.equipped CHEST");
                return;
            }
            if (skeleton.getItemBySlot(EquipmentSlot.LEGS).isEmpty() && diamonds >= 7) {
                skeleton.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
                data.putInt("BR_diamonds", diamonds - 7);
                debug(skeleton, "EquipDiamondArmorGoal.equipped LEGS");
                return;
            }
            if (skeleton.getItemBySlot(EquipmentSlot.FEET).isEmpty() && diamonds >= 4) {
                skeleton.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                data.putInt("BR_diamonds", diamonds - 4);
                debug(skeleton, "EquipDiamondArmorGoal.equipped FEET");
            }
        }
    }

    private static class TrendToCenterGoal extends Goal {
        private final Skeleton skeleton;
        private final GameState gameState;
        private final ShrinkingStorm storm;

        TrendToCenterGoal(Skeleton skeleton, GameState gameState, ShrinkingStorm storm) {
            this.skeleton = skeleton;
            this.gameState = gameState;
            this.storm = storm;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            boolean can = gameState.getPhase() == GamePhase.RUNNING
                && skeleton.getNavigation().isDone();
            debug(skeleton, "TrendToCenterGoal.canUse=" + can);
            return can;
        }

        @Override
        public void tick() {
            ServerLevel world = (ServerLevel) skeleton.level();
            Vec3 spawnCenter = storm.getSpawnCenter(world);
            BlockPos centerPos = new BlockPos(
                (int) spawnCenter.x, (int) spawnCenter.y, (int) spawnCenter.z);
            debug(skeleton, "TrendToCenterGoal.tick -> " + centerPos);
            skeleton.getNavigation().moveTo(
                centerPos.getX(), centerPos.getY(), centerPos.getZ(), 0.8D);
        }
    }
}