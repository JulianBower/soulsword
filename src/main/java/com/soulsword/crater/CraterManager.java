package com.soulsword.crater;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Carves Annihilation craters the way the beam drills: a carving front (the beam tip) moves from the surface down to
 * the crater floor over {@code drillTicks} ticks, and every column inside the bowl is removed from the top down to
 * {@code max(bowlFloor(column), tipY)}. The crater therefore opens layer by layer, centre first, and ends as a smooth
 * paraboloid bowl whose centre reaches bedrock at the soul cap.
 * <p>
 * Work is time-boxed per tick ({@link #BUDGET_NANOS}); if the server cannot keep up the carving simply lags behind the
 * tip instead of stalling the tick. Only already-loaded chunks are touched, so no chunk generation can lag the server.
 */
public final class CraterManager {
    /** Wall-clock budget per tick (a tick is 50 ms). */
    private static final long BUDGET_NANOS = 14_000_000L;
    private static final int FLAGS = 2 | 16; // send to clients, skip neighbour shape updates
    private static final int UNTOUCHED = Integer.MAX_VALUE;
    private static final int SKIPPED = Integer.MIN_VALUE;
    private static final List<Job> JOBS = new ArrayList<>();

    private CraterManager() {}

    private static final class Job {
        final ResourceKey<Level> dimension;
        final int cx, cy, cz, radius, depth, bottomY, drillTicks;
        final long startTick;
        final int[] chunkX, chunkZ;
        /** Per chunk, per column: lowest Y already carved (UNTOUCHED = nothing yet, SKIPPED = chunk not loaded). */
        final int[][] carved;
        int chunkIndex = 0;
        int column = 0;
        boolean dirty = false;
        int wraps = 0;

        Job(ResourceKey<Level> dimension, int cx, int cy, int cz, int radius, int depth, int bottomY, int drillTicks,
            long startTick, int[] chunkX, int[] chunkZ) {
            this.dimension = dimension;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.radius = radius;
            this.depth = depth;
            this.bottomY = bottomY;
            this.drillTicks = Math.max(1, drillTicks);
            this.startTick = startTick;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.carved = new int[chunkX.length][256];
            for (int[] c : this.carved) {
                Arrays.fill(c, UNTOUCHED);
            }
        }

        /** Y of the carving front (the beam tip) at the given game time. */
        int tipY(long now) {
            float p = Mth.clamp((now - startTick) / (float) drillTicks, 0.0F, 1.0F);
            return cy - Math.round(depth * p);
        }

        boolean tipFinished(long now) {
            return now - startTick >= drillTicks;
        }
    }

    public static void queue(ServerLevel level, BlockPos center, int radius, int depth, int drillTicks) {
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
            JOBS.add(new Job(level.dimension(), center.getX(), center.getY(), center.getZ(), radius, depth, bottomY,
                    drillTicks, level.getGameTime(), xs, zs));
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
            finish(job);
            return;
        }
        long now = level.getGameTime();
        int tipY = job.tipY(now);
        long deadline = System.nanoTime() + BUDGET_NANOS;
        job.wraps = 0;
        double r2 = (double) job.radius * job.radius;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        int minY = level.getMinBuildHeight();

        while (System.nanoTime() < deadline) {
            if (job.chunkIndex >= job.chunkX.length) {
                // one full pass finished
                if (!job.dirty && job.tipFinished(now)) {
                    finish(job);
                    return;
                }
                job.dirty = false;
                job.chunkIndex = 0;
                job.column = 0;
                if (++job.wraps >= 2) {
                    return; // nothing more to do this tick; the tip has not moved further yet
                }
                continue;
            }
            int chunkX = job.chunkX[job.chunkIndex];
            int chunkZ = job.chunkZ[job.chunkIndex];
            int[] carved = job.carved[job.chunkIndex];
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) {
                Arrays.fill(carved, SKIPPED); // never generate or load chunks from here (lag-free)
                job.chunkIndex++;
                job.column = 0;
                continue;
            }
            while (job.column < 256) {
                int col = job.column++;
                if (carved[col] != SKIPPED) {
                    int lx = col & 15;
                    int lz = col >> 4;
                    int x = (chunkX << 4) + lx;
                    int z = (chunkZ << 4) + lz;
                    double dx = x - job.cx;
                    double dz = z - job.cz;
                    double d2 = dx * dx + dz * dz;
                    if (d2 <= r2) {
                        double bowl = 1.0D - d2 / r2; // 1 in the middle, 0 at the rim
                        int bowlFloor = Math.max(job.bottomY, job.cy - (int) Math.round(job.depth * bowl));
                        int desired = Math.max(bowlFloor, tipY);
                        if (carved[col] > desired) {
                            int from = carved[col] == UNTOUCHED
                                    ? chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz) - 1
                                    : carved[col] - 1;
                            carveColumn(level, chunk, pos, air, x, z, from, desired, minY);
                            carved[col] = desired;
                            job.dirty = true;
                        }
                    }
                }
                if ((col & 15) == 15 && System.nanoTime() >= deadline) {
                    break;
                }
            }
            if (job.column >= 256) {
                job.column = 0;
                job.chunkIndex++;
            }
        }
    }

    private static void carveColumn(ServerLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos, BlockState air,
                                    int x, int z, int from, int to, int minY) {
        LevelChunkSection[] sections = chunk.getSections();
        int y = Math.min(from, level.getMaxBuildHeight() - 1);
        while (y >= to) {
            int index = (y - minY) >> 4;
            if (index < 0 || index >= sections.length) {
                y--;
                continue;
            }
            if (sections[index].hasOnlyAir()) {
                y = (index << 4) + minY - 1; // skip the whole empty section
                continue;
            }
            pos.set(x, y, z);
            BlockState state = chunk.getBlockState(pos);
            if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0.0F) { // never touch bedrock/barriers
                level.setBlock(pos, air, FLAGS, 0);
            }
            y--;
        }
    }

    private static void finish(Job job) {
        synchronized (JOBS) {
            JOBS.remove(job);
        }
    }
}
