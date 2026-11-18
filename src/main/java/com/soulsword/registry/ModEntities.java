package com.soulsword.registry;

import com.soulsword.SoulSwordMod;
import com.soulsword.entity.AnnihilationBeamEntity;
import com.soulsword.entity.RealityRiftEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SoulSwordMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<RealityRiftEntity>> REALITY_RIFT = ENTITIES.register("reality_rift", () ->
            EntityType.Builder.<RealityRiftEntity>of(RealityRiftEntity::new, MobCategory.MISC)
                    .sized(RealityRiftEntity.WIDTH, RealityRiftEntity.HEIGHT)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .fireImmune()
                    .noSummon()
                    .build("reality_rift"));

    public static final DeferredHolder<EntityType<?>, EntityType<AnnihilationBeamEntity>> ANNIHILATION_BEAM = ENTITIES.register("annihilation_beam", () ->
            EntityType.Builder.<AnnihilationBeamEntity>of(AnnihilationBeamEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(32)
                    .updateInterval(20)
                    .fireImmune()
                    .noSummon()
                    .build("annihilation_beam"));

    private ModEntities() {}
}
