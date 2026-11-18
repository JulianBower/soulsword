package com.soulsword.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** Teleport logic for entities that walk into a Reality Rift. */
public final class RiftTravel {
    private RiftTravel() {}

    public static ResourceKey<Level> dimKey(int dim) {
        return switch (Math.floorMod(dim, 3)) {
            case 1 -> Level.NETHER;
            case 2 -> Level.END;
            default -> Level.OVERWORLD;
        };
    }

    /**
     * Sends an entity through a rift. If the rift already points at the current dimension
     * the entity is folded 64 blocks forward through space instead.
     */
    public static void send(Entity entity, ServerLevel source, int dim, Vec3 forward, float yaw) {
        MinecraftServer server = source.getServer();
        ServerLevel dest = server.getLevel(dimKey(dim));
        if (dest == null) return;

        Vec3 target;
        if (dest == source) {
            Vec3 t = entity.position().add(forward.scale(64.0D));
            target = surface(dest, t.x, t.z, entity.getY());
        } else if (dest.dimension() == Level.END) {
            buildEndPlatform(dest);
            target = new Vec3(100.5D, 49.0D, 0.5D);
        } else if (dest.dimension() == Level.NETHER) {
            double scale = source.dimension() == Level.OVERWORLD ? 1.0D / 8.0D : 1.0D;
            target = netherSpot(dest, entity.getX() * scale, entity.getZ() * scale);
        } else {
            double scale = source.dimension() == Level.NETHER ? 8.0D : 1.0D;
            target = surface(dest, entity.getX() * scale, entity.getZ() * scale, 80.0D);
        }

        BlockPos clamped = dest.getWorldBorder().clampToBounds(target.x, target.y, target.z);
        target = new Vec3(clamped.getX() + 0.5D, target.y, clamped.getZ() + 0.5D);
        entity.resetFallDistance();
        entity.teleportTo(dest, target.x, target.y, target.z, Set.of(), yaw, entity.getXRot());
    }

    /** Top of the terrain at x/z, with a stone patch if it would land the entity in a liquid or the void. */
    private static Vec3 surface(ServerLevel level, double x, double z, double fallbackY) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        level.getChunk(bx >> 4, bz >> 4); // make sure it exists
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        if (y <= level.getMinBuildHeight()) {
            y = (int) fallbackY;
            platform(level, bx, y - 1, bz, Blocks.COBBLESTONE.defaultBlockState());
        } else if (!level.getFluidState(new BlockPos(bx, y - 1, bz)).isEmpty()) {
            platform(level, bx, y, bz, Blocks.COBBLESTONE.defaultBlockState());
            y += 1;
        }
        return new Vec3(x, y, z);
    }

    private static Vec3 netherSpot(ServerLevel level, double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        level.getChunk(bx >> 4, bz >> 4);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = 110; y > 33; y--) {
            p.set(bx, y, bz);
            BlockState feet = level.getBlockState(p);
            BlockState head = level.getBlockState(p.above());
            BlockPos below = p.below();
            BlockState floor = level.getBlockState(below);
            if (feet.isAir() && head.isAir() && floor.isFaceSturdy(level, below, Direction.UP)
                    && level.getFluidState(below).getType() != Fluids.LAVA) {
                return new Vec3(x, y, z);
            }
        }
        int y = 70;
        platform(level, bx, y - 1, bz, Blocks.NETHERRACK.defaultBlockState());
        return new Vec3(x, y, z);
    }

    private static void buildEndPlatform(ServerLevel end) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                end.setBlockAndUpdate(new BlockPos(100 + dx, 48, dz), Blocks.OBSIDIAN.defaultBlockState());
                for (int dy = 1; dy <= 3; dy++) {
                    end.setBlockAndUpdate(new BlockPos(100 + dx, 48 + dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** A 3x3 platform with three blocks of headroom. */
    private static void platform(ServerLevel level, int x, int y, int z, BlockState state) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlockAndUpdate(new BlockPos(x + dx, y, z + dz), state);
                for (int dy = 1; dy <= 3; dy++) {
                    level.setBlockAndUpdate(new BlockPos(x + dx, y + dy, z + dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }
}
