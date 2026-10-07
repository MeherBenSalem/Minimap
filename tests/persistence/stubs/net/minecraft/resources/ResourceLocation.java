package net.minecraft.resources;
public record ResourceLocation(String value) {
    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) { return new ResourceLocation(namespace + ":" + path); }
    public String toString() { return value; }
}
