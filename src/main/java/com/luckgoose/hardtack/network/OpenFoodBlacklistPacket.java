package com.luckgoose.hardtack.network;

import com.luckgoose.hardtack.init.ModItems;
import com.luckgoose.hardtack.menu.GooseFoodBlacklistMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

/**
 * 打开黑名单配置界面网络数据包（客户端 → 服务端）
 * 
 * <p>玩家 Shift+右键饼干时，客户端发送此包请求服务端打开黑名单配置界面。
 * 
 * <p><b>数据包结构：</b>
 * <pre>
 * - slot (VarInt): 饼干所在的背包槽位索引
 * </pre>
 * 
 * <p><b>工作流程：</b>
 * <ol>
 *   <li>玩家 Shift+右键饼干</li>
 *   <li>{@link com.luckgoose.hardtack.item.GooseFoodBagItem#use} 判断是潜行状态</li>
 *   <li>客户端发送此数据包到服务端</li>
 *   <li>服务端验证槽位和物品</li>
 *   <li>使用 {@link NetworkHooks#openScreen} 打开容器界面</li>
 *   <li>客户端接收并显示 {@link GooseFoodBlacklistMenu}</li>
 * </ol>
 * 
 * <p><b>安全检查：</b>
 * <ul>
 *   <li>槽位范围检查：0 ≤ slot < 背包大小</li>
 *   <li>物品类型检查：必须是饼干物品</li>
 *   <li>重复打开检查：如果已打开同一饼干的界面则忽略</li>
 * </ul>
 * 
 * @author LuckGoose
 * @see GooseFoodBlacklistMenu 黑名单容器菜单
 * @see com.luckgoose.hardtack.item.GooseFoodBagItem#use 触发入口
 * @since 1.0.0
 */
public class OpenFoodBlacklistPacket {

    /** 饼干所在的槽位索引 */
    private final int slot;

    /**
     * 构造打开黑名单数据包
     * 
     * @param slot 饼干所在的背包槽位索引
     */
    public OpenFoodBlacklistPacket(int slot) {
        this.slot = slot;
    }

    /**
     * 序列化数据包到网络缓冲区
     * 
     * @param msg 数据包实例
     * @param buf 网络缓冲区
     */
    public static void encode(OpenFoodBlacklistPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.slot);
    }

    /**
     * 从网络缓冲区反序列化数据包
     * 
     * @param buf 网络缓冲区
     * @return 数据包实例
     */
    public static OpenFoodBlacklistPacket decode(FriendlyByteBuf buf) {
        return new OpenFoodBlacklistPacket(buf.readVarInt());
    }

    /**
     * 处理接收到的数据包
     * 
     * <p>在服务端主线程中执行，验证数据并打开容器界面。
     * 
     * @param msg 数据包实例
     * @param ctx 网络上下文
     */
    public static void handle(OpenFoodBlacklistPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            open(player, msg.slot);
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * 打开黑名单配置界面
     * 
     * <p><b>验证流程：</b>
     * <ol>
     *   <li>检查槽位范围：0 ≤ slot < 背包大小</li>
     *   <li>检查物品类型：必须是饼干</li>
     *   <li>检查重复打开：如果已打开同一饼干的界面则忽略</li>
     *   <li>关闭旧界面（如果打开的是不同饼干）</li>
     *   <li>使用 {@link NetworkHooks#openScreen} 打开新界面</li>
     * </ol>
     * 
     * @param player 服务端玩家
     * @param slot 饼干所在的槽位索引
     */
    public static void open(ServerPlayer player, int slot) {
        // 验证槽位范围
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) return;
        
        ItemStack stack = getFoodBag(player, slot);
        if (stack.isEmpty()) return;
        
        // 防止重复打开同一饼干的界面
        if (player.containerMenu instanceof GooseFoodBlacklistMenu menu) {
            if (menu.getBagSlot() == slot) return;
            player.doCloseContainer();
        }
        
        // 打开容器界面
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (containerId, inventory, p) -> new GooseFoodBlacklistMenu(containerId, inventory, slot),
                Component.translatable("container.goose_hardtack.goose_hardtack_blacklist")
        ), buf -> buf.writeVarInt(slot));
    }

    /**
     * 获取指定槽位的饼干物品
     * 
     * @param player 服务端玩家
     * @param slot 槽位索引
     * @return 饼干物品，如果不是饼干返回 EMPTY
     */
    private static ItemStack getFoodBag(ServerPlayer player, int slot) {
        ItemStack stack = player.getInventory().getItem(slot);
        return stack.is(ModItems.GOOSE_HARDTACK.get()) ? stack : ItemStack.EMPTY;
    }
}
