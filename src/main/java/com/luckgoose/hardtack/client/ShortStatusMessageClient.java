package com.luckgoose.hardtack.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 短消息客户端渲染器
 * 
 * <p>在物品栏上方显示短暂的状态消息（类似副标题），用于提示食物收集/归还结果。
 * 
 * <p><b>显示位置：</b>
 * 屏幕中下方（物品栏上方 68 像素），水平居中。
 * 
 * <p><b>显示效果：</b>
 * <ul>
 *   <li>正常状态：白色文字，完全不透明</li>
 *   <li>淡出阶段：最后 10 ticks 逐渐透明（alpha 从 255 → 0）</li>
 * </ul>
 * 
 * <p><b>使用方式：</b>
 * <pre>
 * // 服务端发送消息包
 * ModNetwork.CHANNEL.send(
 *     PacketDistributor.PLAYER.with(() -> player),
 *     new ShortStatusMessagePacket(message, 35)
 * );
 * 
 * // 客户端接收后调用
 * ShortStatusMessageClient.show(message, 35);
 * </pre>
 * 
 * @author LuckGoose
 * @see com.luckgoose.hardtack.network.ShortStatusMessagePacket 网络包
 * @see com.luckgoose.hardtack.item.GooseFoodBagItem#sendShortMessage 发送消息
 * @since 1.0.0
 */
public final class ShortStatusMessageClient {

    /** 当前显示的消息 */
    private static Component message = Component.empty();
    
    /** 剩余显示时间（ticks） */
    private static int ticks;
    
    /** 最大显示时间（ticks，用于计算淡出） */
    private static int maxTicks;

    private ShortStatusMessageClient() {
    }

    /**
     * 显示消息
     * 
     * <p>如果已有消息正在显示，会被新消息覆盖。
     * 
     * @param component 要显示的消息（null 会被转为空文本）
     * @param durationTicks 显示时长（ticks，20 ticks = 1 秒）
     */
    public static void show(Component component, int durationTicks) {
        message = component == null ? Component.empty() : component;
        ticks = Math.max(0, durationTicks);
        maxTicks = ticks;
    }

    /**
     * 渲染消息到屏幕
     * 
     * <p>在 {@link ClientForgeEvents#onRenderGui} 中调用。
     * 
     * <p>渲染细节：
     * <ul>
     *   <li>位置：屏幕水平居中，Y = 屏幕高度 - 68</li>
     *   <li>颜色：白色（RGB: 0xFFFFFF）</li>
     *   <li>透明度：最后 10 ticks 淡出（alpha = ticks × 25）</li>
     *   <li>阴影：启用文字阴影</li>
     * </ul>
     * 
     * @param graphics GUI 图形上下文
     */
    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || ticks <= 0 || message.getString().isEmpty()) return;
        
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        
        // 最后 10 ticks 淡出效果
        int alpha = ticks < 10 ? Mth.clamp(ticks * 25, 0, 255) : 255;
        
        // 居中显示
        int textWidth = minecraft.font.width(message);
        int x = (width - textWidth) / 2;
        int y = height - 68;
        
        graphics.drawString(minecraft.font, message, x, y, 0xFFFFFF | (alpha << 24), true);
    }

    /**
     * 每客户端 tick 调用
     * 
     * <p>减少剩余显示时间，时间到达 0 时清空消息。
     * 在 {@link ClientForgeEvents#onClientTick} 中调用。
     */
    public static void tick() {
        if (ticks > 0) ticks--;
        if (ticks == 0 && maxTicks > 0) {
            message = Component.empty();
            maxTicks = 0;
        }
    }
}
