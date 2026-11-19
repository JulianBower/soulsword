package com.soulsword.item;

/** All soul-dependent scaling formulas in one place. Everything saturates at {@link SoulSwordItem#MAX_SOULS}. */
public final class SoulMath {
    public static final double MAX_BONUS_DAMAGE = 35.0D;

    private SoulMath() {}

    public static long clamp(long souls) {
        return Math.max(0L, Math.min(SoulSwordItem.MAX_SOULS, souls));
    }

    /** Linear: 0 souls = +0, 100,000 souls = +35.0 bonus attack damage. */
    public static double bonusDamage(long souls) {
        return (clamp(souls) / (double) SoulSwordItem.MAX_SOULS) * MAX_BONUS_DAMAGE;
    }

    /** 0..1 progress towards the soul cap. */
    public static double t(long souls) {
        return clamp(souls) / (double) SoulSwordItem.MAX_SOULS;
    }

    /** Radius of the Soul Rupture explosion and shockwave (3 .. 15 blocks). */
    public static double ruptureRadius(long souls) {
        return 3.0D + 12.0D * Math.sqrt(t(souls));
    }

    /** Shockwave damage to bystanders (6 .. 56). */
    public static float ruptureDamage(long souls) {
        return (float) (6.0D + 50.0D * t(souls));
    }

    /** Annihilation crater radius: ~15 blocks at 100 souls up to 120 at 100,000 souls. */
    public static int craterRadius(long souls) {
        return (int) Math.round(12.0D + 108.0D * Math.sqrt(t(souls)));
    }

    /**
     * Depth of the crater centre in blocks below the impact point. {@code fullDepth} is the distance
     * down to the bedrock floor, which is reached at 100,000 souls.
     */
    public static int craterDepth(long souls, int fullDepth) {
        if (clamp(souls) >= SoulSwordItem.MAX_SOULS) {
            return fullDepth;
        }
        int minDepth = Math.min(12, fullDepth);
        return (int) Math.round(minDepth + (fullDepth - minDepth) * Math.sqrt(t(souls)));
    }

    /** Ticks the beam tip takes to bore from the surface to the crater floor (25 .. 40). */
    public static int drillTicks(long souls) {
        return 25 + (int) Math.round(15.0D * t(souls));
    }

    /** Damage for entities between 60% and 100% of the crater radius (60 .. 200). */
    public static float annihilationDamage(long souls) {
        return (float) (60.0D + 140.0D * t(souls));
    }

    /** Radius of the visible beam column. */
    public static float beamVisualRadius(int craterRadius) {
        return 1.5F + craterRadius * 0.3F;
    }
}
