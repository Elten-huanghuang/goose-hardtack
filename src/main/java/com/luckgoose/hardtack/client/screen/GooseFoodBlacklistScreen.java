package com.luckgoose.hardtack.client.screen;

import com.luckgoose.hardtack.item.GooseFoodBagItem;
import com.luckgoose.hardtack.menu.GooseFoodBlacklistMenu;
import com.luckgoose.hardtack.network.FoodBlacklistMarkPacket;
import com.luckgoose.hardtack.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 黑名单配置界面屏幕
 * 
 * <p>渲染黑名单配置界面的客户端 GUI，提供可视化的食物黑名单编辑功能。
 * 
 * <p><b>界面特性：</b>
 * <ul>
 *   <li><b>手动标记</b>：左键点击槽位放入手持的食物</li>
 *   <li><b>JEI 集成</b>：支持从 JEI 拖拽食物图标到槽位（Ghost Ingredient）</li>
 *   <li><b>视觉反馈</b>：
 *     <ul>
 *       <li>绿色高亮：手持的是有效食物</li>
 *       <li>红色高亮：手持的不是食物或不可计入</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <p><b>颜色定义：</b>
 * <ul>
 *   <li>背景色：#C6C6C6（浅灰）</li>
 *   <li>槽位内部：#8B8B8B（中灰）</li>
 *   <li>槽位阴影/边框：#373737（深灰）</li>
 *   <li>槽位高光：#FFFFFF（白色）</li>
 *   <li>接受反馈：40% 透明绿色 (#30D158)</li>
 *   <li>拒绝反馈：40% 透明红色 (#E03131)</li>
 * </ul>
 * 
 * <p><b>界面布局：</b>
 * <ul>
 *   <li>标题位置：Y = 6</li>
 *   <li>黑名单槽位：3 行 × 9 列，起始于 (7, 17)</li>
 *   <li>背包标签：Y = 黑名单底部 - 11</li>
 *   <li>玩家背包：3 行 × 9 列</li>
 *   <li>快捷栏：1 行 × 9 列</li>
 * </ul>
 * 
 * @author LuckGoose
 * @see GooseFoodBlacklistMenu 容器菜单
 * @see GooseFoodBlacklistGhostIngredientHandler JEI Ghost 拖拽处理
 * @since 1.0.0
 */
public class GooseFoodBlacklistScreen extends AbstractContainerScreen<GooseFoodBlacklistMenu> {

    /** 背景色（浅灰） */
    private static final int BG = 0xFF_C6_C6_C6;
    
    /** 槽位内部颜色（中灰） */
    private static final int SLOT_INNER = 0xFF_8B_8B_8B;
    
    /** 槽位高光颜色（白色） */
    private static final int SLOT_HIGHLIGHT = 0xFF_FF_FF_FF;
    
    /** 槽位阴影颜色（深灰） */
    private static final int SLOT_SHADOW = 0xFF_37_37_37;
    
    /** 边框颜色（深灰） */
    private static final int BORDER = 0xFF_37_37_37;
    
    /** 接受提示颜色（40% 透明绿色） */
    private static final int ACCEPT = 0x66_30_D1_58;
    
    /** 拒绝提示颜色（40% 透明红色） */
    private static final int REJECT = 0x66_E0_31_31;

    /**
     * 构造黑名单配置界面
     * 
     * @param menu 容器菜单
     * @param inventory 玩家背包
     * @param title 界面标题
     */
    public GooseFoodBlacklistScreen(GooseFoodBlacklistMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelY = 6;
        this.inventoryLabelY = GooseFoodBlacklistMenu.PLAYER_INVENTORY_Y - 11;
    }

    /**
     * 渲染背景和槽位
     * 
     * <p>绘制界面背景、边框和所有槽位（黑名单 + 背包 + 快捷栏）。
     * 
     * @param g GUI 图形上下文
     * @param partialTick 部分 tick（用于插值）
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     */
    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        
        // 绘制背景
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, BG);
        
        // 绘制边框
        g.fill(x, y, x + imageWidth, y + 1, BORDER);
        g.fill(x, y, x + 1, y + imageHeight, BORDER);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, BORDER);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, BORDER);
        
        // 绘制黑名单槽位（3 行 × 9 列）
        for (int row = 0; row < GooseFoodBlacklistMenu.BLACKLIST_ROWS; row++) {
            for (int col = 0; col < GooseFoodBlacklistMenu.BLACKLIST_COLUMNS; col++) {
                drawSlot(g, x + 7 + col * 18, y + 17 + row * 18);
            }
        }
        
        // 绘制背包槽位（3 行 × 9 列）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(g, x + 7 + col * 18, y + GooseFoodBlacklistMenu.PLAYER_INVENTORY_Y - 1 + row * 18);
            }
        }
        
        // 绘制快捷栏槽位（1 行 × 9 列）
        for (int col = 0; col < 9; col++) {
            drawSlot(g, x + 7 + col * 18, y + GooseFoodBlacklistMenu.PLAYER_INVENTORY_Y - 1 + 58);
        }
        
        // 绘制悬停提示（绿色/红色高亮）
        drawHoveredBlacklistHint(g, mouseX, mouseY);
    }

    /**
     * 渲染整个界面
     * 
     * <p>按顺序渲染：背景 → 槽位和物品 → Tooltip。
     * 
     * @param g GUI 图形上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param partialTick 部分 tick（用于插值）
     */
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    /**
     * 鼠标释放事件处理
     * 
     * <p>当玩家左键释放时，如果鼠标在黑名单槽位上且手持食物，
     * 则将食物标记到黑名单。
     * 
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param button 鼠标按键（0 = 左键，1 = 右键）
     * @return 是否处理了该事件
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Slot slot = findBlacklistSlot(mouseX, mouseY);
            ItemStack carried = this.menu.getCarried();
            if (slot != null && !carried.isEmpty()) {
                mark(slot.index, carried);
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * 从 JEI 标记食物（Ghost Ingredient）
     * 
     * <p>当玩家从 JEI 拖拽食物图标到黑名单槽位时调用。
     * 
     * @param slot 槽位索引
     * @param stack 食物物品
     */
    public void markFromJei(int slot, ItemStack stack) {
        mark(slot, stack);
    }

    /**
     * 标记食物到黑名单
     * 
     * <p>验证食物有效性，创建副本（数量强制为 1），
     * 更新客户端菜单并发送网络包到服务端同步。
     * 
     * @param slot 槽位索引
     * @param stack 食物物品
     */
    private void mark(int slot, ItemStack stack) {
        if (!GooseFoodBagItem.isSupportedBlacklistFood(stack)) return;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        this.menu.markFood(slot, copy);
        ModNetwork.CHANNEL.sendToServer(new FoodBlacklistMarkPacket(slot, copy));
    }

    /**
     * 查找鼠标位置的黑名单槽位
     * 
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @return 黑名单槽位，如果不在任何槽位上返回 null
     */
    private Slot findBlacklistSlot(double mouseX, double mouseY) {
        for (int i = 0; i < GooseFoodBlacklistMenu.BLACKLIST_SLOTS; i++) {
            Slot slot = this.menu.slots.get(i);
            int x = this.leftPos + slot.x;
            int y = this.topPos + slot.y;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) return slot;
        }
        return null;
    }

    /**
     * 绘制悬停槽位的视觉反馈
     * 
     * <p>当鼠标悬停在黑名单槽位上且玩家手持物品时，
     * 显示颜色提示：
     * <ul>
     *   <li>绿色：有效食物，可以标记</li>
     *   <li>红色：无效物品，不可标记</li>
     * </ul>
     * 
     * @param g GUI 图形上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     */
    private void drawHoveredBlacklistHint(GuiGraphics g, int mouseX, int mouseY) {
        Slot slot = findBlacklistSlot(mouseX, mouseY);
        if (slot == null) return;
        ItemStack carried = this.menu.getCarried();
        if (carried.isEmpty()) return;
        int color = GooseFoodBagItem.isSupportedBlacklistFood(carried) ? ACCEPT : REJECT;
        g.fill(this.leftPos + slot.x, this.topPos + slot.y, this.leftPos + slot.x + 16, this.topPos + slot.y + 16, color);
    }

    /**
     * 绘制单个槽位
     * 
     * <p>绘制带有 3D 效果的槽位（内部 + 阴影 + 高光）。
     * 
     * @param g GUI 图形上下文
     * @param x 槽位 X 坐标
     * @param y 槽位 Y 坐标
     */
    private void drawSlot(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_INNER);
        g.fill(x, y, x + 18, y + 1, SLOT_SHADOW);
        g.fill(x, y, x + 1, y + 18, SLOT_SHADOW);
        g.fill(x, y + 17, x + 18, y + 18, SLOT_HIGHLIGHT);
        g.fill(x + 17, y, x + 18, y + 18, SLOT_HIGHLIGHT);
    }
}
