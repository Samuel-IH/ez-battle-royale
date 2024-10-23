package com.samuelih.ezroyale.config;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class DoubleConfigMapper extends ConfigCommandMapper {
    private final Supplier<Double> getter;
    private final Consumer<Double> setter;
    private final double min;
    private final double max;

    public DoubleConfigMapper(String name, Supplier<Double> getter, Consumer<Double> setter, double min, double max) {
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
        return Commands.argument("value", DoubleArgumentType.doubleArg(min, max))
                .executes(ctx -> {
                    double value = DoubleArgumentType.getDouble(ctx, "value");
                    setter.accept(value);
                    didSetConfig(ctx);
                    return 1;
                });
    }
}
