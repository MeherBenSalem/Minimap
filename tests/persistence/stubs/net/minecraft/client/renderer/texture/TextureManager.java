package net.minecraft.client.renderer.texture;
import net.minecraft.resources.ResourceLocation;
public class TextureManager { public void register(net.minecraft.resources.Identifier id, DynamicTexture texture) {} public ResourceLocation register(String name, DynamicTexture texture) { return new ResourceLocation(name); } }
