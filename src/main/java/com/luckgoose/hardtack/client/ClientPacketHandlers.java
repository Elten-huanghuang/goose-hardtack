package com.luckgoose.hardtack.client;

import net.minecraft.network.chat.Component;

/**
 * 客户端网络包处理器
 * 
 * <p>处理服务端发送到客户端的网络数据包。
 * 所有方法必须在客户端环境中调用（通过 {@link net.minecraftforge.fml.DistExecutor}）。
 * 
 * @author LuckGoose
 * @see com.luckgoose.hardtack.network.ShortStatusMessagePacket 短消息数据包
 * @since 1.0.0
 */
public final class ClientPacketHandlers {

    private ClientPacketHandlers() {
    }

    /**
     * 处理短消息数据包
     * 
     * <p>在客户端显示短暂的状态消息（物品栏上方）。
     * 
     * @param message 要显示的消息
     * @param durationTicks 显示时长（ticks，20 ticks = 1 秒）
     */
    public static void handleShortStatusMessage(Component message, int durationTicks) {
        ShortStatusMessageClient.show(message, durationTicks);
    }
}
