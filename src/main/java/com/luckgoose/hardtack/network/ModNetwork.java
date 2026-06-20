package com.luckgoose.hardtack.network;

import com.luckgoose.hardtack.GooseHardtackMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * 网络通信管理器
 * 
 * <p>负责注册和管理客户端-服务端之间的网络数据包通信。
 * 
 * <p><b>注册的数据包：</b>
 * <ul>
 *   <li><b>ShortStatusMessagePacket</b>：服务端 → 客户端，显示短消息（收集/归还提示）</li>
 *   <li><b>OpenFoodBlacklistPacket</b>：客户端 → 服务端，请求打开黑名单配置界面</li>
 *   <li><b>FoodBlacklistMarkPacket</b>：客户端 → 服务端，同步黑名单配置</li>
 * </ul>
 * 
 * <p><b>协议版本：</b>
 * 使用版本号 "1" 进行客户端-服务端兼容性检查。
 * 客户端和服务端的协议版本必须完全匹配才能通信。
 * 
 * @author LuckGoose
 * @see ShortStatusMessagePacket 状态消息包
 * @see OpenFoodBlacklistPacket 打开黑名单包
 * @see FoodBlacklistMarkPacket 黑名单标记包
 * @since 1.0.0
 */
public class ModNetwork {

    /** 网络协议版本号（用于客户端-服务端兼容性检查） */
    private static final String PROTOCOL_VERSION = "1";

    /** 
     * 网络通信通道
     * 
     * <p>所有数据包通过此通道发送和接收。
     * 通道名称：{@code goose_hardtack:main}
     */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GooseHardtackMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    /**
     * 注册所有网络数据包
     * 
     * <p>按顺序注册：
     * <ol>
     *   <li>ID 0: ShortStatusMessagePacket（服务端 → 客户端）</li>
     *   <li>ID 1: OpenFoodBlacklistPacket（客户端 → 服务端）</li>
     *   <li>ID 2: FoodBlacklistMarkPacket（客户端 → 服务端）</li>
     * </ol>
     * 
     * <p>在 {@link com.luckgoose.hardtack.GooseHardtackMod} 构造函数中调用。
     */
    public static void register() {
        int id = 0;
        
        // 服务端 → 客户端：显示短消息
        CHANNEL.registerMessage(id++, ShortStatusMessagePacket.class,
                ShortStatusMessagePacket::encode,
                ShortStatusMessagePacket::decode,
                ShortStatusMessagePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        
        // 客户端 → 服务端：打开黑名单界面
        CHANNEL.registerMessage(id++, OpenFoodBlacklistPacket.class,
                OpenFoodBlacklistPacket::encode,
                OpenFoodBlacklistPacket::decode,
                OpenFoodBlacklistPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        
        // 客户端 → 服务端：同步黑名单配置
        CHANNEL.registerMessage(id++, FoodBlacklistMarkPacket.class,
                FoodBlacklistMarkPacket::encode,
                FoodBlacklistMarkPacket::decode,
                FoodBlacklistMarkPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
