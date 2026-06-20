package com.luckgoose.hardtack.client;

import com.luckgoose.hardtack.GooseHardtackMod;
import com.luckgoose.hardtack.init.ModItems;
import com.luckgoose.hardtack.item.GooseFoodBagItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = GooseHardtackMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientForgeEvents {

    private static final int AUTO_EAT_TRIGGER_TICKS = 60;
    private static final int AUTO_EAT_STATUS_REFRESH_TICKS = 40;

    private static int foodBagUseTickCount;
    private static boolean autoEatRunning;
    private static int autoEatTrackedSlot = -1;
    private static int autoEatStatusRefreshTimer;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            if (autoEatRunning) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null && !minecraft.player.isDeadOrDying()) {
                    minecraft.options.keyUse.setDown(true);
                }
            }
            return;
        }
        ShortStatusMessageClient.tick();
        tickAutoEat();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        ShortStatusMessageClient.render(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!autoEatRunning) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        int key = event.getKey();
        if (key == GLFW.GLFW_KEY_W || key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_S || key == GLFW.GLFW_KEY_D) return;
        if (key == GLFW.GLFW_KEY_SPACE) return;
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT) return;
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL) return;
        interruptAutoEat();
    }

    /**
     * 自动进食逻辑处理（每客户端 tick 调用）
     * 
     * <p><b>状态机：</b>
     * <ol>
     *   <li><b>未激活</b>：检测玩家是否开始使用饼干</li>
     *   <li><b>计时中</b>：累加使用时间，达到 60 ticks 后激活自动保持</li>
     *   <li><b>自动保持</b>：模拟按键，定期刷新提示消息，检测中断条件</li>
     * </ol>
     * 
     * <p><b>中断检测：</b>
     * <ul>
     *   <li>玩家松开右键 → 中断</li>
     *   <li>按下左键 → 中断</li>
     *   <li>潜行 → 中断</li>
     *   <li>切换槽位 → 中断</li>
     *   <li>饼干耗尽 → 完成</li>
     * </ul>
     */
    private static void tickAutoEat() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return;

        int currentSlot = player.getInventory().selected;
        boolean isUsingFoodBag = player.isUsingItem() && isHoldingFoodBagWithFoods(player);

        // 自动保持模式
        if (autoEatRunning) {
            // 中断条件检测
            if (player.isShiftKeyDown()) { interruptAutoEat(); return; }
            if (!minecraft.options.keyUse.isDown()) { interruptAutoEat(); return; }
            if (currentSlot != autoEatTrackedSlot) { interruptAutoEat(); return; }
            if (minecraft.options.keyAttack.isDown()) { interruptAutoEat(); return; }
            if (!isHoldingFoodBagWithFoods(player)) { finishAutoEat(); return; }

            // 保持按键状态
            minecraft.options.keyUse.setDown(true);

            // 定期刷新状态消息
            if (isUsingFoodBag) {
                autoEatStatusRefreshTimer--;
                if (autoEatStatusRefreshTimer <= 0) {
                    autoEatStatusRefreshTimer = AUTO_EAT_STATUS_REFRESH_TICKS;
                    ShortStatusMessageClient.show(
                            Component.translatable("item.goose_hardtack.goose_hardtack.auto_eat.hold"),
                            AUTO_EAT_STATUS_REFRESH_TICKS + 10);
                }
            }
            return;
        }

        // 计时模式：累加使用时间
        if (isUsingFoodBag) {
            if (foodBagUseTickCount == 0) {
                autoEatTrackedSlot = currentSlot;
            }
            foodBagUseTickCount++;

            // 达到 3 秒：激活自动保持
            if (foodBagUseTickCount >= AUTO_EAT_TRIGGER_TICKS) {
                autoEatRunning = true;
                autoEatStatusRefreshTimer = AUTO_EAT_STATUS_REFRESH_TICKS;
                ShortStatusMessageClient.show(
                        Component.translatable("item.goose_hardtack.goose_hardtack.auto_eat.hold"),
                        AUTO_EAT_STATUS_REFRESH_TICKS + 10);
            }
        } else {
            // 未使用：重置计时
            foodBagUseTickCount = 0;
            autoEatTrackedSlot = -1;
        }
    }

    /**
     * 中断自动进食（用户主动中断）
     * 
     * <p>显示"自动进食状态已中断"消息。
     */
    private static void interruptAutoEat() {
        autoEatRunning = false;
        foodBagUseTickCount = 0;
        autoEatTrackedSlot = -1;
        Minecraft.getInstance().options.keyUse.setDown(false);
        ShortStatusMessageClient.show(
                Component.translatable("item.goose_hardtack.goose_hardtack.auto_eat.interrupted"), 20);
    }

    /**
     * 完成自动进食（饼干耗尽）
     * 
     * <p>显示"自动进食完成"消息。
     */
    private static void finishAutoEat() {
        autoEatRunning = false;
        foodBagUseTickCount = 0;
        autoEatTrackedSlot = -1;
        Minecraft.getInstance().options.keyUse.setDown(false);
        ShortStatusMessageClient.show(
                Component.translatable("item.goose_hardtack.goose_hardtack.auto_eat.done"), 20);
    }

    /**
     * 重置自动进食状态（无消息提示）
     *
     * <p>与 {@link #interruptAutoEat()} 和 {@link #finishAutoEat()} 不同，此方法不会向玩家显示任何提示消息。
     *
     * <p><b>适用场景：</b>
     * <ul>
     *   <li>玩家按 ESC 键关闭界面时，自动进食应静默终止</li>
     *   <li>其他需要静默终止自动进食的场景</li>
     * </ul>
     *
     * <p><b>注意：</b>此方法当前未被任何调用方使用，保留作为 API 供 future 集成点使用。
     * 如需在特定场景下静默停止自动进食，可调用此方法。
     */
    private static void resetAutoEat() {
        if (autoEatRunning) {
            Minecraft.getInstance().options.keyUse.setDown(false);
        }
        autoEatRunning = false;
        foodBagUseTickCount = 0;
        autoEatTrackedSlot = -1;
    }

    /**
     * 检查玩家是否手持有食物的饼干
     * 
     * @param player 本地玩家
     * @return 如果主手或副手持有饼干且有食物返回 true
     */
    private static boolean isHoldingFoodBagWithFoods(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(ModItems.GOOSE_HARDTACK.get()) && GooseFoodBagItem.hasStoredFoods(main)) return true;
        ItemStack off = player.getOffhandItem();
        return off.is(ModItems.GOOSE_HARDTACK.get()) && GooseFoodBagItem.hasStoredFoods(off);
    }
}
