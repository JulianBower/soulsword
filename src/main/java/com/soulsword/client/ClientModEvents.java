package com.soulsword.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.soulsword.SoulSwordMod;
import com.soulsword.item.SoulSwordItem;
import com.soulsword.registry.ModEntities;
import com.soulsword.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
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

            /** First person: while right-click is held the blade is raised high and trembles with rising charge. */
            @Override
            public boolean applyForgeHandTransform(PoseStack ps, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                                   float partialTick, float equipProcess, float swingProcess) {
                if (!SoulSwordItem.isActive(player)) {
                    return false;
                }
                int side = arm == HumanoidArm.RIGHT ? 1 : -1;
                float t = player.getTicksUsingItem() + partialTick;
                float charge = SoulSwordItem.getMode(stack) == SoulSwordItem.MODE_ANNIHILATION
                        ? Mth.clamp(t / 6.0F, 0.0F, 1.0F)
                        : SoulSwordItem.chargeProgress(player, partialTick);
                ps.translate(side * 0.56F, -0.52F + equipProcess * -0.6F, -0.72F);
                ps.translate(side * -0.12F * charge, 0.42F * charge + 0.02F * Mth.sin(t * 0.5F), 0.12F * charge);
                ps.mulPose(Axis.XP.rotationDegrees(-12.0F - 38.0F * charge));
                ps.mulPose(Axis.ZP.rotationDegrees(side * -8.0F * charge));
                ps.mulPose(Axis.YP.rotationDegrees(side * 8.0F * charge));
                float shake = 0.004F + 0.012F * charge;
                ps.translate(Mth.sin(t * 47.0F) * shake, Mth.cos(t * 53.0F) * shake, 0.0F);
                // a strike while charged punches the blade forward
                ps.mulPose(Axis.XP.rotationDegrees(-swingProcess * 35.0F));
                ps.translate(0.0F, 0.0F, -swingProcess * 0.25F);
                return true;
            }
        }, ModItems.SOUL_SWORD.get());
    }
}
