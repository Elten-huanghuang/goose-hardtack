package com.luckgoose.hardtack.client.screen;

import com.luckgoose.hardtack.item.GooseFoodBagItem;
import com.luckgoose.hardtack.menu.GooseFoodBlacklistMenu;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI Ghost Ingredient 处理器
 * 
 * <p>处理从 JEI（Just Enough Items）拖拽食物图标到黑名单槽位的操作。
 * 
 * <p><b>功能：</b>
 * <ul>
 *   <li>定义可拖拽目标区域（27 个黑名单槽位）</li>
 *   <li>验证拖拽的物品是否为有效食物</li>
 *   <li>接受拖拽并标记到黑名单</li>
 * </ul>
 * 
 * <p><b>用户体验：</b>
 * <ol>
 *   <li>玩家在 JEI 中看到食物</li>
 *   <li>按住左键拖拽食物图标到黑名单槽位</li>
 *   <li>释放鼠标，食物自动标记到黑名单</li>
 * </ol>
 * 
 * <p><b>实现细节：</b>
 * <ul>
 *   <li>只接受 {@link ItemStack} 类型的 ingredient</li>
 *   <li>只接受通过 {@link GooseFoodBagItem#isSupportedBlacklistFood} 验证的食物</li>
 *   <li>为每个黑名单槽位创建一个拖拽目标</li>
 * </ul>
 * 
 * @author LuckGoose
 * @see GooseFoodBlacklistScreen#markFromJei 标记入口
 * @see IGhostIngredientHandler JEI Ghost Ingredient API
 * @since 1.0.0
 */
public class GooseFoodBlacklistGhostIngredientHandler implements IGhostIngredientHandler<GooseFoodBlacklistScreen> {

    /**
     * 获取可拖拽的目标区域列表
     * 
     * <p>为 27 个黑名单槽位创建拖拽目标，只接受有效的食物。
     * 
     * @param gui 黑名单配置界面
     * @param ingredient 拖拽的 ingredient（JEI 中的物品）
     * @param doStart 是否开始拖拽（true = 开始，false = 结束）
     * @param <I> Ingredient 类型
     * @return 拖拽目标列表
     */
    @Override
    public <I> List<Target<I>> getTargetsTyped(GooseFoodBlacklistScreen gui, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        
        // 只接受 ItemStack 类型且为有效食物
        if (!(ingredient.getIngredient() instanceof ItemStack stack) || !GooseFoodBagItem.isSupportedBlacklistFood(stack)) return targets;
        
        // 为每个黑名单槽位创建拖拽目标
        for (int i = 0; i < GooseFoodBlacklistMenu.BLACKLIST_SLOTS; i++) {
            Slot slot = gui.getMenu().slots.get(i);
            Rect2i bounds = new Rect2i(gui.getGuiLeft() + slot.x, gui.getGuiTop() + slot.y, 17, 17);
            int targetSlot = i;
            
            targets.add(new Target<>() {
                /**
                 * 获取拖拽目标区域
                 * 
                 * @return 槽位的矩形区域（17x17 像素）
                 */
                @Override
                public Rect2i getArea() {
                    return bounds;
                }

                /**
                 * 接受拖拽的 ingredient
                 * 
                 * <p>当玩家释放鼠标时调用，将食物标记到黑名单。
                 * 
                 * @param ingredient 拖拽的 ingredient
                 */
                @Override
                public void accept(I ingredient) {
                    if (ingredient instanceof ItemStack accepted) {
                        gui.markFromJei(targetSlot, accepted);
                    }
                }
            });
        }
        return targets;
    }

    /**
     * 拖拽完成回调
     * 
     * <p>当拖拽操作完成时调用。本实现无需处理。
     */
    @Override
    public void onComplete() {
    }
}
