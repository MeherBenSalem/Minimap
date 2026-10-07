package net.minecraft.client.player;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
public class LocalPlayer {
    public Level level; public int x; public int z;
    public LocalPlayer(Level level) { this.level = level; }
    public double getX() { return x; } public double getZ() { return z; }
    public Level level() { return level; }
    public BlockPos blockPosition() { return new BlockPos(x, 64, z); }
}
