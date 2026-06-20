package com.luckgoose.hardtack.event;

import com.luckgoose.hardtack.init.ModItems;
import com.luckgoose.hardtack.item.GooseFoodBagItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    /** 两次左键交互之间的最小冷却时间（ticks），防止快速连点导致收集→立即归还 */
    private static final int INTERACT_COOLDOWN_TICKS = 8;
    private static final Map<UUID, Integer> LAST_INTERACT_TICK = new ConcurrentHashMap<>();

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
     * <p><b>冷却机制：</b>两次成功交互之间至少有 8 tick 冷却，
     * 防止快速连点导致收集后立刻归还。
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
            event.setCanceled(true);
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);

            if (!player.level().isClientSide) {
                UUID id = player.getUUID();
                int currentTick = player.tickCount;
                Integer lastTick = LAST_INTERACT_TICK.get(id);
                if (lastTick != null && currentTick - lastTick < INTERACT_COOLDOWN_TICKS) {
                    return;
                }
                if (GooseFoodBagItem.interactWithStorage(stack, player.level(), event.getPos(), event.getFace(), player)) {
                    LAST_INTERACT_TICK.put(id, currentTick);
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_INTERACT_TICK.remove(event.getEntity().getUUID());
    }
}
