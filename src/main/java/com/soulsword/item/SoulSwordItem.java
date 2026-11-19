package com.soulsword.item;

import com.soulsword.ability.SoulSwordAbilities;
import com.soulsword.registry.ModComponents;
import com.soulsword.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.HumanoidArm;
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
    /** Hard cap on stored souls. */
    public static final long MAX_SOULS = 100_000L;

    public static final int MODE_RUPTURE = 0;
    public static final int MODE_REALITY_CUT = 1;
    public static final int MODE_ANNIHILATION = 2;

    public static final int HIT_COST = 250;
    public static final int ANNIHILATION_MIN = 100;
    public static final int CHARGE_TICKS = 20;
    /** Ticks until the charge animation / audio / spiral reach full intensity. */
    public static final int FULL_CHARGE_TICKS = 60;
    public static final int CAST_LOCK_TICKS = 40;
    public static final int SEVER_TICKS = 200;
    private static final int HOLD_TICKS = 72000;

    public SoulSwordItem(Properties properties) {
        super(Tiers.NETHERITE, properties);
    }

    // ---------------------------------------------------------------- data helpers

    public static long getSouls(ItemStack stack) {
        return SoulMath.clamp(stack.getOrDefault(ModComponents.SOULS.get(), 0L));
    }

    public static void setSouls(ItemStack stack, long souls) {
        stack.set(ModComponents.SOULS.get(), SoulMath.clamp(souls));
    }

    /** Adds souls, saturating at {@link #MAX_SOULS}. */
    public static void addSouls(ItemStack stack, long amount) {
        setSouls(stack, getSouls(stack) + amount);
    }

    public static int getMode(ItemStack stack) {
        return Math.floorMod(stack.getOrDefault(ModComponents.MODE.get(), 0), 3);
    }

    public static int getRiftDim(ItemStack stack) {
        return Math.floorMod(stack.getOrDefault(ModComponents.RIFT_DIM.get(), 0), 3);
    }

    /** True while the player holds right-click with a Soul Sword (vanilla use state, synced to every client). */
    public static boolean isActive(Player player) {
        return player.isUsingItem() && player.getUseItem().getItem() instanceof SoulSwordItem;
    }

    /** Reality Cut: held long enough to be charged. */
    public static boolean isCharged(Player player) {
        return isActive(player) && player.getTicksUsingItem() >= CHARGE_TICKS;
    }

    /** True while the blade should burn bright (used by both logic and renderer). */
    public static boolean isEmpowered(Player player, ItemStack stack) {
        int mode = getMode(stack);
        if (mode == MODE_RUPTURE) return isActive(player);
        if (mode == MODE_REALITY_CUT) return isCharged(player);
        return false;
    }

    /** 0..1 charge animation progress of a player currently holding right-click. */
    public static float chargeProgress(Player player, float partialTick) {
        if (!isActive(player)) return 0.0F;
        return Mth.clamp((player.getTicksUsingItem() + partialTick) / FULL_CHARGE_TICKS, 0.0F, 1.0F);
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

        // Soul Rupture / Reality Cut: holding right-click is the charge. Left-clicks while holding are forwarded
        // to the server by the client (vanilla swallows attacks during item use), see SwordStrikePayload.
        player.startUsingItem(hand);
        if (!level.isClientSide && mode == MODE_RUPTURE) {
            level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
            player.displayClientMessage(Component.translatable("message.soul_sword.empowered").withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseDuration) {
        int mode = getMode(stack);
        if (mode == MODE_ANNIHILATION) return;
        int elapsed = getUseDuration(stack, entity) - remainingUseDuration;
        float charge = Mth.clamp(elapsed / (float) FULL_CHARGE_TICKS, 0.0F, 1.0F);

        if (level.isClientSide) {
            spiral(level, user, elapsed, charge);
            return;
        }
        if (elapsed % 5 == 0) {
            level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS,
                    0.5F + 0.5F * charge, 0.5F + 1.5F * charge); // pitch rises while charging
        }
        if (elapsed == CHARGE_TICKS) {
            level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.3F);
            if (mode == MODE_REALITY_CUT && user instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.soul_sword.charged").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
    }

    /** Prismatic soul energy spiralling up the raised blade. */
    private static void spiral(Level level, LivingEntity user, int elapsed, float charge) {
        double yaw = Math.toRadians(user.getYRot());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double side = user.getMainArm() == HumanoidArm.RIGHT ? 1.0D : -1.0D;
        double bx = user.getX() + rx * 0.4D * side + fx * 0.35D;
        double bz = user.getZ() + rz * 0.4D * side + fz * 0.35D;
        double by = user.getY() + 1.0D;
        int count = 1 + (int) (3.0F * charge);
        for (int i = 0; i < count; i++) {
            double h = ((elapsed * 0.21D + i * 0.37D) % 1.0D) * (1.2D + 1.6D * charge);
            double angle = h * 7.0D + elapsed * 0.5D + i * 2.1D;
            double radius = 0.30D - 0.05D * h;
            int rgb = Mth.hsvToRgb((float) ((h * 0.35D + elapsed * 0.02D) % 1.0D), 0.7F, 1.0F);
            level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | rgb),
                    bx + Math.cos(angle) * radius, by + h, bz + Math.sin(angle) * radius, 0.0D, 0.04D, 0.0D);
            if (charge > 0.5F && i == 0) {
                level.addParticle(ParticleTypes.END_ROD, bx + Math.cos(angle) * radius, by + h, bz + Math.sin(angle) * radius, 0.0D, 0.03D, 0.0D);
            }
        }
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
        return UseAnim.SPEAR; // third person: arm raised high; first person is replaced by applyForgeHandTransform
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return getMode(stack) == MODE_ANNIHILATION ? CAST_LOCK_TICKS : HOLD_TICKS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getSouls(stack) > 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        long souls = getSouls(stack);
        int mode = getMode(stack);
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        tooltip.add(Component.translatable("item.soul_sword.soul_sword.subtitle").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.soul_sword.souls",
                Component.literal(nf.format(souls)).withStyle(ChatFormatting.AQUA),
                Component.literal(nf.format(MAX_SOULS)).withStyle(ChatFormatting.DARK_AQUA)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.soul_sword.bonus", Component.literal(String.format(Locale.US, "%.2f", SoulMath.bonusDamage(souls))).withStyle(ChatFormatting.RED)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.soul_sword.mode", Component.translatable("mode.soul_sword." + mode).withStyle(modeColor(mode))).withStyle(ChatFormatting.GRAY));
        if (mode == MODE_REALITY_CUT) {
            tooltip.add(Component.translatable("message.soul_sword.rift_dim", Component.translatable(SoulSwordAbilities.dimNameKey(getRiftDim(stack)))).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
