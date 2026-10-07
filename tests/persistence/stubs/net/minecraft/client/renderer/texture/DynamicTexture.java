package net.minecraft.client.renderer.texture;
import com.mojang.blaze3d.platform.NativeImage;
public class DynamicTexture { public static NativeImage lastImage;
    public DynamicTexture(NativeImage image) { lastImage = image; }
    public DynamicTexture(java.util.function.Supplier<String> name, NativeImage image) { this(image); }
    public void upload() {} public void close() {}
}
