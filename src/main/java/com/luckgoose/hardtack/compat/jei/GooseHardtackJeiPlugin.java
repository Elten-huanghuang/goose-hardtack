package com.luckgoose.hardtack.compat.jei;

import com.luckgoose.hardtack.GooseHardtackMod;
import com.luckgoose.hardtack.client.screen.GooseFoodBlacklistGhostIngredientHandler;
import com.luckgoose.hardtack.client.screen.GooseFoodBlacklistScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI（Just Enough Items）插件
 * 
 * <p>将黑名单配置界面与 JEI 集成，提供 Ghost Ingredient 拖拽功能。
 * 
 * <p><b>功能：</b>
 * <ul>
 *   <li>注册 {@link GooseFoodBlacklistScreen} 的 Ghost Ingredient 处理器</li>
 *   <li>允许玩家从 JEI 拖拽食物图标到黑名单槽位</li>
 * </ul>
 * 
 * <p><b>使用方式：</b>
 * <ol>
 *   <li>玩家打开黑名单配置界面</li>
 *   <li>同时打开 JEI 物品列表（通常按 E 键）</li>
 *   <li>在 JEI 中找到食物</li>
 *   <li>按住左键拖拽食物图标到黑名单槽位</li>
 *   <li>释放鼠标，食物自动标记</li>
 * </ol>
 * 
 * <p><b>技术细节：</b>
 * <ul>
 *   <li>使用 {@link JeiPlugin} 注解自动注册</li>
 *   <li>插件 UID：{@code goose_hardtack:jei_plugin}</li>
 *   <li>实现 {@link IModPlugin} 接口</li>
 * </ul>
 * 
 * @author LuckGoose
 * @see GooseFoodBlacklistGhostIngredientHandler Ghost 拖拽处理器
 * @see GooseFoodBlacklistScreen 黑名单配置界面
 * @since 1.0.0
 */
@JeiPlugin
public class GooseHardtackJeiPlugin implements IModPlugin {

    /** 插件唯一标识符 */
    private static final ResourceLocation UID = new ResourceLocation(GooseHardtackMod.MOD_ID, "jei_plugin");

    /**
     * 获取插件唯一标识符
     * 
     * @return 插件 UID（goose_hardtack:jei_plugin）
     */
    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    /**
     * 注册 GUI 处理器
     * 
     * <p>为黑名单配置界面注册 Ghost Ingredient 处理器，
     * 使其支持从 JEI 拖拽物品图标。
     * 
     * @param registration GUI 处理器注册接口
     */
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(GooseFoodBlacklistScreen.class, new GooseFoodBlacklistGhostIngredientHandler());
    }
}
