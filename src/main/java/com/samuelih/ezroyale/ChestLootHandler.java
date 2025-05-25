package com.samuelih.ezroyale;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = "ezroyale", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChestLootHandler {

    private static final String GENERATED_FOR_ROUND_KEY = "ezroyale:round_id";
    private static final ResourceLocation LOOT_TABLE = new ResourceLocation("tw", "chests/everything");

    public static class GameState {
        public static UUID currentRoundId = UUID.randomUUID();
    }

    public static void startNewLootRound() {
        GameState.currentRoundId = UUID.randomUUID();
    }

    @SubscribeEvent
    public static void onContainer(PlayerContainerEvent event) {
        Player player = event.getEntity();
        if (!(player.level instanceof ServerLevel level)) return;

        AbstractContainerMenu menu = event.getContainer();
        BlockEntity blockEntity = null;

        if (menu instanceof ChestMenu chestMenu) {
            blockEntity = tryGetBlockEntityFromContainer(level, chestMenu.getContainer());
        }

        if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity) {
            handleLoot(level, (RandomizableContainerBlockEntity) blockEntity);
        }
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        Block block = event.getPlacedBlock().getBlock();
        if (!(block instanceof ChestBlock || block instanceof BarrelBlock)) return;

        BlockEntity be = level.getBlockEntity(event.getPos());
        if (be instanceof RandomizableContainerBlockEntity container) {
            container.getPersistentData().putString(GENERATED_FOR_ROUND_KEY, GameState.currentRoundId.toString());
        }
    }

    private static BlockEntity tryGetBlockEntityFromContainer(ServerLevel level, Container container) {
        if (container instanceof RandomizableContainerBlockEntity rcbe) {
            return rcbe;
        }
        return null;
    }

    private static void handleLoot(ServerLevel level, RandomizableContainerBlockEntity chest) {
        var tag = chest.getPersistentData();
        String roundId = tag.getString(GENERATED_FOR_ROUND_KEY);

        if (GameState.currentRoundId.toString().equals(roundId)) return;

        // Wipe
        for (int i = 0; i < chest.getContainerSize(); i++) {
            chest.setItem(i, ItemStack.EMPTY);
        }

        // Fill with loot table
        applyLootTable(level, chest);

        // Mark
        tag.putString(GENERATED_FOR_ROUND_KEY, GameState.currentRoundId.toString());
    }

    private static void applyLootTable(ServerLevel level, RandomizableContainerBlockEntity chest) {
        LootTable table = level.getServer().getLootTables().get(LOOT_TABLE);
        LootContext ctx = new LootContext.Builder(level)
                .withParameter(LootContextParams.ORIGIN, chest.getBlockPos().getCenter())
                .create(LootContextParamSet.builder().build());
        table.fill(chest, ctx);
    }
}
