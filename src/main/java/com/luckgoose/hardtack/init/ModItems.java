package com.luckgoose.hardtack.init;

import com.luckgoose.hardtack.GooseHardtackMod;
import com.luckgoose.hardtack.item.GooseFoodBagItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 物品注册器
 * 
 * <p>注册 mod 中的所有物品到 Forge 注册表。
 * 
 * @author LuckGoose
 * @see GooseFoodBagItem 大鹅的压缩饼干物品
 * @since 1.0.0
 */
public class ModItems {

    /** 物品延迟注册器 */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, GooseHardtackMod.MOD_ID);

    /** 大鹅的压缩饼干物品（不可堆叠） */
    public static final RegistryObject<Item> GOOSE_HARDTACK = ITEMS.register("goose_hardtack",
            () -> new GooseFoodBagItem(new Item.Properties().stacksTo(1)));

    /**
     * 注册所有物品到事件总线
     * 
     * @param eventBus Mod 事件总线
     */
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
