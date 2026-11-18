package com.soulsword.ability;

import com.soulsword.entity.AnnihilationBeamEntity;
import com.soulsword.entity.RealityRiftEntity;
import com.soulsword.item.SoulMath;
import com.soulsword.item.SoulSwordItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/** Server-side implementations of the three blade modes. */
public final class SoulSwordAbilities {
    private SoulSwordAbilities() {}

    public static String dimNameKey(int dim) {
        return switch (Math.floorMod(dim, 3)) {
            case 1 -> "dim.soul_sword.nether";
            case 2 -> "dim.soul_sword.end";
            default -> "dim.soul_sword.overworld";
        };
    }

    // ------------------------------------------------------------------ Mode 1: Soul Rupture

    /** Calls a vertical soul beam down onto the struck mob, then detonates a clean shockwave. */
    public static void soulRupture(ServerLevel level, ServerPlayer owner, LivingEntity target, long souls) {
        Vec3 p = target.position();
        double radius = SoulMath.ruptureRadius(souls);
        float beamRadius = 0.6F + (float) radius * 0.12F;

        AnnihilationBeamEntity beam = AnnihilationBeamEntity.create(level, p.x, p.y, p.z,
                AnnihilationBeamEntity.VARIANT_RUPTURE, beamRadius, owner.getUUID());
        level.addFreshEntity(beam);

        DamageSource source = level.damageSources().indirectMagic(beam, owner);
        float baseDamage = SoulMath.ruptureDamage(souls);
        target.hurt(source, baseDamage * 1.5F);

        AABB box = new AABB(p, p).inflate(radius, radius * 0.75D, radius);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != owner && e != target && e.isAlive());
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

    // ------------------------------------------------------------------ Mode 2: Reality Cut

    /** Slices open a rift in mid-air in front of the wielder. */
    public static void sliceRift(ServerLevel level, ServerPlayer owner, LivingEntity target, int dim) {
        Vec3 eye = owner.getEyePosition();
        Vec3 flat = new Vec3(owner.getLookAngle().x, 0, owner.getLookAngle().z);
        flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : flat.normalize();
        double distance = Math.max(1.2D, Math.min(2.2D, owner.distanceTo(target) * 0.6D));
        double x = eye.x + flat.x * distance;
        double z = eye.z + flat.z * distance;
        double y = owner.getY() - 0.1D;

        List<RealityRiftEntity> old = level.getEntitiesOfClass(RealityRiftEntity.class,
                new AABB(owner.blockPosition()).inflate(48.0D), r -> owner.getUUID().equals(r.getOwnerId()));
        for (RealityRiftEntity r : old) {
            r.discard();
        }

        RealityRiftEntity rift = RealityRiftEntity.create(level, x, y, z, owner.getYRot(), dim, owner.getUUID());
        level.addFreshEntity(rift);

        level.playSound(null, x, y, z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.7F, 1.6F);
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.5F, 0.5F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + RealityRiftEntity.HEIGHT / 2.0D, z, 40, 0.5D, 1.0D, 0.5D, 0.15D);
        level.sendParticles(ParticleTypes.END_ROD, x, y + RealityRiftEntity.HEIGHT / 2.0D, z, 16, 0.3D, 1.2D, 0.3D, 0.05D);
    }

    // ------------------------------------------------------------------ Mode 3: Annihilation

    /** Consumes every stored soul and drops the beam. The crater itself is carved by the beam entity. */
    public static void castAnnihilation(ServerPlayer player, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        long souls = SoulSwordItem.getSouls(stack);
        SoulSwordItem.setSouls(stack, 0L);

        int cx;
        int cz;
        int cy;
        HitResult hit = player.pick(64.0D, 1.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos bp = ((BlockHitResult) hit).getBlockPos();
            cx = bp.getX();
            cy = bp.getY();
            cz = bp.getZ();
        } else {
            Vec3 t = player.getEyePosition().add(player.getLookAngle().scale(24.0D));
            cx = (int) Math.floor(t.x);
            cz = (int) Math.floor(t.z);
            level.getChunk(cx >> 4, cz >> 4);
            cy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz) - 1;
            if (cy <= level.getMinBuildHeight() + 8) {
                cy = player.getBlockY();
            }
        }

        int radius = SoulMath.craterRadius(souls);
        int fullDepth = Math.max(1, cy - (level.getMinBuildHeight() + 5));
        int depth = SoulMath.craterDepth(souls, fullDepth);

        AnnihilationBeamEntity beam = AnnihilationBeamEntity.create(level, cx + 0.5D, cy, cz + 0.5D,
                AnnihilationBeamEntity.VARIANT_ANNIHILATION, SoulMath.beamVisualRadius(radius), player.getUUID());
        beam.configureCrater(souls, radius, depth);
        level.addFreshEntity(beam);

        // The caster is never harmed by their own blast, and floats down if the ground is taken from under them.
        double dx = player.getX() - (cx + 0.5D);
        double dz = player.getZ() - (cz + 0.5D);
        if (Math.sqrt(dx * dx + dz * dz) < radius + 6.0D) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 4, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 1800, 0, false, false));
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4.0F, 0.8F);
        level.playSound(null, cx + 0.5D, cy, cz + 0.5D, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 8.0F, 0.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3.0F, 0.4F);
        player.displayClientMessage(Component.translatable("mode.soul_sword.2").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), true);
    }
}
