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
import net.minecraft.util.Mth;
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
 * An orbital laser. The entity sits at the impact point; the beam itself is drawn from the build-height ceiling
 * (Y = 320 in the Overworld) and its tip streaks down at high speed for {@link #impactTick(int)} ticks.
 * <ul>
 *   <li>Variant 0 (Soul Rupture): tip lands, then a clean explosion + shockwave hits the struck mob.</li>
 *   <li>Variant 1 (Annihilation): tip lands with a sonic boom, then keeps boring towards bedrock while a luminous dome
 *       travels down with it and {@link CraterManager} carves the bowl layer by layer behind it.</li>
 * </ul>
 */
public class AnnihilationBeamEntity extends Entity {
    public static final int VARIANT_RUPTURE = 0;
    public static final int VARIANT_ANNIHILATION = 1;
    public static final int RUPTURE_IMPACT_TICK = 5;
    public static final int ANNIHILATION_IMPACT_TICK = 14;
    public static final int RUPTURE_LIFE = 17;
    /** Ticks the beam lingers after the drill reaches the bottom. */
    public static final int LINGER_TICKS = 40;

    private static final EntityDataAccessor<Float> BEAM_RADIUS = SynchedEntityData.defineId(AnnihilationBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(AnnihilationBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CRATER_RADIUS = SynchedEntityData.defineId(AnnihilationBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DEPTH = SynchedEntityData.defineId(AnnihilationBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DRILL = SynchedEntityData.defineId(AnnihilationBeamEntity.class, EntityDataSerializers.INT);

    private UUID ownerId;
    private UUID targetId;
    private long souls;

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

    /** Annihilation: crater radius, centre depth (blocks below impact) and ticks the tip needs to reach the floor. */
    public void configureCrater(long souls, int radius, int depth, int drillTicks) {
        this.souls = souls;
        this.entityData.set(CRATER_RADIUS, radius);
        this.entityData.set(DEPTH, depth);
        this.entityData.set(DRILL, drillTicks);
    }

    /** Soul Rupture: souls at the moment of the hit and the mob that was struck. */
    public void configureRupture(long souls, UUID target) {
        this.souls = souls;
        this.targetId = target;
    }

    public int getVariant() {
        return this.entityData.get(VARIANT);
    }

    public float getBeamRadius() {
        return this.entityData.get(BEAM_RADIUS);
    }

    public int getCraterRadius() {
        return this.entityData.get(CRATER_RADIUS);
    }

    public int getDepth() {
        return this.entityData.get(DEPTH);
    }

    public int getDrillTicks() {
        return Math.max(1, this.entityData.get(DRILL));
    }

    public static int impactTick(int variant) {
        return variant == VARIANT_RUPTURE ? RUPTURE_IMPACT_TICK : ANNIHILATION_IMPACT_TICK;
    }

    public int getImpactTick() {
        return impactTick(getVariant());
    }

    public int getLifetime() {
        return getVariant() == VARIANT_RUPTURE ? RUPTURE_LIFE : getImpactTick() + getDrillTicks() + LINGER_TICKS;
    }

    /** 0..1 progress of the drill after impact, from an age in ticks (shared by server and client). */
    public static float drillProgress(float age, int impactTick, int drillTicks) {
        return Mth.clamp((age - impactTick) / (float) drillTicks, 0.0F, 1.0F);
    }

    /** Radius of the crater bowl (and therefore of the dome) at drill progress p. */
    public static float domeRadius(int craterRadius, float p) {
        return Math.max(3.0F, craterRadius * Mth.sqrt(1.0F - p));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BEAM_RADIUS, 1.0F);
        builder.define(VARIANT, VARIANT_RUPTURE);
        builder.define(CRATER_RADIUS, 0);
        builder.define(DEPTH, 0);
        builder.define(DRILL, 1);
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
        int impact = getImpactTick();
        if (getVariant() == VARIANT_RUPTURE) {
            if (this.tickCount == impact) {
                ruptureImpact(level);
            }
            return;
        }
        if (this.tickCount == impact) {
            annihilationImpact(level);
        } else if (this.tickCount > impact && this.tickCount <= impact + getDrillTicks() && this.tickCount % 2 == 0) {
            blast(level, this.tickCount == impact + getDrillTicks() ? 2 : 1);
        }
    }

    // ------------------------------------------------------------------ Soul Rupture

    private void ruptureImpact(ServerLevel level) {
        Vec3 p = position();
        double radius = SoulMath.ruptureRadius(souls);
        Entity owner = ownerId == null ? null : level.getEntity(ownerId);
        Entity target = targetId == null ? null : level.getEntity(targetId);
        DamageSource source = level.damageSources().indirectMagic(this, owner);
        float baseDamage = SoulMath.ruptureDamage(souls);

        if (target instanceof LivingEntity struck && struck.isAlive()) {
            struck.hurt(source, baseDamage * 1.5F);
        }
        AABB box = new AABB(p, p).inflate(radius, radius * 0.75D, radius);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != owner && e != target && e.isAlive() && !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator())));
        for (LivingEntity e : nearby) {
            double dx = e.getX() - p.x;
            double dz = e.getZ() - p.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > radius) continue;
            double falloff = 1.0D - d / radius;
            e.hurt(source, (float) (baseDamage * (0.4D + 0.6D * falloff)));
            e.knockback(0.4D + 1.4D * falloff, p.x - e.getX(), p.z - e.getZ());
        }

        level.playSound(null, p.x, p.y, p.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.5F, 1.1F);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F + (float) radius * 0.2F, 0.9F);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5F, 0.6F);
        level.sendParticles(ParticleTypes.FLASH, p.x, p.y + 1.0D, p.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y + 0.5D, p.z, 1, 0, 0, 0, 0);
        for (int ring = 0; ring < 2; ring++) {
            for (int i = 0; i < 40; i++) {
                double a = Math.PI * 2.0D * i / 40.0D;
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y + 0.3D, p.z, 0,
                        Math.cos(a), 0.04D, Math.sin(a), 0.5D + ring * 0.4D + radius * 0.05D);
            }
        }
        for (int i = 0; i < 24; i++) {
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + i * 1.2D, p.z, 2, 0.15D, 0.1D, 0.15D, 0.02D);
        }
    }

    // ------------------------------------------------------------------ Annihilation

    private void annihilationImpact(ServerLevel level) {
        double x = getX(), y = getY(), z = getZ();
        level.playSound(null, x, y, z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 8.0F, 0.7F);
        level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 12.0F, 0.6F);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 12.0F, 0.5F);
        level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 12.0F, 0.6F);
        level.sendParticles(ParticleTypes.FLASH, x, y + 2, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 1, z, 1, 0, 0, 0, 0);
        for (int r = 0; r < 3; r++) {
            for (int i = 0; i < 48; i++) {
                double a = Math.PI * 2 * i / 48.0;
                level.sendParticles(ParticleTypes.END_ROD, x, y + 1 + r, z, 0, Math.cos(a), 0.02, Math.sin(a), 0.6 + r * 0.5);
            }
        }
        CraterManager.queue(level, BlockPos.containing(x, y, z), getCraterRadius(), getDepth(), getDrillTicks());
        blast(level, 0);
    }

    private boolean eligible(Entity e) {
        if (e == this || e instanceof AnnihilationBeamEntity || e instanceof RealityRiftEntity) return false;
        if (ownerId != null && ownerId.equals(e.getUUID())) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return e.isAlive();
    }

    /**
     * mode 0 = impact (damage ring + sever for distant survivors), 1 = while drilling (vaporise everything inside the
     * travelling dome), 2 = last drilling pass (vaporise + sever survivors again).
     */
    private void blast(ServerLevel level, int mode) {
        double radius = getCraterRadius();
        int impact = getImpactTick();
        float p = drillProgress(this.tickCount, impact, getDrillTicks());
        double tipY = getY() - getDepth() * p;
        double domeR = domeRadius(getCraterRadius(), p);
        Vec3 c = position();
        Entity owner = ownerId == null ? null : level.getEntity(ownerId);
        DamageSource kill = level.damageSources().genericKill();
        DamageSource dmg = level.damageSources().indirectMagic(this, owner);
        AABB box = new AABB(c.x - radius * 3, level.getMinBuildHeight(), c.z - radius * 3, c.x + radius * 3, level.getMaxBuildHeight(), c.z + radius * 3);
        List<Entity> victims = level.getEntities((Entity) null, box, this::eligible);
        for (Entity e : victims) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            boolean aboveTip = e.getY() >= tipY - 4.0D;
            if (mode == 0 && d <= radius * 0.6 && aboveTip) {
                vaporize(e, kill);
                continue;
            }
            if (mode != 0 && d <= domeR + 2.0D && aboveTip) {
                vaporize(e, kill);
                continue;
            }
            if (mode == 0 && d <= radius) {
                if (e instanceof LivingEntity le) {
                    le.hurt(dmg, SoulMath.annihilationDamage(souls));
                } else {
                    e.discard();
                    continue;
                }
            }
            if (mode != 1 && d <= radius * 3 && e instanceof LivingEntity le && le.isAlive()) {
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
