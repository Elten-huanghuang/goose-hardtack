package com.luckgoose.hardtack.event;

import com.luckgoose.hardtack.init.ModItems;
import com.luckgoose.hardtack.item.GooseFoodBagItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 压缩饼干 Forge 事件监听器
 * 
 * <p>处理玩家与饼干的交互事件，实现左键与存储容器交互的功能。
 * 
 * @author LuckGoose
 * @see GooseFoodBagItem#interactWithStorage 存储交互处理
 * @since 1.0.0
 */
public class GooseFoodBagEvents {

    /**
     * 左键方块事件处理（最高优先级）
     * 
     * <p>当玩家手持饼干左键方块时：
     * <ul>
     *   <li>取消破坏方块行为</li>
     *   <li>在服务端调用 {@link GooseFoodBagItem#interactWithStorage}</li>
     *   <li>收集食物或归还食物</li>
     * </ul>
     * 
     * <p><b>优先级：HIGHEST</b> - 确保在其他 mod 之前处理，防止误触。
     * 
     * @param event 左键方块事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (stack.is(ModItems.GOOSE_HARDTACK.get())) {
            if (!player.level().isClientSide) {
                GooseFoodBagItem.interactWithStorage(stack, player.level(), event.getPos(), event.getFace(), player);
            }
            event.setCanceled(true);
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
        }
    }
}
