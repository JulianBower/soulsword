package com.soulsword.registry;

import com.soulsword.SoulSwordMod;
import com.soulsword.item.SoulSwordItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.component.Unbreakable;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SoulSwordMod.MODID);

    public static final DeferredItem<SoulSwordItem> SOUL_SWORD = ITEMS.register("soul_sword", () ->
            new SoulSwordItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)
                    .fireResistant()
                    .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 3, -2.4F))
                    .component(DataComponents.UNBREAKABLE, new Unbreakable(false))
                    .component(ModComponents.SOULS.get(), 0L)
                    .component(ModComponents.MODE.get(), 0)
                    .component(ModComponents.RIFT_DIM.get(), 0)));

    private ModItems() {}
}
