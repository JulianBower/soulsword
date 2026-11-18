package com.soulsword.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soulsword.SoulSwordMod;
import com.soulsword.entity.AnnihilationBeamEntity;
import net.minecraft.Util;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Towering, fullbright vertical beam: a white-hot core wrapped in prismatic layers, with expanding shockwave rings.
 * Rendered additively with {@link LightTexture#FULL_BRIGHT}, double-sided so it also fills the view from inside.
 */
public class AnnihilationBeamRenderer extends EntityRenderer<AnnihilationBeamEntity> {
    private static final ResourceLocation TEXTURE = SoulSwordMod.id("textures/entity/annihilation_beam.png");
    private static final int SEGMENTS = 32;

    public AnnihilationBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(AnnihilationBeamEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(AnnihilationBeamEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    private static float easeOut(float x) {
        float t = 1.0F - Mth.clamp(x, 0.0F, 1.0F);
        return 1.0F - t * t * t;
    }

    @Override
    public void render(AnnihilationBeamEntity beam, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffer, int light) {
        boolean big = beam.getVariant() == AnnihilationBeamEntity.VARIANT_ANNIHILATION;
        float age = beam.tickCount + partialTick;
        int life = beam.getLifetime();
        float fade = Mth.clamp((life - age) / (big ? 30.0F : 6.0F), 0.0F, 1.0F);
        if (fade <= 0.0F) {
            return;
        }
        double time = (Util.getMillis() % 1_000_000L) / 1000.0D;
        float baseRadius = beam.getBeamRadius();

        double y = Mth.lerp(partialTick, beam.yo, beam.getY());
        float top = (float) (beam.level().getMaxBuildHeight() - y);
        float bottom = big ? (float) (beam.level().getMinBuildHeight() - y) : -0.2F;
        float descend = easeOut(age / (big ? 8.0F : 3.0F));
        float visibleBottom = Mth.lerp(descend, top, bottom);

        float widen;
        float detonateAge;
        if (big) {
            detonateAge = age - AnnihilationBeamEntity.DETONATE_TICK;
            widen = detonateAge < 0 ? 0.0F : easeOut(detonateAge / 8.0F);
        } else {
            detonateAge = age;
            widen = easeOut(age / 2.0F);
        }
        float radius = Math.max(big ? 0.9F : 0.35F, baseRadius * Mth.lerp(widen, big ? 0.05F : 0.4F, 1.0F));
        radius *= 1.0F + 0.025F * (float) Math.sin(time * 22.0D);

        VertexConsumer vc = buffer.getBuffer(RenderType.eyes(TEXTURE));
        Matrix4f m = ps.last().pose();
        float scroll = (float) (time * 1.8D);

        wall(vc, m, radius * 1.00F, visibleBottom, top, 0.55F, 0.35F * fade, scroll, 0.0F, time, false);
        wall(vc, m, radius * 0.62F, visibleBottom, top, 0.45F, 0.65F * fade, -scroll * 1.3F, 0.33F, time, false);
        wall(vc, m, radius * 0.28F, visibleBottom, top, 0.00F, 0.95F * fade, scroll * 2.0F, 0.0F, time, true);
        wall(vc, m, Math.max(0.2F, radius * 0.10F), visibleBottom, top, 0.00F, 1.00F * fade, scroll * 3.0F, 0.0F, time, true);

        // expanding shockwave rings around the impact point
        if (detonateAge >= 0.0F) {
            float finalRadius = big ? (baseRadius - 1.5F) / 0.45F * 1.25F : (baseRadius - 0.6F) / 0.12F;
            float duration = big ? 26.0F : 10.0F;
            for (int k = 0; k < 3; k++) {
                float p = Mth.clamp((detonateAge - k * (big ? 4.0F : 1.5F)) / duration, 0.0F, 1.0F);
                if (p <= 0.0F || p >= 1.0F) continue;
                float rr = 1.0F + finalRadius * easeOut(p);
                float height = 3.0F + rr * 0.05F + k * 2.0F;
                float a = (1.0F - p) * 0.8F * fade;
                wall(vc, m, rr, -0.5F + k * 0.8F, -0.5F + k * 0.8F + height, 0.75F, a, 0.0F, k * 0.3F, time, false);
            }
        }
    }

    /** One double-sided cylinder wall. {@code saturation} 0 gives pure white, otherwise a rainbow around the circumference. */
    private static void wall(VertexConsumer vc, Matrix4f m, float radius, float y0, float y1, float saturation, float alpha,
                             float scroll, float hueOffset, double time, boolean white) {
        if (alpha <= 0.002F || y1 <= y0) return;
        float v0 = y0 / 16.0F + scroll;
        float v1 = y1 / 16.0F + scroll;
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float x0 = Mth.cos(a0) * radius, z0 = Mth.sin(a0) * radius;
            float x1 = Mth.cos(a1) * radius, z1 = Mth.sin(a1) * radius;
            float u0 = (float) i / SEGMENTS * 4.0F;
            float u1 = (float) (i + 1) / SEGMENTS * 4.0F;
            int c0 = color(i, saturation, hueOffset, time, white);
            int c1 = color(i + 1, saturation, hueOffset, time, white);
            // front winding
            vertex(vc, m, x0, y0, z0, c0, alpha, u0, v0);
            vertex(vc, m, x1, y0, z1, c1, alpha, u1, v0);
            vertex(vc, m, x1, y1, z1, c1, alpha, u1, v1);
            vertex(vc, m, x0, y1, z0, c0, alpha, u0, v1);
            // back winding (visible from inside the beam)
            vertex(vc, m, x0, y1, z0, c0, alpha, u0, v1);
            vertex(vc, m, x1, y1, z1, c1, alpha, u1, v1);
            vertex(vc, m, x1, y0, z1, c1, alpha, u1, v0);
            vertex(vc, m, x0, y0, z0, c0, alpha, u0, v0);
        }
    }

    private static int color(int segment, float saturation, float hueOffset, double time, boolean white) {
        if (white || saturation <= 0.0F) {
            return 0xFFFFFF;
        }
        float hue = (float) (((segment % SEGMENTS) / (float) SEGMENTS * 2.0D + hueOffset + time * 0.25D) % 1.0D);
        return Mth.hsvToRgb(hue, saturation, 1.0F);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, int rgb, float alpha, float u, float v) {
        vc.addVertex(m, x, y, z)
                .setColor(((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }
}
