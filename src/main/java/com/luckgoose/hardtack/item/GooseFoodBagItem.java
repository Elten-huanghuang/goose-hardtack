package com.luckgoose.hardtack.item;
import com.luckgoose.hardtack.compat.RefinedStorageCompat;
import com.luckgoose.hardtack.compat.RefinedStorageCompat.StorageActionResult;
import com.luckgoose.hardtack.compat.SolCarrotCompat;
import com.luckgoose.hardtack.init.ModItems;
import com.luckgoose.hardtack.network.ModNetwork;
import com.luckgoose.hardtack.network.OpenFoodBlacklistPacket;
import com.luckgoose.hardtack.network.ShortStatusMessagePacket;
import com.luckgoose.hardtack.config.HardtackConfig;
import com.luckgoose.hardtack.storage.HardtackEatingSessions;
import com.luckgoose.hardtack.storage.HardtackFoodStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 大鹅的压缩饼干物品实现
 * 
 * <p>这是一个可以收集、存储和批量进食未品尝食物的道具，
 * 设计用于配合 Spice of Life: Carrot Edition 快速解锁食物里程碑。
 * 
 * <p><b>交互方式：</b>
 * <ul>
 *   <li><b>左键容器</b>：收集容器中未品尝的食物（每种 1 个），或归还已收集的食物</li>
 *   <li><b>右键使用</b>：开始进食动画，完成后触发批量进食</li>
 *   <li><b>Shift+右键</b>：打开黑名单配置界面（27 格）</li>
 * </ul>
 * 
 * <p><b>数据存储：</b>
 * <ul>
 *   <li>食物列表存储在物品 NBT 的 {@code Foods} 标签中</li>
 *   <li>黑名单存储在 {@code FoodBlacklist} 标签中</li>
 *   <li>期望加心数存储在 {@code ExpectedGain} 标签中（由 SolCarrot 计算）</li>
 * </ul>
 * 
 * <p><b>兼容性：</b>
 * <ul>
 *   <li>支持原版容器和所有实现 {@link IItemHandler} 的方块</li>
 *   <li>支持 Refined Storage 网络（通过 {@link RefinedStorageCompat}）</li>
 *   <li>依赖 Spice of Life: Carrot Edition（通过 {@link SolCarrotCompat}）</li>
 * </ul>
 * 
 * @author LuckGoose
 * @see HardtackEatingSessions 自动进食会话管理
 * @see HardtackFoodStorage 食物数据存储
 * @since 1.0.0
 */
public class GooseFoodBagItem extends Item {

    /** 黑名单 NBT 标签名 */
    private static final String FOOD_BLACKLIST_TAG = "FoodBlacklist";
    
    /** 短消息显示时长（ticks） */
    private static final int MESSAGE_DURATION_TICKS = 35;
    
    /** 黑名单槽位数量（3 行 x 9 列） */
    public static final int FOOD_BLACKLIST_SLOTS = 27;

    public GooseFoodBagItem(Properties properties) {
        super(properties);
    }

    /**
     * 处理对方块的右键交互（覆盖默认行为）
     * 
     * <p>当饼干已存储食物且玩家未潜行时，阻止放置方块，
     * 改为触发左键收集/归还逻辑（在 {@link com.luckgoose.hardtack.event.GooseFoodBagEvents} 中处理）
     * 
     * @return 如果已存储食物，返回 {@link InteractionResult#sidedSuccess}，否则 PASS
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (hasStoredFoods(context.getItemInHand()) && (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown())) {
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return InteractionResult.PASS;
    }

    /**
     * 处理右键使用物品
     * 
     * <p>行为：
     * <ul>
     *   <li>潜行时：打开黑名单配置界面</li>
     *   <li>非潜行且已存储食物：开始进食动画</li>
     *   <li>空饼干：无操作</li>
     * </ul>
     * 
     * @return 交互结果（成功/消费/传递）
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        
        // Shift+右键：打开黑名单配置
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (hand == InteractionHand.MAIN_HAND) {
                    OpenFoodBlacklistPacket.open(serverPlayer, player.getInventory().selected);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        
        // 右键：开始进食（如果有食物）
        if (!hasStoredFoods(stack)) return InteractionResultHolder.pass(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /**
     * 完成进食动画后触发
     *
     * <p>在服务端启动进食会话（{@link HardtackEatingSessions}），
     * 该会话会在每个 tick 中批量消费食物并触发 SolCarrot 的进食事件。
     * 会话结束时由 {@link HardtackEatingSessions#onPlayerTick} 发送完成消息和打嗝音效，
     * 而非在此处提前发送。
     *
     * @see HardtackEatingSessions#start(ServerPlayer, int)
     */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            if (hasStoredFoods(stack) && !HardtackEatingSessions.isRunning(player)) {
                int slot = findBagSlot(player, stack);
                if (slot >= 0) {
                    HardtackEatingSessions.start(player, slot);
                }
            }
        }
        return stack;
    }

    /**
     * 物品使用中每 tick 回调。
     *
     * <p>将使用时间累积到 HardtackEatingSessions，跨受击中断保留。
     * 累积达到阈值后直接启动进食会话，不再依赖 finishUsingItem（受击会重置原版使用进度）。
     */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        if (!hasStoredFoods(stack) || HardtackEatingSessions.isRunning(player)) return;
        HardtackEatingSessions.accumulateUse(player);
    }

    /**
     * 在玩家背包中查找饼干所在的槽位
     * 
     * <p>优先按引用匹配（finishUsingItem 传入的是手持物引用），
     * 引用不一致时回退到「同为本物品 + NBT 相同」的匹配。
     * 
     * @param player 玩家
     * @param targetBag 目标饼干
     * @return 槽位索引，未找到返回 -1
     */
    public static int findBagSlot(ServerPlayer player, ItemStack targetBag) {
        int fallback = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack == targetBag) return i;  // 引用匹配
            if (fallback < 0 && stack.is(ModItems.GOOSE_HARDTACK.get()) && ItemStack.isSameItemSameTags(stack, targetBag)) {
                fallback = i;  // NBT 匹配兜底
            }
        }
        return fallback;
    }

    /**
     * 获取进食动画的总时长（ticks）
     * 
     * @return 根据配置返回固定时长或累加所有食物的 useDuration
     * @see HardtackConfig#EAT_TIME_MODE
     */
    @Override
    public int getUseDuration(ItemStack stack) {
        return calculateTotalUseDuration(stack);
    }

    private static int calculateTotalUseDuration(ItemStack stack) {
        String mode = HardtackConfig.EAT_TIME_MODE.get();
        if ("fixed".equals(mode)) {
            return HardtackConfig.FIXED_EAT_TIME_TICKS.get();
        }
        return HardtackFoodStorage.getTotalUseTicks(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    /**
     * 添加物品提示文本
     * 
     * <p>显示：
     * <ul>
     *   <li>食物数量</li>
     *   <li>期望加心数（SolCarrot 可用时）</li>
     *   <li>预计进食时间</li>
     *   <li>黑名单提示</li>
     * </ul>
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        boolean shiftDown = level != null && level.isClientSide
                && net.minecraft.client.gui.screens.Screen.hasShiftDown();

        int foodCount = HardtackFoodStorage.getFoodCount(stack);
        if (foodCount <= 0) {
            tooltip.add(Component.translatable("item.goose_hardtack.goose_hardtack.tooltip.empty").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("item.goose_hardtack.goose_hardtack.tooltip.count", foodCount).withStyle(ChatFormatting.GRAY));
            int gain = SolCarrotCompat.isAvailable() ? HardtackFoodStorage.getExpectedGain(stack) : 0;
            tooltip.add(Component.translatable("item.goose_hardtack.goose_hardtack.tooltip.gain", gain).withStyle(gain > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GREEN));
            int useTicks = calculateTotalUseDuration(stack);
            double useSeconds = useTicks / 20.0;
            tooltip.add(Component.translatable("item.goose_hardtack.goose_hardtack.tooltip.eat_time", String.format("%.1f", useSeconds)).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.goose_hardtack.goose_hardtack.tooltip.blacklist").withStyle(ChatFormatting.DARK_GRAY));

        if (shiftDown) {
            tooltip.add(Component.translatable("item.goose_hardtack.goose_hardtack.tooltip.controls.header").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("item.goose_hardtack.goose_hardtack.tooltip.controls.collect").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.goose_hardtack.goose_hardtack.tooltip.controls.eat").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.goose_hardtack.goose_hardtack.tooltip.controls.blacklist").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.goose_hardtack.goose_hardtack.tooltip.shift_hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * 与存储方块交互（收集或归还食物）
     * 
     * <p>行为逻辑：
     * <ul>
     *   <li>饼干为空：从容器中收集未品尝的食物（每种 1 个）</li>
     *   <li>饼干有食物：将食物归还到容器（如果容器满了则部分归还）</li>
     * </ul>
     * 
     * <p>支持的容器类型：
     * <ul>
     *   <li>Refined Storage 网络（优先检测）</li>
     *   <li>所有实现 {@link IItemHandler} 的方块（箱子、桶等）</li>
     * </ul>
     * 
     * @param bag 饼干物品
     * @param level 世界
     * @param pos 方块位置
     * @param side 交互面（可为 null）
     * @param player 玩家
     * @return 是否成功交互
     */
    public static boolean interactWithStorage(ItemStack bag, Level level, BlockPos pos, @Nullable Direction side, Player player) {
        if (bag.isEmpty() || !bag.is(ModItems.GOOSE_HARDTACK.get())) return false;
        
        // 优先检测 RS 网络
        boolean useRefinedStorage = RefinedStorageCompat.mightHandle(level, pos);
        IItemHandler handler = null;
        if (!useRefinedStorage) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            handler = getItemHandler(blockEntity, side);
            if (handler == null) return false;
        }
        if (level.isClientSide) return true;
        
        boolean hasFoods = hasStoredFoods(bag);
        List<ItemStack> storedFoods = hasFoods ? getStoredFoods(bag) : List.of();
        
        // 执行收集或归还
        StorageActionResult result = useRefinedStorage
                ? (!hasFoods ? RefinedStorageCompat.collectFoods(bag, level, pos, player) : RefinedStorageCompat.returnFoods(bag, level, pos, storedFoods))
                : (!hasFoods ? collectFoods(bag, handler, player) : returnFoods(bag, handler, storedFoods));
        
        if (!result.handled()) return false;
        
        if (result.changed()) {
            if (!result.collecting()) refreshExpectedGain(bag, player);
            sendShortMessage(player, result.collecting()
                    ? Component.translatable("item.goose_hardtack.goose_hardtack.message.collected", result.moved())
                    : Component.translatable("item.goose_hardtack.goose_hardtack.message.returned", result.moved(), result.remaining()));
            level.playSound(null, pos, SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8F, 1.0F);
            return true;
        }
        
        sendShortMessage(player, failureMessage(result));
        return true;
    }

    /**
     * 刷新期望加心数（根据当前存储的食物计算）
     */
    private static void refreshExpectedGain(ItemStack bag, Player player) {
        if (!hasStoredFoods(bag)) {
            HardtackFoodStorage.setExpectedGain(bag, 0);
            return;
        }
        HardtackFoodStorage.setExpectedGain(bag, SolCarrotCompat.expectedMaxHealthGain(player, getStoredFoods(bag)));
    }

    /**
     * 向玩家发送短暂的状态消息（显示在物品栏上方）
     */
    private static void sendShortMessage(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new ShortStatusMessagePacket(message, MESSAGE_DURATION_TICKS));
        }
    }

    /**
     * 根据失败原因生成提示消息
     */
    private static Component failureMessage(StorageActionResult result) {
        return switch (result.failureReason()) {
            case RETURN_FAILED -> Component.translatable("item.goose_hardtack.goose_hardtack.message.return_failed");
            case RS_UNAVAILABLE -> Component.translatable("item.goose_hardtack.goose_hardtack.message.rs_unavailable");
            case RS_FULL -> Component.translatable("item.goose_hardtack.goose_hardtack.message.rs_full");
            case NO_FOOD, NONE -> Component.translatable("item.goose_hardtack.goose_hardtack.message.no_food");
        };
    }

    /** 检查饼干是否存储了食物 */
    public static boolean hasStoredFoods(ItemStack bag) {
        return HardtackFoodStorage.hasFoods(bag);
    }

    /** 获取饼干中存储的所有食物列表 */
    public static List<ItemStack> getStoredFoods(ItemStack bag) {
        List<ItemStack> foods = new ArrayList<>();
        for (HardtackFoodStorage.Entry entry : HardtackFoodStorage.read(bag)) {
            ItemStack food = HardtackFoodStorage.createStack(entry, entry.count());
            if (!food.isEmpty()) foods.add(food);
        }
        return foods;
    }

    public static List<ItemStack> getFoodBlacklist(ItemStack bag) {
        CompoundTag tag = bag.getTag();
        if (tag == null) {
            // 修复：避免不必要的对象创建
            return createEmptyBlacklist();
        }
        
        List<ItemStack> foods = createEmptyBlacklist();
        ListTag list = tag.getList(FOOD_BLACKLIST_TAG, Tag.TAG_COMPOUND);
        Set<ResourceLocation> seen = new HashSet<>();
        int nextLegacySlot = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.contains("Slot", Tag.TAG_INT) ? entry.getInt("Slot") : nextLegacySlot++;
            if (slot < 0 || slot >= FOOD_BLACKLIST_SLOTS) continue;
            ItemStack food = ItemStack.of(entry);
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(food.getItem());
            if (!food.isEmpty() && id != null && isSupportedBlacklistFood(food) && !seen.contains(id)) {
                food.setCount(1);
                foods.set(slot, food);
                seen.add(id);
            }
        }
        return foods;
    }
    
    private static List<ItemStack> createEmptyBlacklist() {
        List<ItemStack> list = new ArrayList<>(FOOD_BLACKLIST_SLOTS);
        for (int slot = 0; slot < FOOD_BLACKLIST_SLOTS; slot++) {
            list.add(ItemStack.EMPTY);
        }
        return list;
    }

    public static ItemStack getFoodBlacklistEntry(ItemStack bag, int slot) {
        if (slot < 0 || slot >= FOOD_BLACKLIST_SLOTS) return ItemStack.EMPTY;
        List<ItemStack> foods = getFoodBlacklist(bag);
        return foods.get(slot).copy();
    }

    public static boolean setFoodBlacklistEntry(ItemStack bag, int slot, ItemStack stack) {
        if (slot < 0 || slot >= FOOD_BLACKLIST_SLOTS) return false;
        List<ItemStack> foods = getFoodBlacklist(bag);
        if (stack.isEmpty()) {
            foods.set(slot, ItemStack.EMPTY);
            setFoodBlacklist(bag, foods);
            return true;
        }
        if (!isSupportedBlacklistFood(stack)) return false;
        for (int i = 0; i < foods.size(); i++) {
            if (i != slot && isSameItem(foods.get(i), stack)) return false;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        foods.set(slot, copy);
        setFoodBlacklist(bag, foods);
        return true;
    }

    public static boolean isSupportedBlacklistFood(ItemStack stack) {
        return !stack.isEmpty() && stack.isEdible();
    }

    public static Set<ResourceLocation> getFoodBlacklistIds(ItemStack bag) {
        Set<ResourceLocation> ids = new HashSet<>();
        for (ItemStack blacklisted : getFoodBlacklist(bag)) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(blacklisted.getItem());
            if (id != null) ids.add(id);
        }
        return ids;
    }

    @Nullable
    private static IItemHandler getItemHandler(@Nullable BlockEntity blockEntity, @Nullable Direction side) {
        if (blockEntity == null) return null;
        if (side != null) {
            IItemHandler handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
            if (handler != null) return handler;
        }
        IItemHandler handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        if (handler != null) return handler;
        for (Direction direction : Direction.values()) {
            if (direction == side) continue;
            handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction).orElse(null);
            if (handler != null) return handler;
        }
        return null;
    }

    private static StorageActionResult collectFoods(ItemStack bag, IItemHandler handler, Player player) {
        Set<ResourceLocation> blacklisted = getFoodBlacklistIds(bag);
        // 修复：预分配容量，限制最大收集数量
        int maxCollect = Math.min(handler.getSlots(), 8192);
        List<ItemStack> foods = new ArrayList<>(Math.min(maxCollect, 512));
        Set<ResourceLocation> seen = new HashSet<>(Math.min(maxCollect, 512));
        
        try {
            for (int slot = 0; slot < handler.getSlots() && foods.size() < maxCollect; slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (stack.isEmpty() || id == null || seen.contains(id) || blacklisted.contains(id) || !SolCarrotCompat.isUneaten(player, stack.getItem())) continue;
                ItemStack simulated = handler.extractItem(slot, 1, true);
                if (simulated.isEmpty()) continue;
                ItemStack extracted = handler.extractItem(slot, 1, false);
                if (!extracted.isEmpty()) {
                    extracted.setCount(1);
                    foods.add(extracted);
                    seen.add(id);
                }
            }
        } catch (RuntimeException exception) {
            if (!foods.isEmpty()) {
                HardtackFoodStorage.writeItems(bag, foods);
                return StorageActionResult.collected(foods.size());
            }
            throw exception;
        } finally {
            // 修复：及时清理
            seen.clear();
        }
        
        if (foods.isEmpty()) return StorageActionResult.noFood();
        HardtackFoodStorage.writeItems(bag, foods);
        HardtackFoodStorage.setExpectedGain(bag, SolCarrotCompat.expectedMaxHealthGain(player, foods));
        return StorageActionResult.collected(foods.size());
    }

    private static StorageActionResult returnFoods(ItemStack bag, IItemHandler handler, List<ItemStack> storedFoods) {
        // 修复：预分配容量
        List<ItemStack> remaining = new ArrayList<>(storedFoods.size());
        int returned = 0;
        int remainingCount = 0;
        for (ItemStack stored : storedFoods) {
            ItemStack leftover = stored.copy();
            leftover = ItemHandlerHelper.insertItemStacked(handler, leftover, false);
            if (leftover.isEmpty()) {
                returned += stored.getCount();
            } else {
                // 修复：leftover 已经是新对象，无需再 copy
                remaining.add(leftover);
                remainingCount += leftover.getCount();
                returned += stored.getCount() - leftover.getCount();
            }
        }
        
        // 修复：即使一个都没放进去，也要正确处理
        if (returned == 0) {
            // 一个都没放进去，返回失败
            return StorageActionResult.returnFailed(HardtackFoodStorage.getFoodCount(bag));
        }
        
        // 修复：有部分或全部放进去了，更新饼干内容
        HardtackFoodStorage.writeItems(bag, remaining);
        // 返回成功结果，显示"已归还X种，剩余Y种"
        return StorageActionResult.returned(returned, remainingCount);
    }

    public static void setStoredFoodsForCompat(ItemStack bag, List<ItemStack> foods) {
        HardtackFoodStorage.writeItems(bag, foods);
    }

    private static void setFoodBlacklist(ItemStack bag, List<ItemStack> foods) {
        ListTag list = new ListTag();
        Set<ResourceLocation> seen = new HashSet<>();
        for (int slot = 0; slot < foods.size() && slot < FOOD_BLACKLIST_SLOTS; slot++) {
            ItemStack food = foods.get(slot);
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(food.getItem());
            if (food.isEmpty() || id == null || seen.contains(id) || !isSupportedBlacklistFood(food)) continue;
            ItemStack copy = food.copy();
            copy.setCount(1);
            CompoundTag entry = copy.save(new CompoundTag());
            entry.putInt("Slot", slot);
            list.add(entry);
            seen.add(id);
        }
        if (list.isEmpty()) {
            if (bag.hasTag()) bag.getTag().remove(FOOD_BLACKLIST_TAG);
        } else {
            bag.getOrCreateTag().put(FOOD_BLACKLIST_TAG, list);
        }
    }

    private static boolean isSameItem(ItemStack first, ItemStack second) {
        ResourceLocation firstId = ForgeRegistries.ITEMS.getKey(first.getItem());
        ResourceLocation secondId = ForgeRegistries.ITEMS.getKey(second.getItem());
        return firstId != null && firstId.equals(secondId);
    }

}
