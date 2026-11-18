package com.soulsword.entity;

import com.soulsword.crater.CraterManager;
import com.soulsword.item.SoulMath;
import com.soulsword.registry.ModEffects;
import com.soulsword.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * A vertical column of light. Variant 0 is the short Soul Rupture strike (sky to the struck mob),
 * variant 1 is the Annihilation beam (sky to bedrock) that also drives the crater and entity vaporisation.
 */
public class AnnihilationBeamEntity extends Entity {
    public static final int VARIANT_RUPTURE = 0;
    public static final int VARIANT_ANNIHILATION = 1;
    public static final int DETONATE_TICK = 12;
    public static final int ANNIHILATION_LIFE = 130;
    public static final int RUPTURE_LIFE = 16;

    private static final EntityDataAccessor<Float> BEAM_RADIUS = SynchedEntityData.defineId(AnnihilationBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(AnnihilationBeamEntity.class, EntityDataSerializers.INT);

    private UUID ownerId;
    private long souls;
    private int craterRadius;
    private int craterDepth;

    public AnnihilationBeamEntity(EntityType<? extends AnnihilationBeamEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static AnnihilationBeamEntity create(Level level, double x, double y, double z, int variant, float beamRadius, UUID owner) {
        AnnihilationBeamEntity beam = new AnnihilationBeamEntity(ModEntities.ANNIHILATION_BEAM.get(), level);
        beam.setPos(x, y, z);
        beam.entityData.set(VARIANT, variant);
        beam.entityData.set(BEAM_RADIUS, beamRadius);
        beam.ownerId = owner;
        return beam;
    }

    public void configureCrater(long souls, int radius, int depth) {
        this.souls = souls;
        this.craterRadius = radius;
        this.craterDepth = depth;
    }

    public int getVariant() {
        return this.entityData.get(VARIANT);
    }

    public float getBeamRadius() {
        return this.entityData.get(BEAM_RADIUS);
    }

    public int getLifetime() {
        return getVariant() == VARIANT_RUPTURE ? RUPTURE_LIFE : ANNIHILATION_LIFE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BEAM_RADIUS, 1.0F);
        builder.define(VARIANT, VARIANT_RUPTURE);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        if (this.tickCount >= getLifetime()) {
            this.discard();
            return;
        }
        if (getVariant() != VARIANT_ANNIHILATION) {
            return;
        }
        if (this.tickCount == DETONATE_TICK) {
            detonate(level);
        }
        if (this.tickCount >= DETONATE_TICK && this.tickCount < DETONATE_TICK + 60 && (this.tickCount - DETONATE_TICK) % 4 == 0) {
            blast(level, this.tickCount == DETONATE_TICK);
        }
    }

    private void detonate(ServerLevel level) {
        double x = getX(), y = getY(), z = getZ();
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 12.0F, 0.5F);
        level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 12.0F, 0.6F);
        level.sendParticles(ParticleTypes.FLASH, x, y + 2, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 1, z, 1, 0, 0, 0, 0);
        int rings = 3;
        for (int r = 0; r < rings; r++) {
            for (int i = 0; i < 48; i++) {
                double a = Math.PI * 2 * i / 48.0;
                level.sendParticles(ParticleTypes.END_ROD, x, y + 1 + r, z, 0, Math.cos(a), 0.02, Math.sin(a), 0.6 + r * 0.5);
            }
        }
        CraterManager.queue(level, BlockPos.containing(x, y, z), craterRadius, craterDepth);
    }

    private boolean eligible(Entity e) {
        if (e == this || e instanceof AnnihilationBeamEntity || e instanceof RealityRiftEntity) return false;
        if (ownerId != null && ownerId.equals(e.getUUID())) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return e.isAlive();
    }

    private void blast(ServerLevel level, boolean first) {
        double radius = craterRadius;
        Vec3 c = position();
        Entity owner = ownerId == null ? null : level.getEntity(ownerId);
        DamageSource kill = level.damageSources().genericKill();
        DamageSource dmg = level.damageSources().indirectMagic(this, owner);
        AABB box = new AABB(c.x - radius * 3, level.getMinBuildHeight(), c.z - radius * 3, c.x + radius * 3, level.getMaxBuildHeight(), c.z + radius * 3);
        List<Entity> victims = level.getEntities((Entity) null, box, this::eligible);
        for (Entity e : victims) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= radius * 0.6) {
                vaporize(e, kill);
                continue;
            }
            if (first && d <= radius) {
                if (e instanceof LivingEntity le) {
                    le.hurt(dmg, SoulMath.annihilationDamage(souls));
                } else {
                    e.discard();
                    continue;
                }
            }
            if (d <= radius * 3 && e instanceof LivingEntity le && le.isAlive()) {
                le.addEffect(new MobEffectInstance(ModEffects.SOUL_SEVER, 600, 0));
            }
        }
    }

    private static void vaporize(Entity e, DamageSource kill) {
        if (e instanceof LivingEntity le) {
            le.hurt(kill, Float.MAX_VALUE);
            if (le.isAlive() && !le.isRemoved()) {
                le.setHealth(0.0F);
                le.die(kill);
            }
        } else {
            e.discard();
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
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
