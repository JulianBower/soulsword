package com.soulsword.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Soul Sever: purely a healing blocker. Health regeneration is cancelled in
 * {@code SoulSwordEvents#onHeal}; the effect itself never deals damage.
 */
public class SoulSeverEffect extends MobEffect {
    public SoulSeverEffect() {
        super(MobEffectCategory.HARMFUL, 0xE05CFF);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        return true;
    }
}
