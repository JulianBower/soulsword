package com.soulsword.item;

import com.soulsword.ability.SoulSwordAbilities;
import com.soulsword.registry.ModComponents;
import com.soulsword.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class SoulSwordItem extends SwordItem {
    public static final int MODE_RUPTURE = 0;
    public static final int MODE_REALITY_CUT = 1;
    public static final int MODE_ANNIHILATION = 2;

    public static final int HIT_COST = 250;
    public static final int ANNIHILATION_MIN = 100;
    public static final int CHARGE_TICKS = 20;
    public static final int CAST_LOCK_TICKS = 40;
    public static final int SEVER_TICKS = 200;

    private static final String KEY_ACTIVE_UNTIL = "soul_sword_active_until";
    private static final String KEY_CHARGE_START = "soul_sword_charge_start";
    private static final String KEY_NOTIFIED = "soul_sword_charge_notified";

    public SoulSwordItem(Properties properties) {
        super(Tiers.NETHERITE, properties);
    }

    // ---------------------------------------------------------------- data helpers

    public static long getSouls(ItemStack stack) {
        return Math.max(0L, stack.getOrDefault(ModComponents.SOULS.get(), 0L));
    }

    public static void setSouls(ItemStack stack, long souls) {
        stack.set(ModComponents.SOULS.get(), Math.max(0L, souls));
    }

    public static void addSouls(ItemStack stack, long amount) {
        long current = getSouls(stack);
        long next = current + amount;
        setSouls(stack, next < current ? Long.MAX_VALUE : next); // overflow guard only
    }

    public static int getMode(ItemStack stack) {
        return Math.floorMod(stack.getOrDefault(ModComponents.MODE.get(), 0), 3);
    }

    public static int getRiftDim(ItemStack stack) {
        return Math.floorMod(stack.getOrDefault(ModComponents.RIFT_DIM.get(), 0), 3);
    }

    /** Right-click held recently (Soul Rupture empowered / Reality Cut charging). */
    public static boolean isActive(Player player) {
        return player.getPersistentData().getLong(KEY_ACTIVE_UNTIL) >= player.level().getGameTime();
    }

    /** Reality Cut: held long enough to be charged. */
    public static boolean isCharged(Player player) {
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();
        return data.getLong(KEY_ACTIVE_UNTIL) >= now && now - data.getLong(KEY_CHARGE_START) >= CHARGE_TICKS;
    }

    /** True while the blade should burn bright (used by both logic and renderer). */
    public static boolean isEmpowered(Player player, ItemStack stack) {
        int mode = getMode(stack);
        if (mode == MODE_RUPTURE) return isActive(player);
        if (mode == MODE_REALITY_CUT) return isCharged(player);
        return false;
    }

    public static void clearActive(Player player) {
        CompoundTag data = player.getPersistentData();
        data.putLong(KEY_ACTIVE_UNTIL, -1L);
        data.putBoolean(KEY_NOTIFIED, false);
    }

    public static void cycleMode(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof SoulSwordItem)) {
            stack = player.getOffhandItem();
            if (!(stack.getItem() instanceof SoulSwordItem)) return;
        }
        int next = (getMode(stack) + 1) % 3;
        stack.set(ModComponents.MODE.get(), next);
        player.stopUsingItem();
        clearActive(player);
        player.displayClientMessage(Component.translatable("message.soul_sword.mode",
                Component.translatable("mode.soul_sword." + next)).withStyle(modeColor(next), ChatFormatting.BOLD), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F + next * 0.4F);
    }

    public static ChatFormatting modeColor(int mode) {
        return switch (mode) {
            case MODE_RUPTURE -> ChatFormatting.AQUA;
            case MODE_REALITY_CUT -> ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }

    // ---------------------------------------------------------------- behaviour

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int mode = getMode(stack);

        if (mode == MODE_ANNIHILATION) {
            if (getSouls(stack) < ANNIHILATION_MIN) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("message.soul_sword.not_enough", ANNIHILATION_MIN).withStyle(ChatFormatting.RED), true);
                }
                player.getCooldowns().addCooldown(this, 20);
                return InteractionResultHolder.fail(stack);
            }
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                SoulSwordAbilities.castAnnihilation(serverPlayer, stack);
            }
            player.startUsingItem(hand); // casting pose + client-side movement lock
            player.getCooldowns().addCooldown(this, CAST_LOCK_TICKS + 20);
            return InteractionResultHolder.consume(stack);
        }

        if (mode == MODE_REALITY_CUT && player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                int next = (getRiftDim(stack) + 1) % 3;
                stack.set(ModComponents.RIFT_DIM.get(), next);
                player.displayClientMessage(Component.translatable("message.soul_sword.rift_dim",
                        Component.translatable(SoulSwordAbilities.dimNameKey(next))).withStyle(ChatFormatting.LIGHT_PURPLE), true);
                level.playSound(null, player.blockPosition(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1.0F, 1.2F);
            }
            player.getCooldowns().addCooldown(this, 6);
            return InteractionResultHolder.success(stack);
        }

        // Modes 0 and 1: while right-click is held, use() fires every few ticks and keeps the state alive.
        CompoundTag data = player.getPersistentData();
        long now = level.getGameTime();
        boolean fresh = data.getLong(KEY_ACTIVE_UNTIL) < now;
        if (fresh) {
            data.putLong(KEY_CHARGE_START, now);
            data.putBoolean(KEY_NOTIFIED, false);
        }
        data.putLong(KEY_ACTIVE_UNTIL, now + 7);

        if (!level.isClientSide) {
            if (mode == MODE_RUPTURE && fresh) {
                level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
                player.displayClientMessage(Component.translatable("message.soul_sword.empowered").withStyle(ChatFormatting.AQUA), true);
            }
            if (mode == MODE_REALITY_CUT && !data.getBoolean(KEY_NOTIFIED) && now - data.getLong(KEY_CHARGE_START) >= CHARGE_TICKS) {
                data.putBoolean(KEY_NOTIFIED, true);
                level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.4F);
                player.displayClientMessage(Component.translatable("message.soul_sword.charged").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof ServerPlayer player && target.level() instanceof ServerLevel level && target != attacker) {
            int mode = getMode(stack);
            long souls = getSouls(stack);
            if (mode == MODE_RUPTURE && isActive(player)) {
                if (souls >= HIT_COST) {
                    setSouls(stack, souls - HIT_COST);
                    SoulSwordAbilities.soulRupture(level, player, target, souls);
                } else {
                    notEnough(player);
                }
            } else if (mode == MODE_REALITY_CUT && isCharged(player)) {
                if (souls >= HIT_COST) {
                    setSouls(stack, souls - HIT_COST);
                    target.addEffect(new MobEffectInstance(ModEffects.SOUL_SEVER, SEVER_TICKS, 0));
                    SoulSwordAbilities.sliceRift(level, player, target, getRiftDim(stack));
                } else {
                    notEnough(player);
                }
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    private static void notEnough(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.soul_sword.not_enough", HIT_COST).withStyle(ChatFormatting.RED), true);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR; // only ever used by Annihilation's casting pose
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return CAST_LOCK_TICKS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getSouls(stack) > 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        long souls = getSouls(stack);
        int mode = getMode(stack);
        tooltip.add(Component.translatable("item.soul_sword.soul_sword.subtitle").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.soul_sword.souls", Component.literal(NumberFormat.getIntegerInstance(Locale.US).format(souls)).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.soul_sword.bonus", Component.literal(String.format(Locale.US, "%.2f", SoulMath.bonusDamage(souls))).withStyle(ChatFormatting.RED)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.soul_sword.mode", Component.translatable("mode.soul_sword." + mode).withStyle(modeColor(mode))).withStyle(ChatFormatting.GRAY));
        if (mode == MODE_REALITY_CUT) {
            tooltip.add(Component.translatable("message.soul_sword.rift_dim", Component.translatable(SoulSwordAbilities.dimNameKey(getRiftDim(stack)))).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
