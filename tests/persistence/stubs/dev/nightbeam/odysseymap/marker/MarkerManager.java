package dev.nightbeam.odysseymap.marker;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
public class MarkerManager {
    private static final MarkerManager INSTANCE = new MarkerManager();
    public static MarkerManager get() { return INSTANCE; }
    public void clearSession() {} public void tick(Minecraft mc) {} public List<Object> getWaypoints() { return List.of(); }
    public void setBedPoint(ResourceKey<Level> dim, int x, int z) {} public void setDeathPoint(ResourceKey<Level> dim, int x, int z) {}
}
