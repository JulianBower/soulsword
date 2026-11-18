package com.soulsword.client;

import com.soulsword.SoulSwordMod;
import com.soulsword.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Replaces the heart sprites with an animated, swirling mashed-rainbow oil-slick heart while Soul Sever is active. */
@EventBusSubscriber(modid = SoulSwordMod.MODID, value = Dist.CLIENT)
public final class SoulSeverHearts {
    private static final ResourceLocation CONTAINER = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation CONTAINER_HARDCORE = ResourceLocation.withDefaultNamespace("hud/heart/container_hardcore");
    private static final ResourceLocation ABSORB_FULL = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_full");
    private static final ResourceLocation ABSORB_HALF = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_half");
    private static final ResourceLocation SEVER_FULL = SoulSwordMod.id("hud/soul_sever_heart_full");
    private static final ResourceLocation SEVER_HALF = SoulSwordMod.id("hud/soul_sever_heart_half");

    private SoulSeverHearts() {}

    @SubscribeEvent
    public static void onHealthLayer(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.PLAYER_HEALTH.equals(event.getName())) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.getCameraEntity() instanceof Player player) || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return;
        if (!player.hasEffect(ModEffects.SOUL_SEVER)) return;

        event.setCanceled(true);
        GuiGraphics g = event.getGuiGraphics();
        Gui gui = mc.gui;

        int health = Mth.ceil(player.getHealth());
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        int maxHearts = Mth.ceil(player.getMaxHealth() / 2.0F);
        int absorbHearts = Mth.ceil(absorption / 2.0F);
        int total = maxHearts + absorbHearts;
        int rows = Mth.ceil(total / 10.0F);
        int rowHeight = Math.max(10 - (rows - 2), 3);
        int left = g.guiWidth() / 2 - 91;
        int top = g.guiHeight() - gui.leftHeight;
        gui.leftHeight += (rows - 1) * rowHeight + 10;
        boolean hardcore = player.level().getLevelData().isHardcore();
        boolean lowHealth = health + absorption <= 4;
        int shake = lowHealth ? mc.gui.getGuiTicks() : 0;

        for (int i = total - 1; i >= 0; i--) {
            int row = i / 10;
            int col = i % 10;
            int x = left + col * 8;
            int y = top - row * rowHeight;
            if (lowHealth) {
                y += new java.util.Random(shake * 31L + i).nextInt(2);
            }
            int p = i * 2;
            if (i < maxHearts) {
                g.blitSprite(hardcore ? CONTAINER_HARDCORE : CONTAINER, x, y, 9, 9);
                if (p < health) {
                    g.blitSprite(p + 1 == health ? SEVER_HALF : SEVER_FULL, x, y, 9, 9);
                }
            } else {
                int q = p - maxHearts * 2;
                if (q < absorption) {
                    g.blitSprite(q + 1 == absorption ? ABSORB_HALF : ABSORB_FULL, x, y, 9, 9);
                }
            }
        }
    }
}
