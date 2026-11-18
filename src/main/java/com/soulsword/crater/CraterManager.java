package com.soulsword.crater;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Carves Annihilation craters progressively: every server tick a fixed time budget is spent removing
 * blocks column by column, starting at the crater centre and moving outwards chunk by chunk.
 * Large craters therefore take a few seconds to open instead of freezing the server for one huge tick.
 */
public final class CraterManager {
    /** Wall-clock budget per tick (a tick is 50 ms). */
    private static final long BUDGET_NANOS = 12_000_000L;
    private static final int FLAGS = 2 | 16; // send to clients, skip neighbour shape updates
    private static final List<Job> JOBS = new ArrayList<>();

    private CraterManager() {}

    private static final class Job {
        final ResourceKey<Level> dimension;
        final int cx, cy, cz, radius, depth, bottomY;
        final int[] chunkX, chunkZ;
        int chunkIndex = 0;
        int column = 0;

        Job(ResourceKey<Level> dimension, int cx, int cy, int cz, int radius, int depth, int bottomY, int[] chunkX, int[] chunkZ) {
            this.dimension = dimension;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.radius = radius;
            this.depth = depth;
            this.bottomY = bottomY;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }

    public static void queue(ServerLevel level, BlockPos center, int radius, int depth) {
        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;
        List<int[]> chunks = new ArrayList<>();
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                double nx = Math.max((x << 4) - center.getX(), Math.max(0, center.getX() - ((x << 4) + 15)));
                double nz = Math.max((z << 4) - center.getZ(), Math.max(0, center.getZ() - ((z << 4) + 15)));
                if (nx * nx + nz * nz <= (double) radius * radius) {
                    chunks.add(new int[]{x, z});
                }
            }
        }
        chunks.sort(Comparator.comparingDouble(c -> {
            double dx = (c[0] << 4) + 8 - center.getX();
            double dz = (c[1] << 4) + 8 - center.getZ();
            return dx * dx + dz * dz;
        }));
        int[] xs = new int[chunks.size()];
        int[] zs = new int[chunks.size()];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = chunks.get(i)[0];
            zs[i] = chunks.get(i)[1];
        }
        int bottomY = level.getMinBuildHeight() + 5; // keeps the bedrock layer itself intact
        synchronized (JOBS) {
            JOBS.add(new Job(level.dimension(), center.getX(), center.getY(), center.getZ(), radius, depth, bottomY, xs, zs));
        }
    }

    public static void clear() {
        synchronized (JOBS) {
            JOBS.clear();
        }
    }

    public static boolean isBusy() {
        synchronized (JOBS) {
            return !JOBS.isEmpty();
        }
    }

    /** Called once per server tick. Only the oldest job runs, so total cost per tick stays bounded. */
    public static void tick(MinecraftServer server) {
        Job job;
        synchronized (JOBS) {
            if (JOBS.isEmpty()) return;
            job = JOBS.get(0);
        }
        ServerLevel level = server.getLevel(job.dimension);
        if (level == null) {
            synchronized (JOBS) {
                JOBS.remove(job);
            }
            return;
        }
        long deadline = System.nanoTime() + BUDGET_NANOS;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        double r2 = (double) job.radius * job.radius;

        while (job.chunkIndex < job.chunkX.length && System.nanoTime() < deadline) {
            int chunkX = job.chunkX[job.chunkIndex];
            int chunkZ = job.chunkZ[job.chunkIndex];
            LevelChunk chunk = level.getChunk(chunkX, chunkZ);
            while (job.column < 256) {
                int col = job.column++;
                int lx = col & 15;
                int lz = col >> 4;
                int x = (chunkX << 4) + lx;
                int z = (chunkZ << 4) + lz;
                double dx = x - job.cx;
                double dz = z - job.cz;
                double d2 = dx * dx + dz * dz;
                if (d2 <= r2) {
                    double bowl = 1.0D - d2 / r2; // 1 in the middle, 0 at the rim
                    int floorY = Math.max(job.bottomY, job.cy - (int) Math.round(job.depth * bowl));
                    int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz) - 1;
                    for (int y = top; y >= floorY; y--) {
                        pos.set(x, y, z);
                        BlockState state = chunk.getBlockState(pos);
                        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) continue; // never touch bedrock/barriers
                        level.setBlock(pos, air, FLAGS, 0);
                    }
                }
                if (System.nanoTime() >= deadline) break;
            }
            if (job.column >= 256) {
                job.column = 0;
                job.chunkIndex++;
            }
        }
        if (job.chunkIndex >= job.chunkX.length) {
            synchronized (JOBS) {
                JOBS.remove(job);
            }
        }
    }
}
