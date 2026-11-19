package com.soulsword.network;

import com.soulsword.SoulSwordMod;
import com.soulsword.item.SoulSwordItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Vanilla swallows left-clicks while an item is being used, but Soul Rupture / Reality Cut are driven by holding
 * right-click. The client therefore forwards the attack it would have made; the server validates reach and strikes.
 */
public record SwordStrikePayload(int entityId) implements CustomPacketPayload {
    public static final Type<SwordStrikePayload> TYPE = new Type<>(SoulSwordMod.id("sword_strike"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SwordStrikePayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, SwordStrikePayload::entityId, SwordStrikePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwordStrikePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            var held = player.getMainHandItem();
            if (!(held.getItem() instanceof SoulSwordItem) || !SoulSwordItem.isActive(player)
                    || SoulSwordItem.getMode(held) == SoulSwordItem.MODE_ANNIHILATION) return;
            Entity target = player.serverLevel().getEntity(payload.entityId());
            if (target == null || target == player || !target.isAttackable() || !target.isAlive()) return;
            if (!player.canInteractWithEntity(target.getBoundingBox(), 1.0D)) return;
            player.attack(target);
            player.swing(InteractionHand.MAIN_HAND, true);
        });
    }
}
