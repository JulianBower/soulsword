package com.soulsword.network;

import com.soulsword.SoulSwordMod;
import com.soulsword.item.SoulSwordItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sent by the client when the X key is pressed. */
public record SwitchSwordModePayload() implements CustomPacketPayload {
    public static final Type<SwitchSwordModePayload> TYPE = new Type<>(SoulSwordMod.id("switch_mode"));
    public static final StreamCodec<ByteBuf, SwitchSwordModePayload> CODEC = StreamCodec.unit(new SwitchSwordModePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwitchSwordModePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                SoulSwordItem.cycleMode(player);
            }
        });
    }
}
