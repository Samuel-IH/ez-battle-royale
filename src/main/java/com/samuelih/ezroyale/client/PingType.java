package com.samuelih.ezroyale.client;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public enum PingType {
    GENERIC("generic", SoundEvents.NOTE_BLOCK_PLING, 1.0f, 1.0f, 1.0f),
    WARNING("warn", SoundEvents.NOTE_BLOCK_BASS, 1.0f, 0.2f, 0.2f),  // red tint
    LOOT("loot", SoundEvents.NOTE_BLOCK_PLING,     1.0f, 1.0f, 1.0f),
    GUN("gun", SoundEvents.NOTE_BLOCK_PLING,       1.0f, 1.0f, 1.0f),
    AMMO("ammo", SoundEvents.NOTE_BLOCK_PLING,     1.0f, 1.0f, 1.0f),
    MONEY("money", SoundEvents.NOTE_BLOCK_PLING,   1.0f, 1.0f, 1.0f);

    private final ResourceLocation texture;
    private final Holder.Reference<SoundEvent> sound;
    private final float r, g, b;

    PingType(String suffix, Holder.Reference<SoundEvent> sound, float r, float g, float b) {
        this.texture = ResourceLocation.fromNamespaceAndPath("money", "textures/item/ping_type_" + suffix + ".png");
        this.sound = sound;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public ResourceLocation getTexture() {
        return texture;
    }

    public float getR() { return r; }
    public float getG() { return g; }
    public float getB() { return b; }

    // Optional helper if you want a Vec3-like container
    public float[] getColor() {
        return new float[] { r, g, b };
    }

    public SoundEvent getSound() {
        return sound.get();
    }
}
