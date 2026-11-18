package com.soulsword.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.function.Function;

/**
 * Custom render types. The rift uses four chained passes that share the stencil buffer:
 * MASK (write 1 where the tear is visible) -> SKY (draw the other dimension where stencil == 1)
 * -> CLEAR (reset stencil, write the tear's depth) -> GLOW (the prismatic rim).
 */
public final class ModRenderTypes extends RenderType {
    private ModRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        throw new UnsupportedOperationException("not instantiable");
    }

    private static void stencilOff() {
        RenderSystem.stencilMask(0xFF);
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    private static final RenderStateShard.LayeringStateShard STENCIL_WRITE = new RenderStateShard.LayeringStateShard("soul_sword_stencil_write", () -> {
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        RenderSystem.stencilMask(0xFF);
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
    }, ModRenderTypes::stencilOff);

    private static final RenderStateShard.LayeringStateShard STENCIL_TEST = new RenderStateShard.LayeringStateShard("soul_sword_stencil_test", () -> {
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        RenderSystem.stencilMask(0x00);
        RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
    }, ModRenderTypes::stencilOff);

    private static final RenderStateShard.LayeringStateShard STENCIL_CLEAR = new RenderStateShard.LayeringStateShard("soul_sword_stencil_clear", () -> {
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        RenderSystem.stencilMask(0xFF);
        RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_ZERO);
    }, ModRenderTypes::stencilOff);

    private static final RenderStateShard.WriteMaskStateShard NO_WRITE = new RenderStateShard.WriteMaskStateShard(false, false);

    /** Pass 1: no colour, no depth - only marks the stencil where the tear is visible. */
    public static final RenderType RIFT_MASK = create("soul_sword_rift_mask",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 1536, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(NO_WRITE)
                    .setLayeringState(STENCIL_WRITE)
                    .createCompositeState(false));

    /** Pass 2a: vertex-coloured sky dome, drawn only where stencil == 1. */
    public static final RenderType RIFT_SKY = create("soul_sword_rift_sky",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 4096, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setLayeringState(STENCIL_TEST)
                    .createCompositeState(false));

    /** Pass 2b: textured sky cube (The End), drawn only where stencil == 1. */
    public static final Function<ResourceLocation, RenderType> RIFT_SKY_TEXTURED = Util.memoize(tex -> create("soul_sword_rift_sky_tex",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_TEXTURE_COLOR_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setLayeringState(STENCIL_TEST)
                    .createCompositeState(false)));

    /** Pass 2c: celestial bodies (sun) over the dome, additive, only where stencil == 1. */
    public static final Function<ResourceLocation, RenderType> RIFT_SKY_BODY = Util.memoize(tex -> create("soul_sword_rift_body",
            DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 256, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_TEXTURE_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setLayeringState(STENCIL_TEST)
                    .createCompositeState(false)));

    /** Pass 3: resets the stencil and writes the tear's depth so later translucent geometry behind it is hidden. */
    public static final RenderType RIFT_CLEAR = create("soul_sword_rift_clear",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 1536, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(DEPTH_WRITE)
                    .setLayeringState(STENCIL_CLEAR)
                    .createCompositeState(false));

    /** Pass 4: the glowing prismatic edge. */
    public static final RenderType RIFT_GLOW = create("soul_sword_rift_glow",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 4096, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));
}
