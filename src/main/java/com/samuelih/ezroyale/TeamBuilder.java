package com.samuelih.ezroyale;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = EzRoyale.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class TeamBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static class SimpleTeam {
        public String name;
        public ChatFormatting color;

        public SimpleTeam(String name, ChatFormatting color) {
            this.name = name;
            this.color = color;
        }
    }

    private final SimpleTeam[] teams;

    public boolean allowSwitching = true;

    public TeamBuilder(SimpleTeam[] teams) {
        this.teams = teams;
    }

    public TeamBuilder() {
        this(new SimpleTeam[] {
            new SimpleTeam("Red", ChatFormatting.RED),
            new SimpleTeam("Blue", ChatFormatting.BLUE),
            new SimpleTeam("Green", ChatFormatting.GREEN),
            new SimpleTeam("Yellow", ChatFormatting.YELLOW),
            new SimpleTeam("Purple", ChatFormatting.DARK_PURPLE),
            new SimpleTeam("Aqua", ChatFormatting.AQUA)
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        LOGGER.debug("Creating teams");

        Scoreboard scoreboard = event.getServer().getScoreboard();

        for (SimpleTeam team : teams) {
            scoreboard.addPlayerTeam(team.name).setColor(team.color);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.debug("Registering team commands");

        var dispatcher = event.getDispatcher();

        for (SimpleTeam team : teams) {
            dispatcher.register(
                    Commands.literal("join")
                            .then(Commands.literal(team.name.toLowerCase())
                                    .executes(context -> joinTeam(context.getSource(), team.name)))
            );
        }
    }

    private int joinTeam(CommandSourceStack source, String teamName) {
        if (!allowSwitching) {
            Component message = Component.literal("You can't switch teams right now!");
            source.sendFailure(message);
            return 0;
        }

        var player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        Scoreboard scoreboard = player.getScoreboard();
        scoreboard.removePlayerFromTeam(player.getScoreboardName());
        var team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            return 0;
        }
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);

        Component message = Component.literal("You are now on the the " + teamName + " team!");
        source.sendSuccess(message, true);

        return 1;
    }
}
