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
 * Orbital laser renderer.
 * <ul>
 *   <li>Descent: a streak is drawn from the ceiling (Y = 320) to a bright head that accelerates down to the surface.</li>
 *   <li>Impact: shockwave rings expand along the ground.</li>
 *   <li>Drilling (Annihilation): the head keeps boring down and a luminous hemisphere dome
 *       ({@link RenderType#entityTranslucentEmissive}, FULL_BRIGHT) travels down with it, shrinking with the bowl.</li>
 * </ul>
 * Everything is fullbright and double-sided so it also fills the view from inside.
 */
public class AnnihilationBeamRenderer extends EntityRenderer<AnnihilationBeamEntity> {
    private static final ResourceLocation BEAM_TEXTURE = SoulSwordMod.id("textures/entity/annihilation_beam.png");
    private static final ResourceLocation DOME_TEXTURE = SoulSwordMod.id("textures/entity/annihilation_dome.png");
    private static final int SEGMENTS = 32;

    public AnnihilationBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(AnnihilationBeamEntity entity) {
        return BEAM_TEXTURE;
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
        int impact = beam.getImpactTick();
        float fade = Mth.clamp((life - age) / (big ? 30.0F : 6.0F), 0.0F, 1.0F);
        if (fade <= 0.0F) {
            return;
        }
        double time = (Util.getMillis() % 1_000_000L) / 1000.0D;
        float baseRadius = beam.getBeamRadius();
        int craterRadius = beam.getCraterRadius();
        int depth = beam.getDepth();

        double y = Mth.lerp(partialTick, beam.yo, beam.getY());
        float top = (float) (beam.level().getMaxBuildHeight() - y); // the beam starts at the sky ceiling
        float drill = big ? AnnihilationBeamEntity.drillProgress(age, impact, beam.getDrillTicks()) : 0.0F;

        // leading tip: accelerates from the ceiling down to the surface, then bores towards bedrock
        float tip;
        if (age < impact) {
            float f = age / impact;
            tip = Mth.lerp(f * f, top, 0.0F);
        } else {
            tip = -depth * drill;
        }
        if (!big) {
            tip = Math.max(tip, -0.2F);
        }

        float sincePost = age - impact;
        float widen = sincePost < 0 ? 0.0F : easeOut(sincePost / 8.0F);
        float radius = Math.max(big ? 0.9F : 0.35F, baseRadius * Mth.lerp(widen, big ? 0.08F : 0.4F, 1.0F));
        radius *= 1.0F + 0.025F * (float) Math.sin(time * 22.0D);
        float streakTop = age < impact ? 0.12F : 1.0F; // fading trail behind the falling head

        VertexConsumer vc = buffer.getBuffer(RenderType.eyes(BEAM_TEXTURE));
        Matrix4f m = ps.last().pose();
        float scroll = (float) (time * 1.8D);

        wall(vc, m, radius, tip, top, 0.55F, 1.0F * fade, streakTop * fade * 0.35F, scroll, 0.0F, time, false);
        wall(vc, m, radius * 0.62F, tip, top, 0.45F, 0.9F * fade, streakTop * fade * 0.65F, -scroll * 1.3F, 0.33F, time, false);
        wall(vc, m, radius * 0.28F, tip, top, 0.0F, 1.0F * fade, streakTop * fade * 0.95F, scroll * 2.0F, 0.0F, time, true);
        wall(vc, m, Math.max(0.2F, radius * 0.10F), tip, top, 0.0F, 1.0F * fade, 1.0F * fade, scroll * 3.0F, 0.0F, time, true);

        // white-hot head at the leading tip
        float headRadius = age < impact ? Math.max(1.6F, baseRadius * 0.16F) : Math.max(1.4F, baseRadius * 0.10F);
        shell(vc, m, 0.0F, tip, 0.0F, headRadius, -Mth.HALF_PI, Mth.HALF_PI, 8, 14, 0.0F, 0.0F, fade, scroll, time);
        shell(vc, m, 0.0F, tip, 0.0F, headRadius * 1.8F, -Mth.HALF_PI, Mth.HALF_PI, 8, 14, 0.0F, 0.6F, 0.35F * fade, -scroll, time);

        // expanding shockwave rings at the impact point
        if (sincePost >= 0.0F) {
            float finalRadius = big ? craterRadius * 1.25F : (baseRadius - 0.6F) / 0.12F;
            float duration = big ? 26.0F : 10.0F;
            for (int k = 0; k < 3; k++) {
                float p = Mth.clamp((sincePost - k * (big ? 4.0F : 1.5F)) / duration, 0.0F, 1.0F);
                if (p <= 0.0F || p >= 1.0F) continue;
                float rr = 1.0F + finalRadius * easeOut(p);
                float height = 3.0F + rr * 0.05F + k * 2.0F;
                float a = (1.0F - p) * 0.8F * fade;
                wall(vc, m, rr, -0.5F + k * 0.8F, -0.5F + k * 0.8F + height, 0.75F, a, a, 0.0F, k * 0.3F, time, false);
            }
        }

        // the travelling dome (Annihilation only)
        if (big && sincePost >= 0.0F && craterRadius > 0) {
            float grow = easeOut(sincePost / 6.0F);
            float domeRadius = AnnihilationBeamEntity.domeRadius(craterRadius, drill) * grow;
            float pulse = 1.0F + 0.015F * (float) Math.sin(time * 9.0D);
            float alpha = fade * (0.9F - 0.35F * drill);
            VertexConsumer dome = buffer.getBuffer(RenderType.entityTranslucentEmissive(DOME_TEXTURE));
            float cy = tip;
            shell(dome, m, 0.0F, cy, 0.0F, domeRadius * pulse, 0.0F, Mth.HALF_PI, 10, 32, 0.0F, 0.55F, 0.55F * alpha, scroll * 0.3F, time);
            shell(dome, m, 0.0F, cy, 0.0F, domeRadius * 0.93F * pulse, 0.0F, Mth.HALF_PI, 10, 32, 0.5F, 0.45F, 0.40F * alpha, -scroll * 0.4F, time);
            wall(dome, m, domeRadius * pulse, cy - 0.6F, cy + 1.6F, 0.6F, alpha, alpha, scroll, 0.25F, time, false);
        }
    }

    /** One double-sided cylinder wall. {@code saturation} 0 (or {@code white}) gives pure white, else a rainbow around it. */
    private static void wall(VertexConsumer vc, Matrix4f m, float radius, float y0, float y1, float saturation, float alpha0, float alpha1,
                             float scroll, float hueOffset, double time, boolean white) {
        if ((alpha0 <= 0.002F && alpha1 <= 0.002F) || y1 <= y0) return;
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
            vertex(vc, m, x0, y0, z0, c0, alpha0, u0, v0);
            vertex(vc, m, x1, y0, z1, c1, alpha0, u1, v0);
            vertex(vc, m, x1, y1, z1, c1, alpha1, u1, v1);
            vertex(vc, m, x0, y1, z0, c0, alpha1, u0, v1);
            vertex(vc, m, x0, y1, z0, c0, alpha1, u0, v1);
            vertex(vc, m, x1, y1, z1, c1, alpha1, u1, v1);
            vertex(vc, m, x1, y0, z1, c1, alpha0, u1, v0);
            vertex(vc, m, x0, y0, z0, c0, alpha0, u0, v0);
        }
    }

    /** A double-sided latitude band of a sphere (latFrom..latTo in radians, -PI/2 = south pole, 0 = equator). */
    private static void shell(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float r, float latFrom, float latTo,
                              int latSegs, int lonSegs, float hueOffset, float saturation, float alpha, float scroll, double time) {
        if (alpha <= 0.002F || r <= 0.0F) return;
        for (int i = 0; i < latSegs; i++) {
            float p0 = Mth.lerp((float) i / latSegs, latFrom, latTo);
            float p1 = Mth.lerp((float) (i + 1) / latSegs, latFrom, latTo);
            float v0 = (float) i / latSegs + scroll;
            float v1 = (float) (i + 1) / latSegs + scroll;
            for (int j = 0; j < lonSegs; j++) {
                float t0 = Mth.TWO_PI * j / lonSegs;
                float t1 = Mth.TWO_PI * (j + 1) / lonSegs;
                float u0 = (float) j / lonSegs * 4.0F;
                float u1 = (float) (j + 1) / lonSegs * 4.0F;
                int c0 = Mth.hsvToRgb(hue(j, lonSegs, p0, hueOffset, time), saturation, 1.0F);
                int c1 = Mth.hsvToRgb(hue(j + 1, lonSegs, p0, hueOffset, time), saturation, 1.0F);
                int c2 = Mth.hsvToRgb(hue(j + 1, lonSegs, p1, hueOffset, time), saturation, 1.0F);
                int c3 = Mth.hsvToRgb(hue(j, lonSegs, p1, hueOffset, time), saturation, 1.0F);
                float ax = cx + Mth.cos(p0) * Mth.cos(t0) * r, ay = cy + Mth.sin(p0) * r, az = cz + Mth.cos(p0) * Mth.sin(t0) * r;
                float bx = cx + Mth.cos(p0) * Mth.cos(t1) * r, bz = cz + Mth.cos(p0) * Mth.sin(t1) * r;
                float dx = cx + Mth.cos(p1) * Mth.cos(t1) * r, dy = cy + Mth.sin(p1) * r, dz = cz + Mth.cos(p1) * Mth.sin(t1) * r;
                float ex = cx + Mth.cos(p1) * Mth.cos(t0) * r, ez = cz + Mth.cos(p1) * Mth.sin(t0) * r;
                vertex(vc, m, ax, ay, az, c0, alpha, u0, v0);
                vertex(vc, m, bx, ay, bz, c1, alpha, u1, v0);
                vertex(vc, m, dx, dy, dz, c2, alpha, u1, v1);
                vertex(vc, m, ex, dy, ez, c3, alpha, u0, v1);
                vertex(vc, m, ex, dy, ez, c3, alpha, u0, v1);
                vertex(vc, m, dx, dy, dz, c2, alpha, u1, v1);
                vertex(vc, m, bx, ay, bz, c1, alpha, u1, v0);
                vertex(vc, m, ax, ay, az, c0, alpha, u0, v0);
            }
        }
    }

    private static float hue(int lon, int lonSegs, float lat, float offset, double time) {
        return (float) (((lon % lonSegs) / (float) lonSegs * 2.0D + offset + lat * 0.3D + time * 0.2D) % 1.0D + 1.0D) % 1.0F;
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
                .setColor(((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F, Mth.clamp(alpha, 0.0F, 1.0F))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }
}
