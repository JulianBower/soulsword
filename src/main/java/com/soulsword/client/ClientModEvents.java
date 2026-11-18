package com.soulsword.client;

import com.soulsword.SoulSwordMod;
import com.soulsword.registry.ModEntities;
import com.soulsword.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

@EventBusSubscriber(modid = SoulSwordMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private static SoulSwordRenderer swordRenderer;

    private ClientModEvents() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // the rift's portal pass needs a stencil buffer on the main framebuffer
        event.enqueueWork(() -> Minecraft.getInstance().getMainRenderTarget().enableStencil());
    }

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(ClientKeys.SWITCH_MODE);
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SoulSwordModel.LAYER, SoulSwordModel::createLayer);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.REALITY_RIFT.get(), RealityRiftRenderer::new);
        event.registerEntityRenderer(ModEntities.ANNIHILATION_BEAM.get(), AnnihilationBeamRenderer::new);
    }

    @SubscribeEvent
    public static void onClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (swordRenderer == null) {
                    swordRenderer = new SoulSwordRenderer();
                }
                return swordRenderer;
            }
        }, ModItems.SOUL_SWORD.get());
    }
}
