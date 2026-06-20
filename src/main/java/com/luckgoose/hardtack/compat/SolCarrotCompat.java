package com.luckgoose.hardtack.compat;

import com.cazsius.solcarrot.SOLCarrotConfig;
import com.cazsius.solcarrot.api.FoodCapability;
import com.cazsius.solcarrot.api.SOLCarrotAPI;
import com.cazsius.solcarrot.tracking.FoodList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Spice of Life: Carrot Edition 兼容层
 * 
 * <p>提供与 SolCarrot mod 的集成，负责查询玩家的食物进度、计算期望加心数。
 * 
 * <p><b>核心功能：</b>
 * <ul>
 *   <li><b>食物判定</b>：检查食物是否未品尝、是否应该计入进度</li>
 *   <li><b>进度计算</b>：根据待进食的食物列表计算期望加心数</li>
 *   <li><b>管理员命令</b>：解锁/重置玩家的食物进度（用于 /goose hardtack 命令）</li>
 * </ul>
 * 
 * <p><b>关键设计：</b>
 * <ul>
 *   <li>SolCarrot 只按 <b>Item ID</b> 判定，不考虑 NBT 数据</li>
 *   <li>食物进度存储在玩家的 {@link FoodCapability} 中（持久化到玩家数据）</li>
 *   <li>里程碑系统：每吃若干种食物增加最大生命值</li>
 * </ul>
 * 
 * <p><b>NBT 数据结构（仅供管理员命令使用）：</b>
 * <pre>
 * FoodCapability NBT:
 * {
 *   "foodList": ["minecraft:apple", "minecraft:bread", ...]
 * }
 * </pre>
 * 
 * <p><b>注意事项：</b>
 * <ul>
 *   <li>SolCarrot 没有提供单个食物的移除 API，只能手动操作 NBT</li>
 *   <li>手动操作 NBT 时需要完整序列化/反序列化，性能较低</li>
 *   <li>仅在管理员命令中使用 NBT 操作，自动进食使用 SolCarrot 的事件系统</li>
 * </ul>
 * 
 * @author LuckGoose
 * @see com.luckgoose.hardtack.storage.HardtackEatingSessions 自动进食会话
 * @see com.luckgoose.hardtack.command.GooseHardtackCommand 管理员命令
 * @since 1.0.0
 */
public final class SolCarrotCompat {

    /**
     * SolCarrot 的 FoodCapability NBT 标签名
     * 
     * <p>该标签存储玩家已吃过的食物 ID 列表（字符串数组）。
     * 如果上游修改序列化格式，resetFood 会安全返回 false 而非崩溃。
     */
    private static final String FOOD_LIST_NBT_KEY = "foodList";

    private SolCarrotCompat() {
    }

    /**
     * 检查 SolCarrot mod 是否已加载
     * 
     * @return 如果 SolCarrot 可用返回 true
     */
    public static boolean isAvailable() {
        try {
            Class.forName("com.cazsius.solcarrot.SOLCarrot");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 检查食物是否应该计入进度
     * 
     * <p>符合以下条件才计入：
     * <ul>
     *   <li>可食用（{@link Item#isEdible}）</li>
     *   <li>不在 SolCarrot 黑名单中</li>
     *   <li>不在 SolCarrot 白名单中（如果启用白名单模式）</li>
     * </ul>
     * 
     * @param item 物品
     * @return 是否应计入进度
     */
    public static boolean shouldCount(Item item) {
        return item.isEdible() && SOLCarrotConfig.shouldCount(item);
    }

    /**
     * 检查食物是否未被品尝
     * 
     * <p>同时满足以下条件才返回 true：
     * <ul>
     *   <li>可食用且应计入进度（{@link #shouldCount}）</li>
     *   <li>玩家未吃过该食物（{@link FoodCapability#hasEaten}）</li>
     * </ul>
     * 
     * @param player 玩家
     * @param item 物品
     * @return 是否未品尝
     */
    public static boolean isUneaten(Player player, Item item) {
        if (!item.isEdible() || !shouldCount(item)) return false;
        FoodCapability capability = SOLCarrotAPI.getFoodCapability(player);
        return !capability.hasEaten(item);
    }

    /**
     * 计算期望的最大生命值提升
     * 
     * <p>根据玩家当前进度和待进食的食物列表，计算吃完后能达成的新里程碑数量，
     * 并转换为生命值提升（每个里程碑增加若干心）。
     * 
     * <p><b>计算公式：</b>
     * <pre>
     * 新里程碑数 = 达成里程碑数(当前食物数 + 新增食物数) - 达成里程碑数(当前食物数)
     * 加心数 = 新里程碑数 × 每里程碑加心数 × 2（生命值）
     * </pre>
     * 
     * @param player 玩家
     * @param stacks 待进食的食物列表
     * @return 期望加心数（1 心 = 2 点生命值），至少为 0
     */
    public static int expectedMaxHealthGain(Player player, List<ItemStack> stacks) {
        if (player == null || stacks.isEmpty()) return 0;
        
        FoodList foodList = FoodList.get(player);
        int currentFoods = foodList.getProgressInfo().foodsEaten;
        
        // 计算新增的未品尝食物数
        int addedFoods = 0;
        for (ItemStack stack : stacks) {
            Item item = stack.getItem();
            if (item.isEdible() && shouldCount(item) && !foodList.hasEaten(item)) {
                addedFoods++;
            }
        }
        
        int currentMilestones = milestonesAchieved(currentFoods);
        int nextMilestones = milestonesAchieved(currentFoods + addedFoods);
        
        return Math.max(0, nextMilestones - currentMilestones) * SOLCarrotConfig.getHeartsPerMilestone() * 2;
    }

    /**
     * 解锁食物（标记为已品尝）
     * 
     * <p>直接将食物添加到玩家的 FoodList 中，不触发进食事件。
     * 用于管理员命令 {@code /goose hardtack unlock}。
     * 
     * <p><b>注意：</b>添加食物后会自动同步到客户端并检查里程碑。
     * 
     * @param player 玩家
     * @param item 要解锁的食物
     * @return 如果成功添加返回 true（如果已吃过则返回 false）
     */
    public static boolean unlockFood(Player player, Item item) {
        if (!item.isEdible() || !shouldCount(item)) return false;
        boolean changed = FoodList.get(player).addFood(item);
        if (changed) sync(player);
        return changed;
    }

    /**
     * 解锁所有可计入的食物
     * 
     * <p>遍历注册表中的所有物品，将可食用且应计入的物品全部标记为已品尝。
     * 用于管理员命令 {@code /goose hardtack unlock all}。
     * 
     * @param player 玩家
     * @return 新增的食物数量
     */
    public static int unlockAllFoods(Player player) {
        int changed = 0;
        FoodList foodList = FoodList.get(player);
        for (Item item : ForgeRegistries.ITEMS) {
            if (!item.isEdible() || !shouldCount(item)) continue;
            if (foodList.addFood(item)) changed++;
        }
        if (changed > 0) sync(player);
        return changed;
    }

    /**
     * 重置食物（标记为未品尝）
     * 
     * <p><b>⚠️ 性能警告：</b>该操作需要遍历整个食物列表（可能数百项），
     * <b>时间复杂度 O(n)</b>，<b>不应在高频场景中使用</b>。
     * 
     * <p><b>仅用于管理员命令</b>（{@code /goose hardtack reset}），
     * <b>不要在自动进食流程中调用</b>。
     * 
     * <p>由于 SolCarrot 没有提供移除单个食物的 API，
     * 此方法通过手动操作 NBT 实现：
     * <ol>
     *   <li>完整序列化 FoodCapability 到 NBT</li>
     *   <li>从 foodList 标签中移除指定食物 ID</li>
     *   <li>完整反序列化回 FoodCapability</li>
     * </ol>
     * 
     * @param player 玩家
     * @param item 要重置的食物
     * @return 如果成功移除返回 true（如果未吃过则返回 false）
     */
    public static boolean resetFood(Player player, Item item) {
        if (!item.isEdible()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) return false;
        FoodCapability capability = SOLCarrotAPI.getFoodCapability(player);
        CompoundTag tag = capability.serializeNBT().copy();
        // 兜底：上游格式变动时 getList 返回空列表，下面会因 removed=false 安全退出
        if (!tag.contains(FOOD_LIST_NBT_KEY, Tag.TAG_LIST)) return false;
        ListTag foods = tag.getList(FOOD_LIST_NBT_KEY, Tag.TAG_STRING);
        ListTag updated = new ListTag();
        boolean removed = false;
        for (Tag entry : foods) {
            String value = entry.getAsString();
            if (id.toString().equals(value)) {
                removed = true;
                continue;
            }
            updated.add(StringTag.valueOf(value));
        }
        if (!removed) return false;
        tag.put(FOOD_LIST_NBT_KEY, updated);
        capability.deserializeNBT(tag);
        sync(player);
        return true;
    }

    /**
     * 重置所有食物（清空进度）
     * 
     * <p>清空玩家的 FoodList，移除所有已吃记录。
     * 用于管理员命令 {@code /goose hardtack reset all}。
     * 
     * @param player 玩家
     * @return 移除的食物数量
     */
    public static int resetAllFoods(Player player) {
        FoodList foodList = FoodList.get(player);
        int count = foodList.getEatenFoodCount();
        foodList.clearFood();
        sync(player);
        return count;
    }

    /**
     * 同步 FoodList 到客户端
     * 
     * <p>在修改玩家的食物进度后必须调用，否则客户端数据不一致。
     * 同步时会自动检查里程碑并更新玩家的最大生命值。
     * 
     * @param player 玩家
     */
    private static void sync(Player player) {
        if (player == null || player.level().isClientSide) return;
        SOLCarrotAPI.syncFoodList(player);
    }

    /**
     * 计算已达成的里程碑数量
     * 
     * @param foods 已吃食物种类数
     * @return 达成的里程碑数量
     */
    private static int milestonesAchieved(int foods) {
        int achieved = 0;
        for (int milestone : getMilestones()) {
            if (foods >= milestone) achieved++;
        }
        return achieved;
    }

    /**
     * 获取里程碑列表
     * 
     * @return 里程碑数组（例如 [5, 10, 15, 20, ...]）
     */
    private static int[] getMilestones() {
        List<Integer> milestones = SOLCarrotConfig.getMilestones();
        int[] values = new int[milestones.size()];
        for (int i = 0; i < milestones.size(); i++) {
            values[i] = milestones.get(i);
        }
        return values;
    }
}
