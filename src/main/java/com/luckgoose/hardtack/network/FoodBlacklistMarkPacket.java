package com.luckgoose.hardtack.network;

import com.luckgoose.hardtack.item.GooseFoodBagItem;
import com.luckgoose.hardtack.menu.GooseFoodBlacklistMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 黑名单标记网络数据包（客户端 → 服务端）
 * 
 * <p>在黑名单配置界面中，玩家点击槽位标记/取消标记食物时，客户端发送此包同步到服务端。
 * 
 * <p><b>数据包结构：</b>
 * <pre>
 * - slot (VarInt): 黑名单槽位索引（0-26）
 * - stack (ItemStack): 要标记的食物（EMPTY 表示清空该槽位）
 * </pre>
 * 
 * <p><b>工作流程：</b>
 * <ol>
 *   <li>玩家在黑名单界面点击槽位</li>
 *   <li>{@link GooseFoodBlacklistMenu#clicked} 处理点击逻辑</li>
 *   <li>客户端发送此数据包到服务端</li>
 *   <li>服务端验证数据（槽位、物品类型、NBT、菜单状态）</li>
 *   <li>调用 {@link GooseFoodBlacklistMenu#markFood} 更新黑名单</li>
 * </ol>
 * 
 * <p><b>安全验证（5 层防护）：</b>
 * <ol>
 *   <li><b>槽位验证</b>：0 ≤ slot < 27（防止越界攻击）</li>
 *   <li><b>物品类型验证</b>：必须是可食用物品（防止恶意物品）</li>
 *   <li><b>NBT 清理</b>：强制清除所有 NBT（防止 NBT 注入攻击）</li>
 *   <li><b>数量强制</b>：强制数量为 1（防止数量异常）</li>
 *   <li><b>菜单验证</b>：玩家必须打开黑名单界面（防止伪造请求）</li>
 * </ol>
 * 
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>构造函数不抛异常：避免网络解码线程崩溃导致断连</li>
 *   <li>验证逻辑在 handle() 中：统一处理非法值，安全拒绝</li>
 *   <li>自动清理：数量和 NBT 在构造时自动规范化</li>
 * </ul>
 * 
 * @author LuckGoose
 * @see GooseFoodBlacklistMenu 黑名单容器菜单
 * @see com.luckgoose.hardtack.item.GooseFoodBagItem#setFoodBlacklistEntry 黑名单更新
 * @since 1.0.0
 */
public class FoodBlacklistMarkPacket {

    /** 黑名单槽位索引（0-26） */
    private final int slot;
    
    /** 要标记的食物（EMPTY 表示清空该槽位） */
    private final ItemStack stack;

    /**
     * 构造黑名单标记数据包
     * 
     * <p><b>自动规范化：</b>
     * <ul>
     *   <li>创建物品副本（防止引用共享）</li>
     *   <li>强制数量为 1</li>
     *   <li>清除所有 NBT 数据</li>
     * </ul>
     * 
     * <p><b>注意：</b>不在构造函数抛异常，避免网络解码崩溃。
     * 所有非法值的拒绝逻辑在 {@link #handle} 中处理。
     * 
     * @param slot 黑名单槽位索引
     * @param stack 要标记的食物
     */
    public FoodBlacklistMarkPacket(int slot, ItemStack stack) {
        this.slot = slot;
        this.stack = stack.copy();
        
        // 自动规范化：强制数量为 1
        this.stack.setCount(1);
        
        // 自动清理：移除所有 NBT（防止恶意 NBT）
        if (this.stack.hasTag()) {
            this.stack.setTag(null);
        }
    }

    /**
     * 序列化数据包到网络缓冲区
     * 
     * @param msg 数据包实例
     * @param buf 网络缓冲区
     */
    public static void encode(FoodBlacklistMarkPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.slot);
        buf.writeItem(msg.stack);
    }

    /**
     * 从网络缓冲区反序列化数据包
     * 
     * @param buf 网络缓冲区
     * @return 数据包实例
     */
    public static FoodBlacklistMarkPacket decode(FriendlyByteBuf buf) {
        return new FoodBlacklistMarkPacket(buf.readVarInt(), buf.readItem());
    }

    /**
     * 处理接收到的数据包
     * 
     * <p><b>验证流程（5 层防护）：</b>
     * <ol>
     *   <li>验证槽位范围：0 ≤ slot < 27</li>
     *   <li>验证物品类型：必须是可食用物品</li>
     *   <li>验证 NBT：强制清除所有 NBT</li>
     *   <li>验证数量：强制数量为 1</li>
     *   <li>验证菜单：玩家必须打开黑名单界面</li>
     * </ol>
     * 
     * <p><b>安全设计：</b>
     * 所有验证失败时静默拒绝（不发送错误消息），
     * 防止恶意客户端通过错误消息探测服务端状态。
     * 
     * @param msg 数据包实例
     * @param ctx 网络上下文
     */
    public static void handle(FoodBlacklistMarkPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            
            // 验证 1：槽位范围检查（防止越界攻击）
            if (msg.slot < 0 || msg.slot >= GooseFoodBagItem.FOOD_BLACKLIST_SLOTS) return;
            
            // 验证 2：物品类型检查（防止恶意物品类型）
            if (!msg.stack.isEmpty() && !msg.stack.isEdible()) {
                // 静默拒绝，不发送错误消息（防止探测）
                return;
            }
            
            // 验证 3：强制清理 NBT（防止 NBT 注入攻击）
            if (msg.stack.hasTag()) {
                msg.stack.setTag(null);
            }
            
            // 验证 4：强制数量为 1（防止数量异常）
            if (!msg.stack.isEmpty() && msg.stack.getCount() != 1) {
                msg.stack.setCount(1);
            }
            
            // 验证 5：菜单验证（防止伪造请求）
            if (!(player.containerMenu instanceof GooseFoodBlacklistMenu menu)) return;
            
            // 所有验证通过，执行操作
            menu.markFood(msg.slot, msg.stack);
        });
        ctx.get().setPacketHandled(true);
    }
}
