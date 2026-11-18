package com.soulsword.client;

import com.soulsword.SoulSwordMod;
import com.soulsword.item.SoulSwordItem;
import com.soulsword.network.SwitchSwordModePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = SoulSwordMod.MODID, value = Dist.CLIENT)
public final class ClientGameEvents {
    private ClientGameEvents() {}

    private static boolean holdsSword(LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof SoulSwordItem
                || player.getOffhandItem().getItem() instanceof SoulSwordItem;
    }

    /** X cycles Soul Rupture -> Reality Cut -> ANNIHILATION. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (ClientKeys.SWITCH_MODE.consumeClick()) {
            if (mc.player != null && mc.screen == null && holdsSword(mc.player)) {
                PacketDistributor.sendToServer(new SwitchSwordModePayload());
            }
        }
    }

    /** Annihilation casting pose: the caster cannot move for the duration of the cast. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !player.isUsingItem()) return;
        ItemStack using = player.getUseItem();
        if (using.getItem() instanceof SoulSwordItem && SoulSwordItem.getMode(using) == SoulSwordItem.MODE_ANNIHILATION) {
            event.getInput().forwardImpulse = 0.0F;
            event.getInput().leftImpulse = 0.0F;
            event.getInput().jumping = false;
        }
    }
}
