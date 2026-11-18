package com.soulsword;

import com.soulsword.network.SwitchSwordModePayload;
import com.soulsword.registry.ModComponents;
import com.soulsword.registry.ModEffects;
import com.soulsword.registry.ModEntities;
import com.soulsword.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(SoulSwordMod.MODID)
public class SoulSwordMod {
    public static final String MODID = "soul_sword";

    public SoulSwordMod(IEventBus modBus) {
        ModComponents.COMPONENTS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        modBus.addListener(this::registerPayloads);
        modBus.addListener(this::addCreative);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SwitchSwordModePayload.TYPE, SwitchSwordModePayload.CODEC, SwitchSwordModePayload::handle);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.SOUL_SWORD.get());
        }
    }
}
