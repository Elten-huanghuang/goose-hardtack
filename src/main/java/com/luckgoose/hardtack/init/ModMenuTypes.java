package com.luckgoose.hardtack.init;

import com.luckgoose.hardtack.GooseHardtackMod;
import com.luckgoose.hardtack.menu.GooseFoodBlacklistMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 容器菜单类型注册器
 * 
 * <p>注册 mod 中的所有容器菜单类型到 Forge 注册表。
 * 
 * @author LuckGoose
 * @see GooseFoodBlacklistMenu 黑名单配置菜单
 * @since 1.0.0
 */
public class ModMenuTypes {

    /** 容器菜单类型延迟注册器 */
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, GooseHardtackMod.MOD_ID);

    /** 黑名单配置容器菜单类型 */
    public static final RegistryObject<MenuType<GooseFoodBlacklistMenu>> GOOSE_HARDTACK_BLACKLIST =
            MENU_TYPES.register("goose_hardtack_blacklist", () ->
                    IForgeMenuType.create(GooseFoodBlacklistMenu::new));

    /**
     * 注册所有容器菜单类型到事件总线
     * 
     * @param eventBus Mod 事件总线
     */
    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}
