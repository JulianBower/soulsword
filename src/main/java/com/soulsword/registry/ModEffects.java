package com.soulsword.registry;

import com.soulsword.SoulSwordMod;
import com.soulsword.effect.SoulSeverEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, SoulSwordMod.MODID);

    public static final DeferredHolder<MobEffect, SoulSeverEffect> SOUL_SEVER = EFFECTS.register("soul_sever", SoulSeverEffect::new);

    private ModEffects() {}
}
