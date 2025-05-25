package com.samuelih.ezroyale;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = EzRoyale.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TeamWeapons {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        var player = event.player;
        if (player == null) return;

        if (player.tickCount % 10 != 0) return; // once per second

        if (player.getTeam() == null) return;
        var teamColor = player.getTeamColor();

        // loop through all items in the player's inventory
        for (var item : player.getInventory().items) {
            // if item don't start with cgm: then ignore
            var key = ForgeRegistries.ITEMS.getKey(item.getItem());
            if (key == null) continue;
            if (!key.getNamespace().equals("cgm")) continue;

            var tag = item.getTag();
            if (tag == null) continue;

            // only weapons, and all weapons have this tag
            var ammoCount = tag.get("AmmoCount");
            if (ammoCount == null) continue;

            // if the item is a weapon, set the color to the team color
            tag.putInt("Color", teamColor);
        }
    }
}
