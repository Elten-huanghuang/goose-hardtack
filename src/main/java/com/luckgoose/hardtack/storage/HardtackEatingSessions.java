package com.luckgoose.hardtack.storage;

import com.luckgoose.hardtack.GooseHardtackMod;
import com.luckgoose.hardtack.config.HardtackConfig;
import com.luckgoose.hardtack.init.ModItems;
import com.luckgoose.hardtack.item.GooseFoodBagItem;
import com.luckgoose.hardtack.network.ModNetwork;
import com.luckgoose.hardtack.network.ShortStatusMessagePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动进食会话管理器
 *
 * <p>当玩家完成饼干的进食动画后，此管理器会创建一个进食会话，
 * 在每个服务器 tick 中批量消费饼干中的食物，并触发原版的进食事件。
 *
 * <p><b>工作流程：</b>
 * <ol>
 *   <li>玩家长按右键 3 秒完成进食动画</li>
 *   <li>{@link com.luckgoose.hardtack.item.GooseFoodBagItem#finishUsingItem} 调用 {@link #start}</li>
 *   <li>每个 tick 在 {@link #onPlayerTick} 中消费若干食物</li>
 *   <li>每个食物调用 {@link #eat} 触发原版进食事件，SolCarrot 监听该事件记录进度</li>
 *   <li>饼干清空时发送完成消息并播放打嗝音效，会话结束</li>
 * </ol>
 *
 * <p><b>线程安全：</b>
 * <ul>
 *   <li>使用 {@link ConcurrentHashMap} 存储会话映射</li>
 *   <li>存储槽位索引和初始食物数（SessionInfo），而非 ItemStack 引用</li>
 *   <li>操作 NBT 时使用副本 + 同步块 + 原子替换模式</li>
 * </ul>
 *
 * <p><b>中断条件：</b>
 * <ul>
 *   <li>玩家切换维度（{@link #onPlayerChangedDimension}）</li>
 *   <li>玩家登出（{@link #onPlayerLoggedOut}）</li>
 *   <li>玩家死亡或重生（{@link #onPlayerClone}）</li>
 *   <li>饼干槽位被替换为其他物品（{@link #onPlayerTick} 中二次验证失败）</li>
 * </ul>
 *
 * <p><b>设计决策——为何不因受伤中断：</b>
 * 玩家进食期间受伤是常见情况（饥饿、摔落等），中断会话会导致饼干剩余食物未消费，
 * 玩家需要重新长按 3 秒才能继续。保留会话让食物继续消费，与 SolCarrot 的批量解锁
 * 设计一致——进食一旦开始就应完成。
 *
 * @author LuckGoose
 * @see HardtackFoodStorage 食物数据存储
 * @see com.luckgoose.hardtack.compat.SolCarrotCompat SolCarrot 兼容层
 * @since 1.0.0
 */
@Mod.EventBusSubscriber(modid = GooseHardtackMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HardtackEatingSessions {

    /**
     * 进食会话信息
     *
     * @param slot 饼干所在的背包槽位索引
     * @param foodCount 会话启动时饼干中的食物总数（用于完成消息显示）
     */
    private record SessionInfo(int slot, int foodCount) {
    }

    /**
     * 进食会话映射表
     *
     * <p>Key: 玩家 UUID<br>
     * Value: 会话信息（槽位 + 初始食物数）
     *
     * <p>使用 {@link ConcurrentHashMap} 防止多线程并发问题
     */
    private static final Map<UUID, SessionInfo> SESSIONS = new ConcurrentHashMap<>();

    /**
     * 累积使用计时（ticks），跨受击中断保留。
     * <p>原版受击调用 stopUsingItem 会重置物品使用进度。此计数器独立于原版计时，
     * 只要玩家在持续尝试使用饼干（受击后客户端立即重新发起使用），累积值就会递增。
     * 达到阈值后直接启动会话，不再依赖 finishUsingItem。
     */
    private static final Map<UUID, Integer> ACCUMULATED_USE = new ConcurrentHashMap<>();
    /** 使用中断超时（ticks）。超过此阈值视为玩家主动停止，清零累积。 */
    private static final int USE_TIMEOUT_TICKS = 10;
    private static final int AUTO_EAT_TRIGGER_TICKS = 60;
    private static final Map<UUID, Integer> USE_TIMEOUT = new ConcurrentHashMap<>();

    private HardtackEatingSessions() {
    }

    /**
     * 检查玩家是否正在进行进食会话
     * 
     * @param player 服务端玩家
     * @return 是否存在活跃的进食会话
     */
    public static boolean isRunning(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /**
     * 启动进食会话
     *
     * <p>如果饼干中有食物，则记录槽位和初始食物数并开始在每个 tick 中消费食物。
     * 初始食物数用于会话完成时的消息显示。
     *
     * @param player 服务端玩家
     * @param slot 饼干所在的槽位索引
     */
    public static void start(ServerPlayer player, int slot) {
        ItemStack bag = player.getInventory().getItem(slot);
        if (bag.isEmpty() || !bag.is(ModItems.GOOSE_HARDTACK.get())) return;
        int foodCount = HardtackFoodStorage.getFoodCount(bag);
        if (foodCount > 0) {
            UUID id = player.getUUID();
            SESSIONS.put(id, new SessionInfo(slot, foodCount));
            ACCUMULATED_USE.remove(id);
            USE_TIMEOUT.remove(id);
        }
    }

    /**
     * 停止进食会话
     * 
     * @param player 服务端玩家
     */
    public static void stop(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    /**
     * 累积使用计时（由 GooseFoodBagItem#onUseTick 每 tick 调用）。
     * 跨受击中断保留计数值，达到阈值后直接启动进食会话。
     */
    public static void accumulateUse(ServerPlayer player) {
        UUID id = player.getUUID();
        int ticks = ACCUMULATED_USE.merge(id, 1, Integer::sum);
        USE_TIMEOUT.put(id, 0);
        if (ticks >= AUTO_EAT_TRIGGER_TICKS) {
            ACCUMULATED_USE.remove(id);
            USE_TIMEOUT.remove(id);
            int slot = GooseFoodBagItem.findBagSlot(player, player.getUseItem());
            if (slot >= 0) start(player, slot);
        }
    }

    /** 重置累积使用计时（玩家主动停止时调用） */
    public static void resetAccumulatedUse(ServerPlayer player) {
        UUID id = player.getUUID();
        ACCUMULATED_USE.remove(id);
        USE_TIMEOUT.remove(id);
    }

    /**
     * 服务器玩家 Tick 事件处理
     *
     * <p>在每个 tick 的结束阶段，检查玩家是否有活跃的进食会话。
     * 如果有，则批量消费饼干中的食物并触发进食事件。
     *
     * <p><b>线程安全处理：</b>
     * <ol>
     *   <li>使用同步块获取 ItemStack 副本（避免并发修改 NBT）</li>
     *   <li>在副本上批量消费食物（一次 read + 一次 write，O(n)）</li>
     *   <li>使用同步块原子替换回背包（保证数据一致性）</li>
     * </ol>
     *
     * <p><b>性能控制：</b>
     * 每个 tick 消费的食物数量由配置 {@link HardtackConfig#FOODS_PER_TICK} 控制，
     * 设置为 -1 表示不限制。使用 {@link HardtackFoodStorage#consumeMultiple} 批量消费，
     * 避免在循环中逐次调用 consumeOne 导致的 O(n²) NBT 序列化开销。
     *
     * @param event 玩家 Tick 事件
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide || !(event.player instanceof ServerPlayer player)) return;

        // 累积使用超时检测：玩家持续不使用饼干超过阈值视为主动停止，清零累积
        UUID id = player.getUUID();
        if (ACCUMULATED_USE.containsKey(id)) {
            if (!player.isUsingItem() || !player.getUseItem().is(ModItems.GOOSE_HARDTACK.get())) {
                int timeout = USE_TIMEOUT.merge(id, 1, Integer::sum);
                if (timeout > USE_TIMEOUT_TICKS) {
                    resetAccumulatedUse(player);
                }
            }
        }

        SessionInfo info = SESSIONS.get(player.getUUID());
        if (info == null) return;
        int slot = info.slot();

        // 线程安全：使用副本操作，然后原子替换 + 二次验证
        ItemStack originalBag;
        ItemStack modifiedBag;

        synchronized (player.getInventory()) {
            originalBag = player.getInventory().getItem(slot);
            if (originalBag.isEmpty() || !originalBag.is(ModItems.GOOSE_HARDTACK.get())) {
                SESSIONS.remove(player.getUUID());
                return;
            }
            // 创建副本以避免并发修改
            modifiedBag = originalBag.copy();
        }

        if (!HardtackFoodStorage.hasFoods(modifiedBag)) {
            SESSIONS.remove(player.getUUID());
            return;
        }

        int limit = HardtackConfig.FOODS_PER_TICK.get();
        // foodsPerTick=0 时 consumeMultiple 直接返回空列表，不会消费食物，hasFoods 保持 true，会话永久挂起
        if (limit == 0) return;
        boolean changed = false;

        // 批量消费：一次 read + 一次 write，O(n) 替代逐次 consumeOne 的 O(n²)
        List<ItemStack> foods = HardtackFoodStorage.consumeMultiple(modifiedBag, limit);
        if (!foods.isEmpty()) {
            for (ItemStack food : foods) {
                eat(player, food);
            }
            changed = true;
        }

        // consumeMultiple 可能因食物全部失效（对应 mod 被移除等）在内部调用了 clear，
        // 此时 foods 为空但 modifiedBag 已被清空，需写回物品栏以清理残留的 FoodCount 缓存
        if (!changed && !HardtackFoodStorage.hasFoods(modifiedBag)) {
            changed = true;
        }

        // 更新背包中的饼干（二次验证防止并发修改）
        if (changed) {
            synchronized (player.getInventory()) {
                ItemStack currentBag = player.getInventory().getItem(slot);
                // 验证饼干未被其他线程替换
                if (ItemStack.isSameItemSameTags(currentBag, originalBag)) {
                    player.getInventory().setItem(slot, modifiedBag);
                } else {
                    // 饼干已被替换，取消会话（防止修改错误的物品）
                    SESSIONS.remove(player.getUUID());
                    GooseHardtackMod.LOGGER.warn(
                        "Eating session cancelled for player {} - item in slot {} was replaced",
                        player.getName().getString(), slot);
                    return;
                }
            }
        }

        // 检查是否耗尽
        if (!HardtackFoodStorage.hasFoods(modifiedBag)) {
            SESSIONS.remove(player.getUUID());
            // 发送完成消息（使用会话启动时记录的食物数）
            ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ShortStatusMessagePacket(
                    Component.translatable("item.goose_hardtack.goose_hardtack.message.eaten", info.foodCount()),
                    35));
            // 播放进食完成音效
            player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }

    /**
     * 玩家登出时清理会话
     *
     * <p>玩家登出后会话无意义，清理以释放内存并防止 re-login 后残留状态。
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        SESSIONS.remove(id);
        ACCUMULATED_USE.remove(id);
        USE_TIMEOUT.remove(id);
    }

    /**
     * 玩家切换维度时清理会话
     *
     * <p>跨维度后物品槽位引用不再可靠，清理会话保证安全。
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        UUID id = event.getEntity().getUUID();
        SESSIONS.remove(id);
        ACCUMULATED_USE.remove(id);
        USE_TIMEOUT.remove(id);
    }

    /**
     * 玩家重生时清理旧会话
     *
     * <p>死亡重置物品栏状态，旧会话的槽位引用失效，必须清理。
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        UUID id = event.getOriginal().getUUID();
        SESSIONS.remove(id);
        ACCUMULATED_USE.remove(id);
        USE_TIMEOUT.remove(id);
    }

    /**
     * 模拟玩家吃掉一个食物
     * 
     * <p>调用原版的 {@link ItemStack#finishUsingItem} 触发完整的进食流程，
     * 包括恢复饱食度、应用药水效果、触发 {@link net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish} 事件。
     * 
     * <p>SolCarrot 会监听该事件并记录食物进度，达成里程碑时自动加心。
     * 
     * @param player 玩家
     * @param food 要吃的食物（单个）
     */
    private static void eat(Player player, ItemStack food) {
        LivingEntity entity = player;
        ItemStack eating = food.copy();
        eating.setCount(1);
        ItemStack eventFood = eating.copy();
        
        // 触发原版进食逻辑
        ItemStack result = eating.finishUsingItem(player.level(), entity);
        
        // 触发 Forge 事件（SolCarrot 监听此事件）
        result = ForgeEventFactory.onItemUseFinish(entity, eventFood, 0, result);
        
        // 处理进食后的物品（如返回空碗）
        if (!result.isEmpty() && result != eating) {
            ItemStack resultCopy = result.copy();
            ItemStack leftover = player.getInventory().add(resultCopy) ? ItemStack.EMPTY : resultCopy;
            if (!leftover.isEmpty()) player.drop(leftover, false);
        }
    }
}
