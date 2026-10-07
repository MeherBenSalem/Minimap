package dev.nightbeam.odysseymap.world;
import net.minecraft.world.level.Level;
public class ColumnSampler { public int calls; public int sampleColumn(Level level, int x, int z) { calls++; return level.color; } }
