package com.soulsword.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.soulsword.SoulSwordMod;
import com.soulsword.item.SoulSwordItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.List;

/**
 * Cheat command (operators only):
 * <pre>
 *   /soul_sword souls set &lt;amount&gt; [players]
 *   /soul_sword souls add &lt;amount&gt; [players]
 * </pre>
 * Applies to the Soul Sword in the target's main hand (or off hand). Results are capped to 0..100,000.
 */
@EventBusSubscriber(modid = SoulSwordMod.MODID)
public final class SoulSwordCommand {
    private SoulSwordCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("soul_sword")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("souls")
                        .then(Commands.literal("set")
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(ctx -> run(ctx, false, List.of(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.argument("players", EntityArgument.players())
                                                .executes(ctx -> run(ctx, false, EntityArgument.getPlayers(ctx, "players"))))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("amount", LongArgumentType.longArg())
                                        .executes(ctx -> run(ctx, true, List.of(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.argument("players", EntityArgument.players())
                                                .executes(ctx -> run(ctx, true, EntityArgument.getPlayers(ctx, "players"))))))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, boolean add, Collection<ServerPlayer> targets) throws CommandSyntaxException {
        long amount = LongArgumentType.getLong(ctx, "amount");
        int changed = 0;
        long last = 0;
        for (ServerPlayer player : targets) {
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof SoulSwordItem)) {
                stack = player.getOffhandItem();
                if (!(stack.getItem() instanceof SoulSwordItem)) continue;
            }
            if (add) {
                SoulSwordItem.setSouls(stack, SoulSwordItem.getSouls(stack) + amount);
            } else {
                SoulSwordItem.setSouls(stack, amount);
            }
            last = SoulSwordItem.getSouls(stack);
            changed++;
        }
        if (changed == 0) {
            ctx.getSource().sendFailure(Component.translatable("commands.soul_sword.no_sword"));
            return 0;
        }
        final int count = changed;
        final long shown = last;
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.soul_sword.souls", count, shown), true);
        return changed;
    }
}
