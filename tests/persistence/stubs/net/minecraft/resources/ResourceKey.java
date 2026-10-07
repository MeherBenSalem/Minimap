package net.minecraft.resources;
public record ResourceKey<T>(ResourceLocation location) { public Identifier identifier() { return new Identifier(location.value()); } }
