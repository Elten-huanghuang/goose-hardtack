package com.luckgoose.hardtack.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 饼干食物数据存储管理器
 * 
 * <p>负责在物品 NBT 中存储和管理食物列表，包括序列化、反序列化、增删查改等操作。
 * 
 * <p><b>NBT 数据结构：</b>
 * <pre>
 * {
 *   "Foods": [                    // 食物列表（ListTag）
 *     {"id": "minecraft:apple", "count": 1},
 *     {"id": "minecraft:bread", "count": 2}
 *   ],
 *   "FoodCount": 3,               // 食物总数（快速访问）
 *   "TotalUseTicks": 96,          // 进食总时长（ticks）
 *   "ExpectedGain": 4,            // 期望加心数（由 SolCarrot 计算）
 *   "CustomModelData": 1          // 模型数据（1 = 装满）
 * }
 * </pre>
 * 
 * <p><b>安全限制：</b>
 * <ul>
 *   <li>最大食物种类：8192 种（防止内存溢出）</li>
 *   <li>最大进食时长：72000 ticks = 60 分钟（防止数值溢出）</li>
 *   <li>最小进食时长：32 ticks（兜底值）</li>
 * </ul>
 * 
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>按 Item ID 去重：同一种物品只存储一次</li>
 *   <li>立即序列化：所有修改立即写入 NBT，无缓存</li>
 *   <li>防御性编程：所有数值都有上下限保护</li>
 * </ul>
 * 
 * @author LuckGoose
 * @see com.luckgoose.hardtack.item.GooseFoodBagItem 物品实现
 * @see HardtackEatingSessions 进食会话管理
 * @since 1.0.0
 */
public final class HardtackFoodStorage {

    /** 食物列表 NBT 标签 */
    private static final String FOODS_TAG = "Foods";
    
    /** 食物 ID NBT 标签（在 Foods 列表的每个条目中） */
    private static final String ID_TAG = "id";
    
    /** 食物数量 NBT 标签（在 Foods 列表的每个条目中） */
    private static final String COUNT_TAG = "count";
    
    /** 食物总数缓存标签（快速访问，避免遍历列表） */
    private static final String FOOD_COUNT_TAG = "FoodCount";
    
    /** 进食总时长标签（累加所有食物的 useDuration） */
    private static final String TOTAL_USE_TICKS_TAG = "TotalUseTicks";
    
    /** 期望加心数标签（由 SolCarrot 计算） */
    private static final String EXPECTED_GAIN_TAG = "ExpectedGain";
    
    /** 自定义模型数据标签（用于切换模型） */
    private static final String CUSTOM_MODEL_DATA_TAG = "CustomModelData";
    
    /** 装满状态的模型数据值 */
    private static final int FILLED_MODEL_DATA = 1;
    
    /** 最大食物种类数（防止内存溢出） */
    private static final int MAX_FOOD_TYPES = 8192;
    
    /** 最大进食时长（ticks = 60 分钟，防止数值溢出） */
    private static final int MAX_USE_DURATION_TICKS = 72000;
    
    /** 最小进食时长（ticks，兜底值） */
    private static final int MIN_USE_DURATION_TICKS = 32;

    private HardtackFoodStorage() {
    }

    /**
     * 检查饼干是否存储了食物
     * 
     * @param bag 饼干物品
     * @return 如果 FoodCount > 0 返回 true
     */
    public static boolean hasFoods(ItemStack bag) {
        CompoundTag tag = bag.getTag();
        return tag != null && tag.getInt(FOOD_COUNT_TAG) > 0;
    }

    /**
     * 获取饼干中存储的食物总数
     * 
     * @param bag 饼干物品
     * @return 食物数量（单位：种类数，非物品数量）
     */
    public static int getFoodCount(ItemStack bag) {
        CompoundTag tag = bag.getTag();
        return tag == null ? 0 : tag.getInt(FOOD_COUNT_TAG);
    }

    /**
     * 获取进食动画的总时长
     * 
     * <p>该值是所有存储食物的 {@link Item#getUseDuration} 累加而成。
     * 
     * @param bag 饼干物品
     * @return 进食时长（ticks），最小 32，最大 72000
     */
    public static int getTotalUseTicks(ItemStack bag) {
        CompoundTag tag = bag.getTag();
        if (tag == null) return MIN_USE_DURATION_TICKS;
        int ticks = tag.getInt(TOTAL_USE_TICKS_TAG);
        // 限制范围：32 ticks（1.6 秒）~ 72000 ticks（60 分钟）
        return Math.max(MIN_USE_DURATION_TICKS, Math.min(ticks, MAX_USE_DURATION_TICKS));
    }

    /**
     * 获取期望加心数
     * 
     * <p>该值由 {@link com.luckgoose.hardtack.compat.SolCarrotCompat} 计算，
     * 表示吃掉所有食物后能获得的最大生命值提升。
     * 
     * @param bag 饼干物品
     * @return 期望加心数（1 心 = 2 点生命值）
     */
    public static int getExpectedGain(ItemStack bag) {
        CompoundTag tag = bag.getTag();
        return tag == null ? 0 : tag.getInt(EXPECTED_GAIN_TAG);
    }

    /**
     * 设置期望加心数
     * 
     * @param bag 饼干物品
     * @param gain 加心数（≤0 时移除该标签）
     */
    public static void setExpectedGain(ItemStack bag, int gain) {
        if (gain <= 0) {
            if (bag.hasTag()) bag.getTag().remove(EXPECTED_GAIN_TAG);
            return;
        }
        bag.getOrCreateTag().putInt(EXPECTED_GAIN_TAG, gain);
    }

    /**
     * 从 NBT 读取食物列表
     * 
     * <p>反序列化 Foods 标签，将每个条目转换为 {@link Entry} 对象。
     * 无效的条目（ID 不存在、数量 ≤0、非食物）会被自动跳过。
     * 
     * @param bag 饼干物品
     * @return 食物条目列表（只读副本，修改不影响 NBT）
     */
    public static List<Entry> read(ItemStack bag) {
        List<Entry> entries = new ArrayList<>();
        CompoundTag tag = bag.getTag();
        if (tag == null) return entries;
        
        ListTag list = tag.getList(FOODS_TAG, Tag.TAG_COMPOUND);
        for (Tag itemTag : list) {
            CompoundTag entryTag = (CompoundTag) itemTag;
            ResourceLocation id = ResourceLocation.tryParse(entryTag.getString(ID_TAG));
            int count = entryTag.getInt(COUNT_TAG);
            if (id == null || count <= 0) continue;
            
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item.isEdible()) {
                entries.add(new Entry(id, count));
            }
        }
        return entries;
    }

    /**
     * 将食物列表写入 NBT
     * 
     * <p>序列化食物条目到 NBT，并计算以下缓存值：
     * <ul>
     *   <li>FoodCount：食物总数（快速访问）</li>
     *   <li>TotalUseTicks：进食总时长（所有食物 useDuration 累加）</li>
     *   <li>CustomModelData：模型数据（有食物时为 1）</li>
     * </ul>
     * 
     * <p><b>安全限制：</b>
     * <ul>
     *   <li>自动按 ID 去重合并</li>
     *   <li>最大 8192 种食物（超出则清空饼干）</li>
     *   <li>进食时长限制在 32~72000 ticks</li>
     * </ul>
     * 
     * @param bag 饼干物品
     * @param entries 食物条目列表
     */
    public static void write(ItemStack bag, List<Entry> entries) {
        // 合并相同 ID 的食物
        Map<ResourceLocation, Integer> merged = new LinkedHashMap<>();
        for (Entry entry : entries) {
            if (entry.count() <= 0) continue;
            Item item = ForgeRegistries.ITEMS.getValue(entry.id());
            if (item != null && item.isEdible()) {
                merged.merge(entry.id(), entry.count(), HardtackFoodStorage::safeAdd);
            }
        }
        
        // 空列表：清空饼干
        if (merged.isEmpty()) {
            clear(bag);
            return;
        }
        
        // 安全限制：最大 8192 种食物
        if (merged.size() > MAX_FOOD_TYPES) {
            clear(bag);
            return;
        }
        
        // 序列化到 NBT
        ListTag list = new ListTag();
        int foodCount = 0;
        int totalUseTicks = 0;
        
        for (Map.Entry<ResourceLocation, Integer> entry : merged.entrySet()) {
            Item item = ForgeRegistries.ITEMS.getValue(entry.getKey());
            if (item == null) continue;
            
            int count = entry.getValue();
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(ID_TAG, entry.getKey().toString());
            entryTag.putInt(COUNT_TAG, count);
            list.add(entryTag);
            
            foodCount = safeAdd(foodCount, count);
            
            // 计算进食时长（防止溢出）
            int useDuration = item.getUseDuration(item.getDefaultInstance());
            if (count > 0 && useDuration > Integer.MAX_VALUE / count) {
                totalUseTicks = MAX_USE_DURATION_TICKS; // 直接设为最大值
            } else {
                totalUseTicks = safeAdd(totalUseTicks, useDuration * count);
            }
        }
        
        // 空列表兜底
        if (foodCount <= 0) {
            clear(bag);
            return;
        }
        
        // 写入 NBT
        CompoundTag tag = bag.getOrCreateTag();
        tag.put(FOODS_TAG, list);
        tag.putInt(FOOD_COUNT_TAG, foodCount);
        tag.putInt(TOTAL_USE_TICKS_TAG, Math.min(Math.max(MIN_USE_DURATION_TICKS, totalUseTicks), MAX_USE_DURATION_TICKS));
        tag.putInt(CUSTOM_MODEL_DATA_TAG, FILLED_MODEL_DATA);
    }

    /**
     * 写入食物列表（ItemStack 版本）
     * 
     * <p>将 ItemStack 列表转换为 Entry 列表后调用 {@link #write}。
     * 
     * @param bag 饼干物品
     * @param foods ItemStack 列表
     */
    public static void writeItems(ItemStack bag, List<ItemStack> foods) {
        List<Entry> entries = new ArrayList<>();
        for (ItemStack food : foods) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(food.getItem());
            if (!food.isEmpty() && id != null && food.isEdible()) {
                entries.add(new Entry(id, Math.max(1, food.getCount())));
            }
        }
        write(bag, entries);
    }

    /**
     * 消费一个食物并返回
     * 
     * <p>从饼干中取出第一个食物（数量 1），更新剩余列表。
     * 如果第一个食物数量 > 1，则减 1 后保留。
     * 
     * @param bag 饼干物品（会被修改）
     * @return 消费的食物 ItemStack（数量为 1），如果饼干为空则返回 EMPTY
     */
    public static ItemStack consumeOne(ItemStack bag) {
        List<Entry> entries = read(bag);
        if (entries.isEmpty()) return ItemStack.EMPTY;
        
        Entry entry = entries.get(0);
        ItemStack food = createStack(entry, 1);
        if (food.isEmpty()) return ItemStack.EMPTY;
        
        // 更新列表
        List<Entry> updated = new ArrayList<>();
        if (entry.count() > 1) {
            updated.add(entry.withCount(entry.count() - 1));
        }
        for (int i = 1; i < entries.size(); i++) {
            updated.add(entries.get(i));
        }
        
        write(bag, updated);
        
        // 清除期望加心数（需要重新计算）
        if (bag.hasTag()) {
            bag.getTag().remove(EXPECTED_GAIN_TAG);
        }
        
        return food;
    }

    /**
     * 清空饼干中的所有食物数据
     * 
     * @param bag 饼干物品
     */
    public static void clear(ItemStack bag) {
        if (!bag.hasTag()) return;
        CompoundTag tag = bag.getTag();
        tag.remove(FOODS_TAG);
        tag.remove(FOOD_COUNT_TAG);
        tag.remove(TOTAL_USE_TICKS_TAG);
        tag.remove(EXPECTED_GAIN_TAG);
        tag.remove(CUSTOM_MODEL_DATA_TAG);
        if (tag.isEmpty()) bag.setTag(null);
    }

    /**
     * 根据 Entry 创建 ItemStack
     * 
     * @param entry 食物条目
     * @param count 物品数量
     * @return ItemStack，如果物品不存在则返回 EMPTY
     */
    public static ItemStack createStack(Entry entry, int count) {
        Item item = ForgeRegistries.ITEMS.getValue(entry.id());
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = item.getDefaultInstance();
        stack.setCount(count);
        return stack;
    }

    /**
     * 安全加法（防止整数溢出）
     * 
     * @param first 第一个数
     * @param second 第二个数
     * @return 和（最大为 Integer.MAX_VALUE）
     */
    private static int safeAdd(int first, int second) {
        long value = (long) first + second;
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    /**
     * 食物条目（不可变记录）
     * 
     * <p>表示一种食物及其数量，用于在内存中传递数据，不直接关联 NBT。
     * 
     * @param id 物品注册 ID（ResourceLocation）
     * @param count 数量（必须 > 0）
     */
    public record Entry(ResourceLocation id, int count) {
        /**
         * 创建新的 Entry，修改数量
         * 
         * @param count 新数量
         * @return 新的 Entry 实例
         */
        public Entry withCount(int count) {
            return new Entry(id, count);
        }
    }
}
