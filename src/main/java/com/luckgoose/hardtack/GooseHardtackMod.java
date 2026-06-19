package com.luckgoose.hardtack;

import com.luckgoose.hardtack.config.HardtackConfig;
import com.luckgoose.hardtack.event.GooseFoodBagEvents;
import com.luckgoose.hardtack.init.ModCreativeTabs;
import com.luckgoose.hardtack.init.ModItems;
import com.luckgoose.hardtack.init.ModMenuTypes;
import com.luckgoose.hardtack.network.ModNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * 大鹅的压缩饼干 Mod 主类
 * 
 * <p>提供一个可以收集、存储和批量进食未品尝食物的魔法物品，
 * 配合 Spice of Life: Carrot Edition 解锁食物里程碑。
 * 
 * <p><b>核心功能：</b>
 * <ul>
 *   <li>左键容器：收集/归还未品尝的食物</li>
 *   <li>右键长按 3 秒：自动批量进食</li>
 *   <li>Shift+右键：配置收集黑名单</li>
 * </ul>
 * 
 * @author LuckGoose
 * @version 1.0.1
 * @since 1.0.0
 */
@Mod(GooseHardtackMod.MOD_ID)
public class GooseHardtackMod {

    /** Mod ID，用于所有资源注册和命名空间 */
    public static final String MOD_ID = "goose_hardtack";
    
    /** 全局日志记录器，用于调试和错误追踪 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Mod 构造函数，在 Forge 加载阶段调用
     * 
     * <p>按顺序注册：
     * <ol>
     *   <li>物品和创造标签页</li>
     *   <li>容器菜单类型</li>
     *   <li>网络通信通道</li>
     *   <li>配置文件</li>
     *   <li>Forge 事件监听器</li>
     * </ol>
     */
    public GooseHardtackMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        LOGGER.info("Initializing Goose Hardtack mod...");

        // 注册游戏内容
        ModItems.register(modBus);
        ModMenuTypes.register(modBus);
        ModCreativeTabs.register(modBus);
        
        // 注册网络通信
        ModNetwork.register();
        
        // 注册配置文件（位于 serverconfig/goose/goose-hardtack.toml）
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, HardtackConfig.SERVER_SPEC, "goose/goose-hardtack.toml");

        // 注册 Forge 游戏事件
        MinecraftForge.EVENT_BUS.register(new GooseFoodBagEvents());

        LOGGER.info("Goose Hardtack mod initialized successfully");
    }
}
