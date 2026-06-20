package com.luckgoose.hardtack.menu;

import com.luckgoose.hardtack.init.ModItems;
import com.luckgoose.hardtack.init.ModMenuTypes;
import com.luckgoose.hardtack.item.GooseFoodBagItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 黑名单配置容器菜单
 * 
 * <p>提供 27 格黑名单槽位（3 行 × 9 列），用于配置哪些食物不应被自动收集。
 * 
 * <p><b>界面布局：</b>
 * <pre>
 * ┌─────────────────────────────────┐
 * │  黑名单槽位（27 格，只能放食物） │
 * │  ■ ■ ■ ■ ■ ■ ■ ■ ■             │
 * │  ■ ■ ■ ■ ■ ■ ■ ■ ■             │
 * │  ■ ■ ■ ■ ■ ■ ■ ■ ■             │
 * ├─────────────────────────────────┤
 * │  玩家背包（27 格）               │
 * │  ■ ■ ■ ■ ■ ■ ■ ■ ■             │
 * │  ■ ■ ■ ■ ■ ■ ■ ■ ■             │
 * │  ■ ■ ■ ■ ■ ■ ■ ■ ■             │
 * ├─────────────────────────────────┤
 * │  快捷栏（9 格）                  │
 * │  ■ ■ ■ ■ 🎒 ■ ■ ■ ■  ← 饼干槽位锁定 │
 * └─────────────────────────────────┘
 * </pre>
 * 
 * <p><b>交互方式：</b>
 * <ul>
 *   <li><b>左键点击</b>：标记/取消标记食物</li>
 *   <li><b>JEI Ghost</b>：从 JEI 拖拽食物图标到槽位</li>
 *   <li><b>槽位限制</b>：
 *     <ul>
 *       <li>黑名单槽位：只能放食物，数量固定为 1，不可取出</li>
 *       <li>饼干槽位：完全锁定，不可移动或替换</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <p><b>数据同步：</b>
 * <ul>
 *   <li>打开界面时从饼干 NBT 加载黑名单</li>
 *   <li>每次点击时通过 {@link com.luckgoose.hardtack.network.FoodBlacklistMarkPacket} 同步到服务端</li>
 *   <li>关闭界面时自动保存到饼干 NBT</li>
 * </ul>
 * 
 * @author LuckGoose
 * @see GooseFoodBagItem#getFoodBlacklist() 黑名单数据读取
 * @see com.luckgoose.hardtack.network.OpenFoodBlacklistPacket 打开界面
 * @see com.luckgoose.hardtack.client.screen.GooseFoodBlacklistScreen GUI 渲染
 * @since 1.0.0
 */
public class GooseFoodBlacklistMenu extends AbstractContainerMenu {

    /** 黑名单槽位数量（27 格） */
    public static final int BLACKLIST_SLOTS = GooseFoodBagItem.FOOD_BLACKLIST_SLOTS;
    
    /** 黑名单行数（3 行） */
    public static final int BLACKLIST_ROWS = 3;
    
    /** 黑名单列数（9 列） */
    public static final int BLACKLIST_COLUMNS = 9;
    public static final int PLAYER_INVENTORY_Y = 85;

    private final Inventory playerInventory;
    private final int bagSlot;
    private final ItemStack bagStack;
    private final SimpleContainer blacklistContainer;
    private final boolean validBag;
    private Slot lockedBagSlot;

    public GooseFoodBlacklistMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readVarInt());
    }

    public GooseFoodBlacklistMenu(int containerId, Inventory playerInventory, int bagSlot) {
        super(ModMenuTypes.GOOSE_HARDTACK_BLACKLIST.get(), containerId);
        this.playerInventory = playerInventory;
        this.bagSlot = bagSlot;
        this.bagStack = getBagStack(playerInventory, bagSlot);
        this.validBag = !this.bagStack.isEmpty();
        this.blacklistContainer = new SimpleContainer(BLACKLIST_SLOTS);
        java.util.List<ItemStack> blacklist = GooseFoodBagItem.getFoodBlacklist(this.bagStack);
        for (int slot = 0; slot < BLACKLIST_SLOTS; slot++) {
            this.blacklistContainer.setItem(slot, blacklist.get(slot).copy());
        }

        int startX = 8;
        int startY = 18;
        for (int row = 0; row < BLACKLIST_ROWS; row++) {
            for (int col = 0; col < BLACKLIST_COLUMNS; col++) {
                this.addSlot(new BlacklistSlot(this.blacklistContainer, col + row * BLACKLIST_COLUMNS, startX + col * 18, startY + row * 18));
            }
        }
        layoutPlayerInventorySlots(playerInventory, 8, PLAYER_INVENTORY_Y);
    }

    private void layoutPlayerInventorySlots(Inventory inventory, int leftCol, int topRow) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addPlayerSlot(inventory, col + row * 9 + 9, leftCol + col * 18, topRow + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            addPlayerSlot(inventory, col, leftCol + col * 18, topRow + 58);
        }
    }

    private void addPlayerSlot(Inventory inventory, int slot, int x, int y) {
        Slot added = this.addSlot(slot == this.bagSlot ? new LockedBagSlot(inventory, slot, x, y) : new Slot(inventory, slot, x, y));
        if (slot == this.bagSlot) this.lockedBagSlot = added;
    }

    private static ItemStack getBagStack(Inventory inventory, int slot) {
        if (slot >= 0 && slot < inventory.getContainerSize()) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.GOOSE_HARDTACK.get())) return stack;
        }
        return ItemStack.EMPTY;
    }

    public boolean markFood(int slot, ItemStack stack) {
        if (!this.validBag || slot < 0 || slot >= BLACKLIST_SLOTS) return false;
        boolean changed = GooseFoodBagItem.setFoodBlacklistEntry(this.bagStack, slot, stack);
        if (changed) {
            this.blacklistContainer.setItem(slot, GooseFoodBagItem.getFoodBlacklistEntry(this.bagStack, slot));
            this.broadcastChanges();
        }
        return changed;
    }

    public boolean clearFood(int slot) {
        return markFood(slot, ItemStack.EMPTY);
    }

    public int getBagSlot() {
        return this.bagSlot;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        if (!this.validBag) return false;
        return this.bagSlot >= 0 && this.bagSlot < this.playerInventory.getContainerSize() && this.playerInventory.getItem(this.bagSlot).is(ModItems.GOOSE_HARDTACK.get());
    }

    @Override
    public void clicked(int slotId, int button, @NotNull ClickType clickType, @NotNull Player player) {
        if (!this.validBag) return;
        if (isBlockedClick(slotId)) return;
        if (slotId >= 0 && slotId < BLACKLIST_SLOTS && clickType == ClickType.PICKUP) {
            ItemStack carried = this.getCarried();
            if (button == 0 && !carried.isEmpty()) {
                markFood(slotId, carried);
                return;
            }
            if (button == 0 && carried.isEmpty()) {
                clearFood(slotId);
                return;
            }
        }
        if (slotId >= 0 && slotId < BLACKLIST_SLOTS) return;
        super.clicked(slotId, button, clickType, player);
    }

    private boolean isBlockedClick(int slotId) {
        if (this.getCarried() == this.bagStack) return true;
        if (slotId >= 0 && slotId < this.slots.size()) {
            Slot clickedSlot = this.slots.get(slotId);
            return isOpenedBagSlot(clickedSlot);
        }
        return false;
    }

    private boolean isOpenedBagSlot(Slot slot) {
        return slot == this.lockedBagSlot || slot != null && slot.container == this.playerInventory && slot.getContainerSlot() == this.bagSlot;
    }

    private static class BlacklistSlot extends Slot {

        public BlacklistSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return GooseFoodBagItem.isSupportedBlacklistFood(stack);
        }

        @Override
        public boolean mayPickup(@NotNull Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private static class LockedBagSlot extends Slot {

        public LockedBagSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPickup(@NotNull Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return false;
        }

        @Override
        public boolean allowModification(@NotNull Player player) {
            return false;
        }
    }
}
