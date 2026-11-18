package com.soulsword.entity;

import com.soulsword.ability.RiftTravel;
import com.soulsword.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.UUID;

/** A jagged tear in space that links to another dimension. Facing is the entity's yaw. */
public class RealityRiftEntity extends Entity {
    public static final float WIDTH = 2.4F;
    public static final float HEIGHT = 3.4F;
    public static final int MAX_AGE = 400;
    public static final int OPEN_TICKS = 8;
    public static final int CLOSE_TICKS = 24;
    private static final String COOLDOWN_KEY = "soul_sword_rift_cd";

    private static final EntityDataAccessor<Integer> DIM = SynchedEntityData.defineId(RealityRiftEntity.class, EntityDataSerializers.INT);

    private UUID ownerId;

    public RealityRiftEntity(EntityType<? extends RealityRiftEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static RealityRiftEntity create(Level level, double x, double bottomY, double z, float yaw, int dim, UUID owner) {
        RealityRiftEntity rift = new RealityRiftEntity(ModEntities.REALITY_RIFT.get(), level);
        rift.setPos(x, bottomY, z);
        rift.setYRot(yaw);
        rift.yRotO = yaw;
        rift.entityData.set(DIM, dim);
        rift.ownerId = owner;
        return rift;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public int getDim() {
        return this.entityData.get(DIM);
    }

    /** Half-width (in blocks) of the lens-shaped tear at normalised height u in [-1, 1]. */
    public static double halfWidthAt(double u) {
        double c = Math.cos(Math.max(-1.0D, Math.min(1.0D, u)) * Math.PI / 2.0D);
        return WIDTH / 2.0D * Math.pow(Math.max(0.0D, c), 0.8D);
    }

    /** Unit vector pointing out of the rift plane (the cutter's look direction). */
    public Vec3 forward() {
        double yaw = Math.toRadians(getYRot());
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    /** Unit vector along the rift's width. */
    public Vec3 lateral() {
        double yaw = Math.toRadians(getYRot());
        return new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DIM, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (this.tickCount > MAX_AGE) {
            this.discard();
            return;
        }
        if (this.tickCount >= OPEN_TICKS && this.tickCount < MAX_AGE - CLOSE_TICKS + 8) {
            ServerLevel level = (ServerLevel) this.level();
            Vec3 center = position().add(0, HEIGHT / 2.0D, 0);
            Vec3 fwd = forward();
            Vec3 lat = lateral();
            for (Entity e : level.getEntities((Entity) null, getBoundingBox().inflate(1.2D, 0.4D, 1.2D), this::canTravel)) {
                Vec3 rel = e.position().add(0, e.getBbHeight() * 0.5D, 0).subtract(center);
                double u = rel.y / (HEIGHT / 2.0D);
                double along = rel.dot(lat);
                double depth = rel.dot(fwd);
                double pad = e.getBbWidth() * 0.5D;
                if (Math.abs(depth) < 0.55D + pad && Math.abs(u) <= 1.1D && Math.abs(along) < halfWidthAt(u) + pad) {
                    e.getPersistentData().putLong(COOLDOWN_KEY, level.getGameTime() + 60L);
                    RiftTravel.send(e, level, getDim(), fwd, getYRot());
                }
            }
        }
    }

    private boolean canTravel(Entity e) {
        if (e == this || e.isRemoved() || e.isPassenger() || e.isVehicle()) return false;
        if (!(e instanceof LivingEntity) && !(e instanceof ItemEntity)) return false;
        if (e.getType().is(Tags.EntityTypes.BOSSES)) return false;
        return e.getPersistentData().getLong(COOLDOWN_KEY) < e.level().getGameTime();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0D * 256.0D;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
