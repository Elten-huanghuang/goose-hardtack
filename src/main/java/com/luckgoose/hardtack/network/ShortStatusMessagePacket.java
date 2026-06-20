package com.luckgoose.hardtack.network;

import com.luckgoose.hardtack.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 短消息网络数据包（服务端 → 客户端）
 * 
 * <p>服务端发送此包通知客户端在物品栏上方显示短暂的状态消息。
 * 
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>食物收集成功：显示"成功收集到 N 种未品尝过的食物"</li>
 *   <li>食物归还成功：显示"已成功归还 N 种食物"</li>
 *   <li>操作失败：显示失败原因（网络不可用、未找到食物等）</li>
 * </ul>
 * 
 * <p><b>数据包结构：</b>
 * <pre>
 * - message (Component): 要显示的消息文本
 * - durationTicks (VarInt): 显示时长（ticks，20 ticks = 1 秒）
 * </pre>
 * 
 * <p><b>工作流程：</b>
 * <ol>
 *   <li>服务端创建数据包：{@code new ShortStatusMessagePacket(message, 35)}</li>
 *   <li>通过 {@link ModNetwork#CHANNEL} 发送到客户端</li>
 *   <li>客户端接收后调用 {@link ClientPacketHandlers#handleShortStatusMessage}</li>
 *   <li>最终调用 {@link com.luckgoose.hardtack.client.ShortStatusMessageClient#show}</li>
 * </ol>
 * 
 * @author LuckGoose
 * @see com.luckgoose.hardtack.client.ShortStatusMessageClient 客户端渲染
 * @see com.luckgoose.hardtack.item.GooseFoodBagItem#sendShortMessage 发送入口
 * @since 1.0.0
 */
public class ShortStatusMessagePacket {

    /** 要显示的消息文本 */
    private final Component message;
    
    /** 显示时长（ticks） */
    private final int durationTicks;

    /**
     * 构造短消息数据包
     * 
     * @param message 要显示的消息
     * @param durationTicks 显示时长（ticks，20 ticks = 1 秒）
     */
    public ShortStatusMessagePacket(Component message, int durationTicks) {
        this.message = message;
        this.durationTicks = durationTicks;
    }

    /**
     * 序列化数据包到网络缓冲区
     * 
     * @param msg 数据包实例
     * @param buf 网络缓冲区
     */
    public static void encode(ShortStatusMessagePacket msg, FriendlyByteBuf buf) {
        buf.writeComponent(msg.message);
        buf.writeVarInt(msg.durationTicks);
    }

    /**
     * 从网络缓冲区反序列化数据包
     * 
     * @param buf 网络缓冲区
     * @return 数据包实例
     */
    public static ShortStatusMessagePacket decode(FriendlyByteBuf buf) {
        return new ShortStatusMessagePacket(buf.readComponent(), buf.readVarInt());
    }

    /**
     * 处理接收到的数据包
     * 
     * <p>使用 {@link DistExecutor#unsafeRunWhenOn} 确保客户端代码只在客户端执行。
     * 
     * @param msg 数据包实例
     * @param ctx 网络上下文
     */
    public static void handle(ShortStatusMessagePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleShortStatusMessage(msg.message, msg.durationTicks)));
        ctx.get().setPacketHandled(true);
    }
}
