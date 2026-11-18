package com.soulsword.event;

import com.soulsword.SoulSwordMod;
import com.soulsword.crater.CraterManager;
import com.soulsword.item.SoulMath;
import com.soulsword.item.SoulSwordItem;
import com.soulsword.registry.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = SoulSwordMod.MODID)
public final class SoulSwordEvents {
    private SoulSwordEvents() {}

    /** +1 soul per mob killed in melee, +50 for bosses. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide || !(dead instanceof Mob) || !(dead.level() instanceof ServerLevel level)) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player) return;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof SoulSwordItem)) return;

        boolean boss = dead instanceof EnderDragon || dead instanceof WitherBoss || dead.getType().is(Tags.EntityTypes.BOSSES);
        SoulSwordItem.addSouls(held, boss ? 50L : 1L);

        level.sendParticles(ParticleTypes.SOUL, dead.getX(), dead.getY() + dead.getBbHeight() * 0.5D, dead.getZ(), boss ? 40 : 6, 0.3D, 0.4D, 0.3D, 0.05D);
        level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.5F, boss ? 0.5F : 1.4F);
    }

    /** Infinite melee scaling: +25.0 attack damage per 1,000,000 souls, uncapped. */
    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SoulSwordItem)) return;
        double bonus = SoulMath.bonusDamage(SoulSwordItem.getSouls(stack));
        if (bonus <= 0.0D) return;
        event.addModifier(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(SoulSwordMod.id("soul_bonus"), bonus, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND);
    }

    /** Soul Sever blocks every form of healing. */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity().hasEffect(ModEffects.SOUL_SEVER)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        CraterManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CraterManager.clear();
    }
}
