package com.samuelih.ezroyale.config;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;



public abstract class ConfigCommandMapper {
    protected String name;
    public Runnable onConfigChanged = () -> {};

    protected abstract String getCurrentValue();
    protected abstract ArgumentBuilder<CommandSourceStack, ?> getCommand();

    public ConfigCommandMapper(String name) {
        this.name = name;
    }

    public void registerConfigCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("ezroyale")
                        .requires(source -> source.hasPermission(2)) // Only allow ops to run this command
                        .then(Commands.literal("config")
                                .then(Commands.literal(name)
                                        .executes(ctx -> {
                                            ctx.getSource().sendSuccess(
                                                    Component.literal("Current " + name + ": " + getCurrentValue()),
                                                    false
                                            );
                                            return 1;
                                        })
                                )
                        )
        );

        dispatcher.register(
                Commands.literal("ezroyale")
                        .requires(source -> source.hasPermission(2)) // Only allow ops to run this command
                        .then(Commands.literal("config")
                                .then(Commands.literal(name)
                                        .then(getCommand())
                                )
                        )
        );
    }

    protected void didSetConfig(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(
                Component.literal("Set " + name + " to " + getCurrentValue()),
                false
        );

        onConfigChanged.run();
    }
}
