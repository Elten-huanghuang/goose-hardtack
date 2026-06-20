package com.luckgoose.hardtack.client;

import com.luckgoose.hardtack.GooseHardtackMod;
import com.luckgoose.hardtack.client.screen.GooseFoodBlacklistScreen;
import com.luckgoose.hardtack.init.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端初始化设置
 * 
 * <p>在客户端启动时注册屏幕（Screen）与容器菜单（Menu）的绑定关系。
 * 
 * @author LuckGoose
 * @since 1.0.0
 */
@Mod.EventBusSubscriber(modid = GooseHardtackMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    /**
     * 客户端设置事件处理
     * 
     * <p>注册黑名单配置界面的屏幕工厂。
     * 
     * @param event 客户端设置事件
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenuTypes.GOOSE_HARDTACK_BLACKLIST.get(), GooseFoodBlacklistScreen::new));
    }
}
