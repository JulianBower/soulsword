package com.soulsword.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.soulsword.entity.RealityRiftEntity;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Renders the rift as a see-through window. Pass order (each pass is flushed before the next one starts):
 * 1) MASK    - stencil := 1 where the jagged tear is visible (depth tested, nothing drawn)
 * 2) SKY     - the linked dimension's sky, centred on the camera, drawn only where stencil == 1
 * 3) CLEAR   - stencil := 0 again and the tear's depth is written
 * 4) GLOW    - prismatic rim, halo and cracks
 */
public class RealityRiftRenderer extends EntityRenderer<RealityRiftEntity> {
    private static final ResourceLocation END_SKY = ResourceLocation.withDefaultNamespace("textures/environment/end_sky.png");
    private static final ResourceLocation SUN = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    private static final int N = 16;
    private static final int STAR_COUNT = 160;
    private static final float[][] STARS = new float[STAR_COUNT][4];

    static {
        Random rnd = new Random(10842L);
        for (float[] star : STARS) {
            double a = rnd.nextDouble() * Math.PI * 2.0D;
            double y = rnd.nextDouble() * 2.0D - 1.0D;
            double r = Math.sqrt(1.0D - y * y);
            star[0] = (float) (Math.cos(a) * r);
            star[1] = (float) y;
            star[2] = (float) (Math.sin(a) * r);
            star[3] = 0.4F + rnd.nextFloat() * 0.7F;
        }
    }

    public RealityRiftRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(RealityRiftEntity entity) {
        return END_SKY;
    }

    @Override
    public boolean shouldRender(RealityRiftEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    private static float ease(float x) {
        return x * x * (3.0F - 2.0F * x);
    }

    private static void flush(MultiBufferSource buffer, RenderType type) {
        if (buffer instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(type);
        }
    }

    @Override
    public void render(RealityRiftEntity rift, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffer, int light) {
        float age = rift.tickCount + partialTick;
        float open = Mth.clamp(age / RealityRiftEntity.OPEN_TICKS, 0.0F, 1.0F);
        float close = Mth.clamp((RealityRiftEntity.MAX_AGE - age) / RealityRiftEntity.CLOSE_TICKS, 0.0F, 1.0F);
        float s = ease(open) * ease(close);
        if (s < 0.02F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double time = (Util.getMillis() % 1_000_000L) / 1000.0D;
        float[][][] shape = shape(rift.getId(), s, time);
        float[][] left = shape[0];
        float[][] right = shape[1];

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double ex = Mth.lerp(partialTick, rift.xo, rift.getX());
        double ey = Mth.lerp(partialTick, rift.yo, rift.getY());
        double ez = Mth.lerp(partialTick, rift.zo, rift.getZ());

        // 1) stencil mask
        ps.pushPose();
        ps.translate(0.0D, RealityRiftEntity.HEIGHT / 2.0D, 0.0D);
        ps.mulPose(Axis.YP.rotationDegrees(-rift.getYRot()));
        fill(buffer.getBuffer(ModRenderTypes.RIFT_MASK), ps.last().pose(), left, right);
        flush(buffer, ModRenderTypes.RIFT_MASK);
        ps.popPose();

        // 2) the other dimension, centred on the camera
        ps.pushPose();
        ps.translate(cam.x - ex, cam.y - ey, cam.z - ez);
        drawSky(rift.getDim(), mc, ps, buffer, partialTick, time);
        ps.popPose();

        // 3) clear the stencil, write depth of the tear
        ps.pushPose();
        ps.translate(0.0D, RealityRiftEntity.HEIGHT / 2.0D, 0.0D);
        ps.mulPose(Axis.YP.rotationDegrees(-rift.getYRot()));
        fill(buffer.getBuffer(ModRenderTypes.RIFT_CLEAR), ps.last().pose(), left, right);
        flush(buffer, ModRenderTypes.RIFT_CLEAR);

        // 4) prismatic rim (drawn just in front of the plane on both sides so depth testing picks the camera side)
        VertexConsumer glow = buffer.getBuffer(ModRenderTypes.RIFT_GLOW);
        for (float z : new float[]{0.02F, -0.02F}) {
            Matrix4f m = new Matrix4f(ps.last().pose()).translate(0.0F, 0.0F, z);
            rim(glow, m, left, right, s, time, rift.getId());
        }
        flush(buffer, ModRenderTypes.RIFT_GLOW);
        ps.popPose();
    }

    // ------------------------------------------------------------------ geometry

    /** Jagged lens-shaped outline: returns {left[N+1][2], right[N+1][2]}. */
    private static float[][][] shape(int seed, float s, double time) {
        RandomSource rnd = RandomSource.create(seed * 7919L + 17L);
        float[][] left = new float[N + 1][2];
        float[][] right = new float[N + 1][2];
        float halfHeight = RealityRiftEntity.HEIGHT / 2.0F;
        for (int i = 0; i <= N; i++) {
            double u = -1.0D + 2.0D * i / N;
            boolean tip = i == 0 || i == N;
            float y = (float) u * halfHeight * s + (tip ? 0.0F : (rnd.nextFloat() - 0.5F) * 0.14F * s);
            float hw = (float) RealityRiftEntity.halfWidthAt(u) * s;
            float wob = 0.03F * (float) Math.sin(time * 5.0D + i * 1.7D);
            float jl = tip ? 0.0F : (rnd.nextFloat() - 0.2F) * 0.32F * s;
            float jr = tip ? 0.0F : (rnd.nextFloat() - 0.2F) * 0.32F * s;
            float lx = -hw - jl + wob;
            float rx = hw + jr - wob;
            if (lx > rx) {
                float t = lx;
                lx = rx;
                rx = t;
            }
            left[i][0] = lx;
            left[i][1] = y;
            right[i][0] = rx;
            right[i][1] = y;
        }
        return new float[][][]{left, right};
    }

    private static void tri(VertexConsumer vc, Matrix4f m, float x1, float y1, float x2, float y2, float x3, float y3, float r, float g, float b, float a) {
        vc.addVertex(m, x1, y1, 0.0F).setColor(r, g, b, a);
        vc.addVertex(m, x2, y2, 0.0F).setColor(r, g, b, a);
        vc.addVertex(m, x3, y3, 0.0F).setColor(r, g, b, a);
    }

    private static void fill(VertexConsumer vc, Matrix4f m, float[][] left, float[][] right) {
        for (int i = 0; i < N; i++) {
            tri(vc, m, left[i][0], left[i][1], right[i][0], right[i][1], left[i + 1][0], left[i + 1][1], 1, 1, 1, 1);
            tri(vc, m, right[i][0], right[i][1], right[i + 1][0], right[i + 1][1], left[i + 1][0], left[i + 1][1], 1, 1, 1, 1);
        }
    }

    private static void line(VertexConsumer vc, Matrix4f m, float x1, float y1, float x2, float y2, float w, float r, float g, float b, float a) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-4F) return;
        float nx = -dy / len * w;
        float ny = dx / len * w;
        tri(vc, m, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x2 + nx, y2 + ny, r, g, b, a);
        tri(vc, m, x1 - nx, y1 - ny, x2 - nx, y2 - ny, x2 + nx, y2 + ny, r, g, b, a);
    }

    private static void rim(VertexConsumer vc, Matrix4f m, float[][] left, float[][] right, float s, double time, int seed) {
        for (int side = 0; side < 2; side++) {
            float[][] edge = side == 0 ? left : right;
            for (int i = 0; i < N; i++) {
                int rgb = Mth.hsvToRgb((float) ((i / (float) N + side * 0.5F + time * 0.35D) % 1.0D), 0.75F, 1.0F);
                float r = ((rgb >> 16) & 255) / 255.0F;
                float g = ((rgb >> 8) & 255) / 255.0F;
                float b = (rgb & 255) / 255.0F;
                line(vc, m, edge[i][0], edge[i][1], edge[i + 1][0], edge[i + 1][1], 0.24F * s, r, g, b, 0.16F); // halo
                line(vc, m, edge[i][0], edge[i][1], edge[i + 1][0], edge[i + 1][1], 0.075F * s, r, g, b, 0.9F);  // band
                line(vc, m, edge[i][0], edge[i][1], edge[i + 1][0], edge[i + 1][1], 0.025F * s, 1, 1, 1, 0.95F); // hot core
            }
        }
        // cracks radiating from the edge
        RandomSource rnd = RandomSource.create(seed * 31L + 5L);
        for (int k = 0; k < 7; k++) {
            boolean leftSide = rnd.nextBoolean();
            float[][] edge = leftSide ? left : right;
            int idx = 2 + rnd.nextInt(N - 3);
            float x = edge[idx][0];
            float y = edge[idx][1];
            float angle = (float) (rnd.nextDouble() * 0.9D - 0.45D) + (leftSide ? (float) Math.PI : 0.0F);
            int rgb = Mth.hsvToRgb((float) ((k / 7.0F + time * 0.5D) % 1.0D), 0.7F, 1.0F);
            for (int seg = 0; seg < 3; seg++) {
                float len = (0.18F + rnd.nextFloat() * 0.3F) * s;
                angle += (rnd.nextFloat() - 0.5F) * 1.2F;
                float nx = x + Mth.cos(angle) * len;
                float ny = y + Mth.sin(angle) * len;
                float fade = 1.0F - seg * 0.3F;
                line(vc, m, x, y, nx, ny, 0.03F * s, ((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F, 0.85F * fade);
                x = nx;
                y = ny;
            }
        }
    }

    // ------------------------------------------------------------------ the other side

    private static void drawSky(int dim, Minecraft mc, PoseStack ps, MultiBufferSource buffer, float partialTick, double time) {
        if (dim == 2) {
            VertexConsumer vc = buffer.getBuffer(ModRenderTypes.RIFT_SKY_TEXTURED.apply(END_SKY));
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees((float) (time * 1.5D)));
            for (int i = 0; i < 6; i++) {
                ps.pushPose();
                switch (i) {
                    case 1 -> ps.mulPose(Axis.XP.rotationDegrees(90.0F));
                    case 2 -> ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
                    case 3 -> ps.mulPose(Axis.XP.rotationDegrees(180.0F));
                    case 4 -> ps.mulPose(Axis.ZP.rotationDegrees(90.0F));
                    case 5 -> ps.mulPose(Axis.ZP.rotationDegrees(-90.0F));
                    default -> { }
                }
                Matrix4f m = ps.last().pose();
                vc.addVertex(m, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F).setColor(110, 92, 160, 255);
                vc.addVertex(m, -100.0F, -100.0F, 100.0F).setUv(0.0F, 1.0F).setColor(110, 92, 160, 255);
                vc.addVertex(m, 100.0F, -100.0F, 100.0F).setUv(1.0F, 1.0F).setColor(110, 92, 160, 255);
                vc.addVertex(m, 100.0F, -100.0F, -100.0F).setUv(1.0F, 0.0F).setColor(110, 92, 160, 255);
                ps.popPose();
            }
            ps.popPose();
            flush(buffer, ModRenderTypes.RIFT_SKY_TEXTURED.apply(END_SKY));
            return;
        }

        float tod = mc.level != null ? mc.level.getTimeOfDay(partialTick) : 0.0F;
        float day = Mth.clamp(Mth.cos(tod * Mth.TWO_PI) * 2.0F + 0.5F, 0.0F, 1.0F);
        float[] top;
        float[] horizon;
        float[] bottom;
        if (dim == 1) {
            float flicker = 0.92F + 0.08F * (float) Math.sin(time * 2.3D);
            top = new float[]{0.16F, 0.015F, 0.015F};
            horizon = new float[]{0.62F * flicker, 0.14F * flicker, 0.03F};
            bottom = new float[]{0.95F * flicker, 0.36F * flicker, 0.06F};
        } else {
            top = new float[]{Mth.lerp(day, 0.01F, 0.30F), Mth.lerp(day, 0.01F, 0.52F), Mth.lerp(day, 0.05F, 1.00F)};
            horizon = new float[]{Mth.lerp(day, 0.03F, 0.74F), Mth.lerp(day, 0.03F, 0.86F), Mth.lerp(day, 0.08F, 1.00F)};
            bottom = new float[]{Mth.lerp(day, 0.02F, 0.28F), Mth.lerp(day, 0.03F, 0.36F), Mth.lerp(day, 0.04F, 0.26F)};
        }

        VertexConsumer vc = buffer.getBuffer(ModRenderTypes.RIFT_SKY);
        Matrix4f m = ps.last().pose();
        int lat = 10;
        int lon = 20;
        float radius = 100.0F;
        for (int i = 0; i < lat; i++) {
            float a0 = Mth.PI * (-0.5F + (float) i / lat);
            float a1 = Mth.PI * (-0.5F + (float) (i + 1) / lat);
            for (int j = 0; j < lon; j++) {
                float b0 = Mth.TWO_PI * j / lon;
                float b1 = Mth.TWO_PI * (j + 1) / lon;
                float[] p00 = dir(a0, b0, radius);
                float[] p01 = dir(a0, b1, radius);
                float[] p10 = dir(a1, b0, radius);
                float[] p11 = dir(a1, b1, radius);
                float[] c0 = gradient(Mth.sin(a0), top, horizon, bottom);
                float[] c1 = gradient(Mth.sin(a1), top, horizon, bottom);
                sky(vc, m, p00, c0);
                sky(vc, m, p10, c1);
                sky(vc, m, p01, c0);
                sky(vc, m, p01, c0);
                sky(vc, m, p10, c1);
                sky(vc, m, p11, c1);
            }
        }
        if (dim == 0 && day < 0.55F) {
            float night = 1.0F - day / 0.55F;
            for (float[] star : STARS) {
                float[] d = {star[0], star[1], star[2]};
                float[] t1 = normalize(cross(d, Math.abs(d[1]) > 0.9F ? new float[]{1, 0, 0} : new float[]{0, 1, 0}));
                float[] t2 = cross(d, t1);
                float size = star[3];
                float c = night * star[3];
                float[] col = {c, c, c};
                float[] a = star(d, t1, t2, -size, -size);
                float[] b = star(d, t1, t2, size, -size);
                float[] cc = star(d, t1, t2, size, size);
                float[] dd = star(d, t1, t2, -size, size);
                sky(vc, m, a, col);
                sky(vc, m, b, col);
                sky(vc, m, cc, col);
                sky(vc, m, a, col);
                sky(vc, m, cc, col);
                sky(vc, m, dd, col);
            }
        }
        flush(buffer, ModRenderTypes.RIFT_SKY);

        if (dim == 0 && Mth.cos(tod * Mth.TWO_PI) > -0.05F) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-90.0F));
            ps.mulPose(Axis.XP.rotationDegrees(tod * 360.0F));
            Matrix4f sm = ps.last().pose();
            RenderType body = ModRenderTypes.RIFT_SKY_BODY.apply(SUN);
            VertexConsumer sv = buffer.getBuffer(body);
            float size = 30.0F;
            sv.addVertex(sm, -size, 100.0F, -size).setUv(0.0F, 0.0F);
            sv.addVertex(sm, size, 100.0F, -size).setUv(1.0F, 0.0F);
            sv.addVertex(sm, size, 100.0F, size).setUv(1.0F, 1.0F);
            sv.addVertex(sm, -size, 100.0F, size).setUv(0.0F, 1.0F);
            ps.popPose();
            flush(buffer, body);
        }
    }

    private static float[] dir(float lat, float lon, float r) {
        return new float[]{Mth.cos(lat) * Mth.cos(lon) * r, Mth.sin(lat) * r, Mth.cos(lat) * Mth.sin(lon) * r};
    }

    private static float[] gradient(float y, float[] top, float[] horizon, float[] bottom) {
        float[] to = y >= 0.0F ? top : bottom;
        float t = (float) Math.pow(Math.abs(y), y >= 0.0F ? 0.6D : 0.5D);
        return new float[]{Mth.lerp(t, horizon[0], to[0]), Mth.lerp(t, horizon[1], to[1]), Mth.lerp(t, horizon[2], to[2])};
    }

    private static void sky(VertexConsumer vc, Matrix4f m, float[] p, float[] c) {
        vc.addVertex(m, p[0], p[1], p[2]).setColor(c[0], c[1], c[2], 1.0F);
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float[] normalize(float[] v) {
        float l = Mth.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new float[]{v[0] / l, v[1] / l, v[2] / l};
    }

    private static float[] star(float[] d, float[] t1, float[] t2, float u, float v) {
        float r = 92.0F;
        return new float[]{d[0] * r + t1[0] * u + t2[0] * v, d[1] * r + t1[1] * u + t2[1] * v, d[2] * r + t1[2] * u + t2[2] * v};
    }
}
