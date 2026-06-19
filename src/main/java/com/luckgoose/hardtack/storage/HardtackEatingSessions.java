package com.luckgoose.hardtack.storage;

import com.luckgoose.hardtack.GooseHardtackMod;
import com.luckgoose.hardtack.config.HardtackConfig;
import com.luckgoose.hardtack.init.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
 *   <li>饼干清空或玩家受伤时会话结束</li>
 * </ol>
 * 
 * <p><b>线程安全：</b>
 * <ul>
 *   <li>使用 {@link ConcurrentHashMap} 存储会话映射</li>
 *   <li>存储槽位索引而非 ItemStack 引用，避免引用共享问题</li>
 *   <li>操作 NBT 时使用副本 + 同步块 + 原子替换模式</li>
 * </ul>
 * 
 * <p><b>中断条件：</b>
 * <ul>
 *   <li>玩家受伤（{@link #onLivingHurt}）</li>
 *   <li>玩家切换维度（{@link #onPlayerChangedDimension}）</li>
 *   <li>玩家登出（{@link #onPlayerLoggedOut}）</li>
 *   <li>玩家死亡或重生（{@link #onPlayerClone}）</li>
 * </ul>
 * 
 * @author LuckGoose
 * @see HardtackFoodStorage 食物数据存储
 * @see com.luckgoose.hardtack.compat.SolCarrotCompat SolCarrot 兼容层
 * @since 1.0.0
 */
@Mod.EventBusSubscriber(modid = GooseHardtackMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HardtackEatingSessions {

    /**
     * 进食会话映射表
     * 
     * <p>Key: 玩家 UUID<br>
     * Value: 饼干所在的槽位索引（而非 ItemStack 引用，避免线程安全问题）
     * 
     * <p>使用 {@link ConcurrentHashMap} 防止多线程并发问题
     */
    private static final Map<UUID, Integer> SESSIONS = new ConcurrentHashMap<>();

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
     * <p>如果饼干中有食物，则创建会话并开始在每个 tick 中消费食物。
     * 
     * @param player 服务端玩家
     * @param slot 饼干所在的槽位索引
     */
    public static void start(ServerPlayer player, int slot) {
        ItemStack bag = player.getInventory().getItem(slot);
        if (bag.isEmpty() || !bag.is(ModItems.GOOSE_HARDTACK.get())) return;
        if (HardtackFoodStorage.hasFoods(bag)) {
            SESSIONS.put(player.getUUID(), slot);
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
     * 服务器玩家 Tick 事件处理
     * 
     * <p>在每个 tick 的结束阶段，检查玩家是否有活跃的进食会话。
     * 如果有，则批量消费饼干中的食物并触发进食事件。
     * 
     * <p><b>线程安全处理：</b>
     * <ol>
     *   <li>使用同步块获取 ItemStack 副本（避免并发修改 NBT）</li>
     *   <li>在副本上操作食物数据</li>
     *   <li>使用同步块原子替换回背包（保证数据一致性）</li>
     * </ol>
     * 
     * <p><b>性能控制：</b>
     * 每个 tick 消费的食物数量由配置 {@link HardtackConfig#FOODS_PER_TICK} 控制，
     * 设置为 -1 表示不限制。
     * 
     * @param event 玩家 Tick 事件
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide || !(event.player instanceof ServerPlayer player)) return;
        Integer slot = SESSIONS.get(player.getUUID());
        if (slot == null) return;
        
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
        
        int processed = 0;
        int limit = HardtackConfig.FOODS_PER_TICK.get();
        boolean changed = false;
        
        // 批量消费食物（在副本上操作，无需锁）
        while ((limit < 0 || processed < limit) && HardtackFoodStorage.hasFoods(modifiedBag)) {
            ItemStack food = HardtackFoodStorage.consumeOne(modifiedBag);
            if (food.isEmpty()) break;
            eat(player, food);
            processed++;
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
        
        // 检查是否耗尽（使用更新后的 modifiedBag）
        if (!HardtackFoodStorage.hasFoods(modifiedBag)) {
            SESSIONS.remove(player.getUUID());
            // 播放进食完成音效
            player.level().playSound(null, player.blockPosition(), 
                SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }

    /** 玩家登出时清理会话 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    /** 玩家切换维度时清理会话 */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    /** 玩家受伤时中断会话 */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getAmount() > 0 && event.getEntity() instanceof ServerPlayer player) stop(player);
    }

    /** 玩家重生时清理旧会话 */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        SESSIONS.remove(event.getOriginal().getUUID());
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
