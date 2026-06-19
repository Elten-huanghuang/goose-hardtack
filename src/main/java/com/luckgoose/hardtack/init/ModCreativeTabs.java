package com.luckgoose.hardtack.init;

import com.luckgoose.hardtack.GooseHardtackMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 创造模式标签页注册器
 * 
 * <p>注册 mod 的创造模式物品栏标签页。
 * 
 * @author LuckGoose
 * @since 1.0.0
 */
public class ModCreativeTabs {

    /** 创造模式标签页延迟注册器 */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GooseHardtackMod.MOD_ID);

    /** 主标签页（包含大鹅的压缩饼干） */
    public static final RegistryObject<CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.goose_hardtack.main"))
            .icon(() -> new ItemStack(ModItems.GOOSE_HARDTACK.get()))
            .displayItems((parameters, output) -> output.accept(ModItems.GOOSE_HARDTACK.get()))
            .withTabsBefore(new ResourceLocation("minecraft", "spawn_eggs"))
            .build());

    /**
     * 注册所有创造模式标签页到事件总线
     * 
     * @param eventBus Mod 事件总线
     */
    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
