package net.minecraft.client;
import java.nio.file.Path;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.storage.LevelResource;
public class Minecraft {
    private static Minecraft instance;
    public Minecraft() { instance = this; }
    public static Minecraft getInstance() { return instance; }
    public net.minecraft.client.renderer.texture.TextureManager getTextureManager() { return new net.minecraft.client.renderer.texture.TextureManager(); }
    public final Gui gui = new Gui();
    public class Gui { public Object screen() { return screen; } public void setScreen(Object value) { screen = value; } }
    public ClientLevel level; public LocalPlayer player; public Object screen;
    public IntegratedServer singleplayerServer; public ServerData currentServer;
    public IntegratedServer getSingleplayerServer() { return singleplayerServer; }
    public ServerData getCurrentServer() { return currentServer; }
    public void setScreen(Object screen) { this.screen = screen; }
    public record IntegratedServer(Path saveDirectory) { public Path getWorldPath(LevelResource root) { return saveDirectory; } }
    public static class ServerData { public final String ip; public ServerData(String ip) { this.ip = ip; } }
}
