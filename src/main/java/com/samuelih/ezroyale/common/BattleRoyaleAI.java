package com.samuelih.ezroyale.common;

import com.samuelih.ezroyale.EzRoyale;
import com.samuelih.ezroyale.GamePhase;
import com.samuelih.ezroyale.GameState;
import com.samuelih.ezroyale.ShrinkingStorm;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class BattleRoyaleAI {
    private static final int CHEST_SCAN_RADIUS = 32;

    public static void applyAI(Zombie zombie, GameState gameState, ShrinkingStorm storm) {
        zombie.goalSelector.removeAllGoals(goal -> true);
        zombie.targetSelector.removeAllGoals(goal -> true);

        zombie.setPersistenceRequired();

        zombie.goalSelector.addGoal(0, new AvoidStormGoal(zombie, storm));
        zombie.goalSelector.addGoal(1, new MoveToContainerGoal(zombie, gameState));
        zombie.targetSelector.addGoal(0,
            new NearestAttackableTargetGoal<>(zombie, Player.class, true));
        zombie.goalSelector.addGoal(2,
            new MeleeAttackGoal(zombie, 1.0D, true));
        zombie.goalSelector.addGoal(3, new EquipDiamondArmorGoal(zombie, gameState));
        zombie.goalSelector.addGoal(4, new TrendToCenterGoal(zombie, gameState, storm));
    }

    private static class AvoidStormGoal extends Goal {
        private final Zombie zombie;
        private final ShrinkingStorm storm;

        AvoidStormGoal(Zombie zombie, ShrinkingStorm storm) {
            this.zombie = zombie;
            this.storm = storm;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            Level world = zombie.level();
            if (!(world instanceof ServerLevel)) {
                return false;
            }
            WorldBorder border = world.getWorldBorder();
            return !border.isWithinBounds(zombie.getX(), zombie.getZ());
        }

        @Override
        public void start() {
            updatePath();
        }

        @Override
        public void tick() {
            updatePath();
        }

        private void updatePath() {
            ServerLevel world = (ServerLevel) zombie.level();
            Vec3 spawnCenter = storm.getSpawnCenter(world);
            BlockPos safe = new BlockPos((int) spawnCenter.x, (int) spawnCenter.y, (int) spawnCenter.z);
            zombie.getNavigation().moveTo(safe.getX(), safe.getY(), safe.getZ(), 1.0D);
        }
    }

    private static class MoveToContainerGoal extends Goal {
        private final Zombie zombie;
        private final GameState gameState;
        private BlockPos targetPos;

        MoveToContainerGoal(Zombie zombie, GameState gameState) {
            this.zombie = zombie;
            this.gameState = gameState;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (gameState.getPhase() != GamePhase.RUNNING) {
                return false;
            }
            Level world = zombie.level();
            BlockPos center = zombie.blockPosition();
            double bestDist = Double.MAX_VALUE;
            BlockPos bestPos = null;
            for (int dx = -CHEST_SCAN_RADIUS; dx <= CHEST_SCAN_RADIUS; dx++) {
                for (int dz = -CHEST_SCAN_RADIUS; dz <= CHEST_SCAN_RADIUS; dz++) {
                    BlockPos pos = center.offset(dx, 0, dz);
                    BlockEntity be = world.getBlockEntity(pos);
                    if (be instanceof RandomizableContainerBlockEntity) {
                        Container container = (RandomizableContainerBlockEntity) be;
                        if (hasValuable(container)) {
                            double dist = center.distSqr(pos);
                            if (dist < bestDist) {
                                bestDist = dist;
                                bestPos = pos;
                            }
                        }
                    }
                }
            }
            if (bestPos != null && zombie.getNavigation().createPath(bestPos, 1) != null) {
                targetPos = bestPos;
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return targetPos != null && !zombie.getNavigation().isDone();
        }

        @Override
        public void start() {
            zombie.getNavigation().moveTo(
                targetPos.getX() + 0.5,
                targetPos.getY() + 1,
                targetPos.getZ() + 0.5,
                1.0D);
        }

        @Override
        public void tick() {
            if (targetPos != null && zombie.blockPosition().closerThan(targetPos, 1.5D)) {
                Level world = zombie.level();
                BlockEntity be = world.getBlockEntity(targetPos);
                if (be instanceof RandomizableContainerBlockEntity) {
                    RandomizableContainerBlockEntity container = (RandomizableContainerBlockEntity) be;
                    pickUpValuables(zombie, container);
                    container.setChanged();
                }
                targetPos = null;
            }
        }

        @Override
        public void stop() {
            targetPos = null;
        }

        private boolean hasValuable(Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && (stack.getItem() == EzRoyale.MONEY.get()
                    || stack.getItem() == Items.DIAMOND)) {
                    return true;
                }
            }
            return false;
        }

        private void pickUpValuables(Zombie zombie, Container container) {
            CompoundTag data = zombie.getPersistentData();
            int moneyCount = data.getInt("BR_money");
            int diamondCount = data.getInt("BR_diamonds");
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                int count = stack.getCount();
                if (stack.getItem() == EzRoyale.MONEY.get()) {
                    moneyCount += count;
                    container.removeItem(i, count);
                } else if (stack.getItem() == Items.DIAMOND) {
                    diamondCount += count;
                    container.removeItem(i, count);
                }
            }
            data.putInt("BR_money", moneyCount);
            data.putInt("BR_diamonds", diamondCount);
        }
    }

    private static class EquipDiamondArmorGoal extends Goal {
        private final Zombie zombie;
        private final GameState gameState;

        EquipDiamondArmorGoal(Zombie zombie, GameState gameState) {
            this.zombie = zombie;
            this.gameState = gameState;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (gameState.getPhase() != GamePhase.RUNNING) {
                return false;
            }
            CompoundTag data = zombie.getPersistentData();
            int diamonds = data.getInt("BR_diamonds");
            if (diamonds <= 0) {
                return false;
            }
            if (!zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                && !zombie.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                && !zombie.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                && !zombie.getItemBySlot(EquipmentSlot.FEET).isEmpty()) {
                return false;
            }
            return true;
        }

        @Override
        public void start() {
            CompoundTag data = zombie.getPersistentData();
            int diamonds = data.getInt("BR_diamonds");
            if (zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && diamonds > 0) {
                zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                data.putInt("BR_diamonds", diamonds - 1);
                return;
            }
            if (zombie.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && diamonds > 0) {
                zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                data.putInt("BR_diamonds", diamonds - 1);
                return;
            }
            if (zombie.getItemBySlot(EquipmentSlot.LEGS).isEmpty() && diamonds > 0) {
                zombie.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
                data.putInt("BR_diamonds", diamonds - 1);
                return;
            }
            if (zombie.getItemBySlot(EquipmentSlot.FEET).isEmpty() && diamonds > 0) {
                zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                data.putInt("BR_diamonds", diamonds - 1);
            }
        }
    }

    private static class TrendToCenterGoal extends Goal {
        private final Zombie zombie;
        private final GameState gameState;
        private final ShrinkingStorm storm;

        TrendToCenterGoal(Zombie zombie, GameState gameState, ShrinkingStorm storm) {
            this.zombie = zombie;
            this.gameState = gameState;
            this.storm = storm;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return gameState.getPhase() == GamePhase.RUNNING
                && zombie.getNavigation().isDone();
        }

        @Override
        public void tick() {
            ServerLevel world = (ServerLevel) zombie.level();
            Vec3 spawnCenter = storm.getSpawnCenter(world);
            BlockPos centerPos = new BlockPos((int) spawnCenter.x, (int) spawnCenter.y, (int) spawnCenter.z);
            zombie.getNavigation().moveTo(centerPos.getX(), centerPos.getY(), centerPos.getZ(), 0.8D);
        }
    }
}