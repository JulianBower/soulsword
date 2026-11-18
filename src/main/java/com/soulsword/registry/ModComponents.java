package com.soulsword.registry;

import com.mojang.serialization.Codec;
import com.soulsword.SoulSwordMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(SoulSwordMod.MODID);

    /** Total absorbed souls. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> SOULS =
            COMPONENTS.registerComponentType("souls", b -> b.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));

    /** 0 = Soul Rupture, 1 = Reality Cut, 2 = Annihilation. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MODE =
            COMPONENTS.registerComponentType("mode", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Target dimension of newly cut rifts: 0 = Overworld, 1 = Nether, 2 = End. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RIFT_DIM =
            COMPONENTS.registerComponentType("rift_dim", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModComponents() {}
}
