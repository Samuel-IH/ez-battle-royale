package com.samuelih.ezroyale.config;


import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class IntegerConfigMapper extends ConfigCommandMapper {
    private final Supplier<Integer> getter;
    private final Consumer<Integer> setter;
    private final int min;
    private final int max;

    public IntegerConfigMapper(String name, Supplier<Integer> getter, Consumer<Integer> setter, int min, int max) {
        super(name);
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
    }

    @Override
    protected String getCurrentValue() {
        return String.valueOf(getter.get());
    }

    @Override
    protected ArgumentBuilder<CommandSourceStack, ?> getCommand() {
        return Commands.argument("value", IntegerArgumentType.integer(min, max))
                .executes(ctx -> {
                    int value = IntegerArgumentType.getInteger(ctx, "value");
                    setter.accept(value);
                    didSetConfig(ctx);
                    return 1;
                });
    }
}
