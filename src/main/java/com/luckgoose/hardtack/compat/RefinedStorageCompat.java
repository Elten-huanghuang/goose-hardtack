package com.luckgoose.hardtack.compat;

import com.luckgoose.hardtack.config.HardtackConfig;
import com.luckgoose.hardtack.item.GooseFoodBagItem;
import com.luckgoose.hardtack.storage.HardtackFoodStorage;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.network.node.INetworkNode;
import com.refinedmods.refinedstorage.api.network.node.INetworkNodeManager;
import com.refinedmods.refinedstorage.api.storage.cache.IStorageCache;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.api.util.IComparer;
import com.refinedmods.refinedstorage.api.util.StackListEntry;
import com.refinedmods.refinedstorage.apiimpl.API;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Refined Storage 兼容层
 * 
 * <p>提供与 Refined Storage（精致存储）mod 的集成，支持从 RS 网络中收集和归还食物。
 * 
 * <p><b>核心功能：</b>
 * <ul>
 *   <li><b>网络检测</b>：识别 RS 控制器、网格、接口等网络节点</li>
 *   <li><b>食物收集</b>：从 RS 网络提取未品尝的食物（每种 1 个）</li>
 *   <li><b>食物归还</b>：将饼干中的食物插入回 RS 网络</li>
 * </ul>
 * 
 * <p><b>性能保护机制：</b>
 * <ul>
 *   <li><b>时间限制</b>：单次扫描最多 50ms，超时强制中断（防止卡服）</li>
 *   <li><b>条目限制</b>：最多扫描 N 个条目（配置 maxRsScanEntries，默认 5000）</li>
 *   <li><b>收集限制</b>：最多收集 N 种食物（配置 maxRsCollectedFoods，默认 8192）</li>
 *   <li><b>异常隔离</b>：单个条目失败不影响整体，返回部分结果</li>
 * </ul>
 * 
 * <p><b>线程安全：</b>
 * <ul>
 *   <li>捕获 {@link java.util.ConcurrentModificationException}（RS 网络在扫描时被修改）</li>
 *   <li>每个条目用 try-catch 包裹，跳过损坏的数据</li>
 *   <li>finally 块清理大型集合，帮助 GC 回收</li>
 * </ul>
 * 
 * <p><b>设计权衡：</b>
 * <ul>
 *   <li>RS API 设计为即时迭代（非快照），大型网络扫描会阻塞主线程</li>
 *   <li>优先保证服务器稳定性（50ms 限制），而非完整性（可能只收集部分食物）</li>
 *   <li>异常情况下返回部分结果优于完全失败</li>
 * </ul>
 * 
 * @author LuckGoose
 * @see com.luckgoose.hardtack.item.GooseFoodBagItem#interactWithStorage 交互入口
 * @see HardtackConfig#MAX_RS_SCAN_ENTRIES 扫描限制配置
 * @see HardtackConfig#MAX_RS_COLLECTED_FOODS 收集限制配置
 * @since 1.0.0
 */
public final class RefinedStorageCompat {

    private static final String MOD_ID = "refinedstorage";

    private RefinedStorageCompat() {
    }

    /**
     * 检查方块位置是否可能是 RS 网络节点
     * 
     * <p>快速检测，避免不必要的 capability 查询开销。
     * 
     * @param level 世界
     * @param pos 方块位置
     * @return 如果可能是 RS 网络节点返回 true
     */
    public static boolean mightHandle(Level level, BlockPos pos) {
        return isGridBlock(level, pos);
    }

    /**
     * 从 RS 网络收集未品尝的食物
     * 
     * <p><b>工作流程：</b>
     * <ol>
     *   <li>获取 RS 网络实例（检查网络是否可用）</li>
     *   <li>扫描网络中的物品条目（受时间和数量限制）</li>
     *   <li>筛选未品尝的食物（排除黑名单）</li>
     *   <li>从网络中提取食物（SIMULATE → PERFORM 两阶段）</li>
     *   <li>写入饼干 NBT 并计算期望加心数</li>
     * </ol>
     * 
     * <p><b>性能保护：</b>
     * <ul>
     *   <li>时间限制：50ms 超时强制中断</li>
     *   <li>扫描限制：maxRsScanEntries（默认 5000）</li>
     *   <li>收集限制：maxRsCollectedFoods（默认 8192）</li>
     *   <li>异常隔离：单个条目失败跳过，继续处理其他条目</li>
     * </ul>
     * 
     * <p><b>失败处理：</b>
     * <ul>
     *   <li>网络不可用 → 返回 rsUnavailable</li>
     *   <li>扫描超时 → 记录警告日志，返回已收集的食物</li>
     *   <li>并发修改 → 返回部分结果</li>
     *   <li>未找到食物 → 返回 noFood</li>
     * </ul>
     * 
     * @param bag 饼干物品（会被修改）
     * @param level 世界
     * @param pos RS 网络节点位置
     * @param player 玩家（用于判断未品尝食物）
     * @return 操作结果（包含成功/失败状态和移动的食物数量）
     */
    public static StorageActionResult collectFoods(ItemStack bag, Level level, BlockPos pos, Player player) {
        INetwork network = getNetwork(level, pos);
        if (network == null) return StorageActionResult.rsUnavailable();

        int scanned = 0;
        int maxScanEntries = HardtackConfig.MAX_RS_SCAN_ENTRIES.get();
        int maxCollectedFoods = HardtackConfig.MAX_RS_COLLECTED_FOODS.get();
        if (maxCollectedFoods == 0) return StorageActionResult.noFood();

        // 预分配容量，减少 ArrayList 扩容开销
        int expectedCapacity = maxCollectedFoods > 0 ? Math.min(maxCollectedFoods, 512) : 512;
        List<ItemStack> candidates = new ArrayList<>(expectedCapacity);
        List<ItemStack> foods = new ArrayList<>(expectedCapacity);
        Set<ResourceLocation> seen = new HashSet<>(expectedCapacity);
        Set<ResourceLocation> blacklisted = GooseFoodBagItem.getFoodBlacklistIds(bag);

        try {
            // 时间限制和异常处理
            long startTime = System.nanoTime();
            for (StackListEntry<ItemStack> entry : getNetworkStackEntries(network)) {
                // 超过 50ms 强制中断，防止卡服
                if ((System.nanoTime() - startTime) > 50_000_000) {
                    com.luckgoose.hardtack.GooseHardtackMod.LOGGER.warn(
                        "RS network scan interrupted after 50ms, scanned {} entries", scanned);
                    break;
                }
                
                if (maxCollectedFoods > 0 && candidates.size() >= maxCollectedFoods) break;
                if (maxScanEntries >= 0 && scanned >= maxScanEntries) break;
                scanned++;
                
                try {
                    ItemStack stack = entry.getStack();
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (stack.isEmpty() || id == null || seen.contains(id) || blacklisted.contains(id) || !SolCarrotCompat.isUneaten(player, stack.getItem())) continue;
                    candidates.add(stack.copy());
                    seen.add(id);
                } catch (Exception e) {
                    // 跳过有问题的条目，继续处理其他条目
                    com.luckgoose.hardtack.GooseHardtackMod.LOGGER.debug("Failed to process RS entry", e);
                    continue;
                }
            }

            // 从网络中提取食物
            for (ItemStack candidate : candidates) {
                try {
                    // 两阶段提取：先模拟，确认可提取后再真实提取
                    ItemStack simulated = network.extractItem(candidate, 1, IComparer.COMPARE_NBT, Action.SIMULATE);
                    if (simulated.isEmpty()) continue;
                    ItemStack extracted = network.extractItem(candidate, 1, IComparer.COMPARE_NBT, Action.PERFORM);
                    if (extracted.isEmpty()) continue;
                    extracted.setCount(1);
                    foods.add(extracted);
                } catch (Exception e) {
                    com.luckgoose.hardtack.GooseHardtackMod.LOGGER.warn("Failed to extract item from RS network", e);
                    continue;
                }
            }
        } catch (java.util.ConcurrentModificationException e) {
            // RS 网络在迭代期间被修改，返回已收集的部分
            com.luckgoose.hardtack.GooseHardtackMod.LOGGER.warn(
                "RS network modified during scan for player {}, collected {} of {} candidates before interruption",
                player.getName().getString(), foods.size(), candidates.size());
            // 继续处理已收集的食物（部分成功优于完全失败）
        } catch (Exception e) {
            com.luckgoose.hardtack.GooseHardtackMod.LOGGER.error("Unexpected error during RS food collection", e);
            return StorageActionResult.rsUnavailable();
        } finally {
            // 及时清理大型集合，帮助 GC 回收
            candidates.clear();
            seen.clear();
        }

        if (foods.isEmpty()) return StorageActionResult.noFood();
        GooseFoodBagItem.setStoredFoodsForCompat(bag, foods);
        HardtackFoodStorage.setExpectedGain(bag, SolCarrotCompat.expectedMaxHealthGain(player, foods));
        return StorageActionResult.collected(foods.size());
    }

    /**
     * 将饼干中的食物归还到 RS 网络
     * 
     * <p><b>工作流程：</b>
     * <ol>
     *   <li>检查网络是否可用</li>
     *   <li>遍历所有存储的食物</li>
     *   <li>逐个插入网络（SIMULATE → PERFORM）</li>
     *   <li>记录成功/失败的食物</li>
     *   <li>更新饼干 NBT（只保留未归还的食物）</li>
     * </ol>
     * 
     * <p><b>部分归还：</b>
     * 如果网络满了，只归还能放入的部分，剩余食物保留在饼干中。
     * 
     * @param bag 饼干物品（会被修改）
     * @param level 世界
     * @param pos RS 网络节点位置
     * @param storedFoods 要归还的食物列表
     * @return 操作结果（包含归还数量和剩余数量）
     */
    public static StorageActionResult returnFoods(ItemStack bag, Level level, BlockPos pos, List<ItemStack> storedFoods) {
        INetwork network = getNetwork(level, pos);
        if (network == null) return StorageActionResult.rsUnavailable();
        if (storedFoods.isEmpty()) return StorageActionResult.noFood();

        List<ItemStack> remaining = new ArrayList<>(storedFoods.size());
        int returned = 0;
        int remainingCount = 0;

        for (ItemStack stored : storedFoods) {
            ItemStack simulated = network.insertItem(stored.copy(), stored.getCount(), Action.SIMULATE);
            int canInsert = stored.getCount() - (simulated.isEmpty() ? 0 : simulated.getCount());
            if (canInsert <= 0) {
                remaining.add(stored);
                remainingCount += stored.getCount();
                continue;
            }

            ItemStack toInsert = stored.copy();
            toInsert.setCount(canInsert);
            ItemStack leftover = network.insertItem(toInsert, canInsert, Action.PERFORM);
            int inserted = canInsert - (leftover.isEmpty() ? 0 : leftover.getCount());
            returned += inserted;

            if (inserted < stored.getCount()) {
                ItemStack remainder = stored.copy();
                remainder.setCount(stored.getCount() - inserted);
                remaining.add(remainder);
                remainingCount += remainder.getCount();
            }
        }

        GooseFoodBagItem.setStoredFoodsForCompat(bag, remaining);

        if (returned == 0) {
            return StorageActionResult.rsFull(remainingCount);
        }

        return StorageActionResult.returned(returned, remainingCount);
    }

    /**
     * 获取 RS 网络实例
     * 
     * @param level 世界
     * @param pos 网络节点位置
     * @return RS 网络实例，如果不可用返回 null
     */
    @Nullable
    private static INetwork getNetwork(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        INetworkNodeManager manager = API.instance().getNetworkNodeManager(serverLevel);
        INetworkNode node = manager.getNode(pos);
        return node == null ? null : node.getNetwork();
    }

    private static boolean isGridBlock(Level level, BlockPos pos) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
        if (id == null || !MOD_ID.equals(id.getNamespace())) return false;
        String path = id.getPath();
        return path.equals("grid")
                || path.endsWith("_grid")
                || path.equals("crafting_grid")
                || path.endsWith("_crafting_grid")
                || path.equals("pattern_grid")
                || path.endsWith("_pattern_grid")
                || path.equals("fluid_grid")
                || path.endsWith("_fluid_grid");
    }

    /**
     * 获取 RS 网络中的物品存储缓存条目
     * 
     * @param network RS 网络
     * @return 物品条目的可迭代对象
     */
    private static Iterable<StackListEntry<ItemStack>> getNetworkStackEntries(INetwork network) {
        IStorageCache<ItemStack> cache = network.getItemStorageCache();
        return cache.getList().getStacks();
    }

    /**
     * 失败原因枚举
     */
    public enum FailureReason {
        /** 操作成功 */
        NONE,
        /** 未找到未品尝的食物 */
        NO_FOOD,
        /** 归还失败（容器已满） */
        RETURN_FAILED,
        /** RS 网络不可用 */
        RS_UNAVAILABLE,
        /** RS 网络已满 */
        RS_FULL
    }

    /**
     * 存储操作结果（不可变记录）
     * 
     * <p>表示从容器或 RS 网络收集/归还食物的操作结果。
     * 
     * <p><b>字段说明：</b>
     * <ul>
     *   <li><b>handled</b>：是否处理了请求（false 表示操作未执行）</li>
     *   <li><b>changed</b>：是否修改了饼干内容</li>
     *   <li><b>collecting</b>：是收集操作（true）还是归还操作（false）</li>
     *   <li><b>moved</b>：移动的食物数量（收集时为新增数，归还时为成功归还数）</li>
     *   <li><b>remaining</b>：剩余的食物数量（归还失败时有效）</li>
     *   <li><b>failureReason</b>：失败原因（成功时为 NONE）</li>
     * </ul>
     * 
     * <p><b>典型用例：</b>
     * <pre>
     * // 成功收集 10 种食物
     * StorageActionResult.collected(10)
     * → handled=true, changed=true, collecting=true, moved=10, remaining=10
     * 
     * // 归还成功 5 种，剩余 3 种
     * StorageActionResult.returned(5, 3)
     * → handled=true, changed=true, collecting=false, moved=5, remaining=3
     * 
     * // RS 网络不可用
     * StorageActionResult.rsUnavailable()
     * → handled=true, changed=false, failureReason=RS_UNAVAILABLE
     * 
     * // 未找到食物
     * StorageActionResult.noFood()
     * → handled=true, changed=false, failureReason=NO_FOOD
     * </pre>
     * 
     * @param handled 是否处理了请求
     * @param changed 是否修改了饼干
     * @param collecting 是否为收集操作
     * @param moved 移动的食物数量
     * @param remaining 剩余的食物数量
     * @param failureReason 失败原因
     */
    public record StorageActionResult(boolean handled, boolean changed, boolean collecting, int moved, int remaining, FailureReason failureReason) {
        /** 操作未处理（不是容器或 RS 网络） */
        public static StorageActionResult pass() {
            return new StorageActionResult(false, false, false, 0, 0, FailureReason.NONE);
        }

        /** 未找到未品尝的食物 */
        public static StorageActionResult noFood() {
            return new StorageActionResult(true, false, true, 0, 0, FailureReason.NO_FOOD);
        }

        /** 归还失败（容器已满） */
        public static StorageActionResult returnFailed(int remaining) {
            return new StorageActionResult(true, false, false, 0, remaining, FailureReason.RETURN_FAILED);
        }

        /** 成功收集食物 */
        public static StorageActionResult collected(int moved) {
            return new StorageActionResult(true, true, true, moved, moved, FailureReason.NONE);
        }

        /** 成功归还食物（可能部分归还） */
        public static StorageActionResult returned(int moved, int remaining) {
            return new StorageActionResult(true, true, false, moved, remaining, FailureReason.NONE);
        }

        /** RS 网络不可用 */
        public static StorageActionResult rsUnavailable() {
            return new StorageActionResult(true, false, false, 0, 0, FailureReason.RS_UNAVAILABLE);
        }

        /** RS 网络已满 */
        public static StorageActionResult rsFull(int remaining) {
            return new StorageActionResult(true, false, false, 0, remaining, FailureReason.RS_FULL);
        }
    }
}
