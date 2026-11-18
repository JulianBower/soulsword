package com.soulsword.item;

/** All soul-dependent scaling formulas in one place. */
public final class SoulMath {
    public static final long ONE_MILLION = 1_000_000L;

    private SoulMath() {}

    /** 1,000,000 souls = +25.0 damage, no cap. */
    public static double bonusDamage(long souls) {
        return souls / 40000.0D;
    }

    /** 0..1 progress towards one million souls (clamped). */
    public static double t(long souls) {
        return Math.min(1.0D, Math.max(0.0D, souls / (double) ONE_MILLION));
    }

    /** Visual/AoE radius of the Soul Rupture explosion and shockwave. */
    public static double ruptureRadius(long souls) {
        return 3.0D + 9.0D * Math.sqrt(t(souls)) + Math.min(6.0D, Math.log10(Math.max(1.0D, souls / 1000.0D)) * 1.2D);
    }

    /** Damage dealt by the Soul Rupture shockwave to bystanders. */
    public static float ruptureDamage(long souls) {
        return (float) (6.0D + souls / 10000.0D);
    }

    /**
     * Annihilation crater radius: ~14 blocks at 100 souls, 180 at one million,
     * then slowly growing (hard-capped at 256 for server safety).
     */
    public static int craterRadius(long souls) {
        if (souls <= ONE_MILLION) {
            return (int) Math.round(12.0D + 168.0D * Math.sqrt(t(souls)));
        }
        double extra = 30.0D * Math.log10(souls / (double) ONE_MILLION);
        return (int) Math.round(Math.min(256.0D, 180.0D + extra));
    }

    /**
     * Depth of the crater centre, in blocks below the impact point.
     * {@code fullDepth} = distance down to the bedrock floor; reached at 1,000,000 souls.
     */
    public static int craterDepth(long souls, int fullDepth) {
        if (souls >= ONE_MILLION) {
            return fullDepth;
        }
        int minDepth = Math.min(12, fullDepth);
        return (int) Math.round(minDepth + (fullDepth - minDepth) * Math.sqrt(t(souls)));
    }

    /** Damage for entities between 60% and 100% of the crater radius. */
    public static float annihilationDamage(long souls) {
        return (float) (60.0D + souls / 5000.0D);
    }

    /** Radius of the visible beam column. */
    public static float beamVisualRadius(int craterRadius) {
        return 1.5F + craterRadius * 0.45F;
    }
}
