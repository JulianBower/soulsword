package com.soulsword.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.soulsword.SoulSwordMod;
import com.soulsword.item.SoulMath;
import com.soulsword.item.SoulSwordItem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws the 3D greatsword. The veins glow brighter and more prismatic the more souls it holds. */
public class SoulSwordRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation TEXTURE = SoulSwordMod.id("textures/item/soul_sword.png");
    private static final ResourceLocation GLOW = SoulSwordMod.id("textures/item/soul_sword_glow.png");

    private SoulSwordModel model;

    public SoulSwordRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    private static int rgb(float r, float g, float b, float a) {
        return (Mth.clamp((int) (a * 255.0F), 0, 255) << 24)
                | (Mth.clamp((int) (r * 255.0F), 0, 255) << 16)
                | (Mth.clamp((int) (g * 255.0F), 0, 255) << 8)
                | Mth.clamp((int) (b * 255.0F), 0, 255);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (this.model == null) {
            this.model = new SoulSwordModel(mc.getEntityModels().bakeLayer(SoulSwordModel.LAYER));
        }

        long souls = SoulSwordItem.getSouls(stack);
        int mode = SoulSwordItem.getMode(stack);
        Player player = mc.player;
        boolean held = player != null && player.getMainHandItem() == stack;
        boolean empowered = held && SoulSwordItem.isEmpowered(player, stack);

        double time = (Util.getMillis() % 1_000_000L) / 1000.0D;
        float prism = (float) SoulMath.t(souls);
        float base = 0.20F + 0.60F * (float) Math.min(1.0D, Math.log10(souls + 1.0D) / 6.0D);
        float pulse = empowered ? 0.35F + 0.15F * (float) Math.sin(time * 12.0D) : 0.0F;
        float intensity = Mth.clamp(base + pulse + 0.05F * (float) Math.sin(time * 4.0D), 0.0F, 1.0F);

        // low souls: cold abyssal violet-blue; many souls: full rainbow shimmer
        float hue = (float) ((time * 0.12D) % 1.0D);
        int prismatic = Mth.hsvToRgb(hue, 0.65F, 1.0F);
        float mix = 0.2F + 0.8F * prism;
        float r = Mth.lerp(mix, 0.45F, ((prismatic >> 16) & 255) / 255.0F);
        float g = Mth.lerp(mix, 0.35F, ((prismatic >> 8) & 255) / 255.0F);
        float b = Mth.lerp(mix, 1.00F, (prismatic & 255) / 255.0F);
        if (mode == SoulSwordItem.MODE_ANNIHILATION) { // white-hot
            r = Mth.lerp(0.6F, r, 1.0F);
            g = Mth.lerp(0.6F, g, 0.95F);
            b = Mth.lerp(0.6F, b, 0.85F);
        }

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0F));
        poseStack.scale(1.0F, -1.0F, -1.0F);
        boolean inventoryLike = context == ItemDisplayContext.GUI || context == ItemDisplayContext.GROUND
                || context == ItemDisplayContext.FIXED || context == ItemDisplayContext.HEAD;
        if (inventoryLike) {
            float s = 0.62F;
            poseStack.scale(s, s, s);
            poseStack.translate(0.0F, 10.5F / 16.0F, 0.0F); // centre the blade in the slot
        } else {
            poseStack.translate(0.0F, 6.4F / 16.0F, 0.0F);  // grip sits where the hand holds it
        }

        VertexConsumer base_vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.renderToBuffer(poseStack, base_vc, light, OverlayTexture.NO_OVERLAY, -1);

        VertexConsumer glow = buffer.getBuffer(RenderType.eyes(GLOW));
        this.model.renderToBuffer(poseStack, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                rgb(r * intensity, g * intensity, b * intensity, 1.0F));

        // sparkling secondary layer once the blade holds a lot of souls
        if (prism > 0.15F || empowered) {
            int second = Mth.hsvToRgb((hue + 0.5F) % 1.0F, 0.8F, 1.0F);
            float a = Mth.clamp(0.25F + prism * 0.5F + (empowered ? 0.25F : 0.0F), 0.0F, 1.0F);
            poseStack.pushPose();
            float grow = 1.05F + 0.02F * (float) Math.sin(time * 7.0D);
            poseStack.scale(grow, 1.0F, grow);
            VertexConsumer aura = buffer.getBuffer(RenderType.eyes(GLOW));
            this.model.renderToBuffer(poseStack, aura, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    rgb(((second >> 16) & 255) / 255.0F * a, ((second >> 8) & 255) / 255.0F * a, (second & 255) / 255.0F * a, 1.0F));
            poseStack.popPose();
        }
        poseStack.popPose();
    }
}
