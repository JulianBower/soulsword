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

    /** Calls a vertical soul beam down from the sky onto the struck mob; the explosion happens when its tip lands. */
    public static void soulRupture(ServerLevel level, ServerPlayer owner, LivingEntity target, long souls) {
        Vec3 p = target.position();
        double radius = SoulMath.ruptureRadius(souls);
        float beamRadius = 0.6F + (float) radius * 0.12F;

        AnnihilationBeamEntity beam = AnnihilationBeamEntity.create(level, p.x, p.y, p.z,
                AnnihilationBeamEntity.VARIANT_RUPTURE, beamRadius, owner.getUUID());
        beam.configureRupture(souls, target.getUUID());
        level.addFreshEntity(beam);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 2.0F, 1.6F);
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
        beam.configureCrater(souls, radius, depth, SoulMath.drillTicks(souls));
        level.addFreshEntity(beam);

        // The caster is never harmed by their own blast, and floats down if the ground is taken from under them.
        double dx = player.getX() - (cx + 0.5D);
        double dz = player.getZ() - (cz + 0.5D);
        if (Math.sqrt(dx * dx + dz * dz) < radius + 6.0D) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 4, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 1800, 0, false, false));
        }

        // the sonic boom / dragon growl / explosion play when the beam tip actually lands (AnnihilationBeamEntity)
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 3.0F, 0.8F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 0.5F);
        player.displayClientMessage(Component.translatable("mode.soul_sword.2").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), true);
    }
}
