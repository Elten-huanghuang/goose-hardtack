package com.luckgoose.hardtack.command;

import com.luckgoose.hardtack.GooseHardtackMod;
import com.luckgoose.hardtack.compat.SolCarrotCompat;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;
import java.util.List;

/**
 * 管理员命令处理器
 * 
 * <p>提供管理员命令用于直接操作玩家的 SolCarrot 食物进度。
 * 
 * <p><b>命令语法：</b>
 * <pre>
 * /goose hardtack unlock <food> [targets]    - 解锁指定食物
 * /goose hardtack unlock all [targets]       - 解锁所有食物
 * /goose hardtack reset <food> [targets]     - 重置指定食物
 * /goose hardtack reset all [targets]        - 重置所有食物
 * </pre>
 * 
 * <p><b>权限要求：</b>
 * 需要管理员权限（permission level 2）。
 * 
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>服务器管理员奖励玩家</li>
 *   <li>修复玩家进度错误</li>
 *   <li>测试和调试</li>
 * </ul>
 * 
 * @author LuckGoose
 * @see SolCarrotCompat 食物进度操作
 * @since 1.0.0
 */
@Mod.EventBusSubscriber(modid = GooseHardtackMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GooseHardtackCommand {

    private static final String TARGETS = "targets";
    private static final String FOOD = "food";

    /**
     * 注册命令到 Brigadier 调度器
     * 
     * @param event 命令注册事件
     */
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("goose")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("hardtack")
                        .then(Commands.literal("unlock")
                                .then(Commands.literal("all")
                                        .executes(context -> unlockAll(context, self(context)))
                                        .then(Commands.argument(TARGETS, EntityArgument.players())
                                                .executes(context -> unlockAll(context, EntityArgument.getPlayers(context, TARGETS)))))
                                .then(Commands.argument(FOOD, ItemArgument.item(event.getBuildContext()))
                                        .executes(context -> unlockFood(context, self(context), ItemArgument.getItem(context, FOOD)))
                                        .then(Commands.argument(TARGETS, EntityArgument.players())
                                                .executes(context -> unlockFood(context, EntityArgument.getPlayers(context, TARGETS), ItemArgument.getItem(context, FOOD))))))
                        .then(Commands.literal("reset")
                                .then(Commands.literal("all")
                                        .executes(context -> resetAll(context, self(context)))
                                        .then(Commands.argument(TARGETS, EntityArgument.players())
                                                .executes(context -> resetAll(context, EntityArgument.getPlayers(context, TARGETS)))))
                                .then(Commands.argument(FOOD, ItemArgument.item(event.getBuildContext()))
                                        .executes(context -> resetFood(context, self(context), ItemArgument.getItem(context, FOOD)))
                                        .then(Commands.argument(TARGETS, EntityArgument.players())
                                                .executes(context -> resetFood(context, EntityArgument.getPlayers(context, TARGETS), ItemArgument.getItem(context, FOOD))))))));
    }

    /**
     * 获取命令执行者自身
     * 
     * @param context 命令上下文
     * @return 单个玩家的集合
     * @throws CommandSyntaxException 如果执行者不是玩家
     */
    private static Collection<ServerPlayer> self(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return List.of(context.getSource().getPlayerOrException());
    }

    /**
     * 解锁指定食物
     * 
     * @param context 命令上下文
     * @param players 目标玩家集合
     * @param input 食物参数
     * @return 命令执行结果
     */
    private static int unlockFood(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players, ItemInput input) {
        Item item = input.getItem();
        if (!isValidFood(item)) {
            fail(context, Component.translatable("command.goose_hardtack.hardtack.invalid_food", item.getDescription()));
            return 0;
        }
        int changed = 0;
        for (ServerPlayer player : players) {
            if (SolCarrotCompat.unlockFood(player, item)) changed++;
        }
        success(context, Component.translatable("command.goose_hardtack.hardtack.unlock.food", item.getDescription(), changed, players.size()));
        return changed == 0 ? 0 : Command.SINGLE_SUCCESS;
    }

    /**
     * 解锁所有食物
     * 
     * @param context 命令上下文
     * @param players 目标玩家集合
     * @return 命令执行结果
     */
    private static int unlockAll(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players) {
        int changed = 0;
        for (ServerPlayer player : players) {
            changed += SolCarrotCompat.unlockAllFoods(player);
        }
        success(context, Component.translatable("command.goose_hardtack.hardtack.unlock.all", changed, players.size()));
        return changed == 0 ? 0 : Command.SINGLE_SUCCESS;
    }

    /**
     * 重置指定食物
     * 
     * @param context 命令上下文
     * @param players 目标玩家集合
     * @param input 食物参数
     * @return 命令执行结果
     */
    private static int resetFood(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players, ItemInput input) {
        Item item = input.getItem();
        if (!isValidFood(item)) {
            fail(context, Component.translatable("command.goose_hardtack.hardtack.invalid_food", item.getDescription()));
            return 0;
        }
        int changed = 0;
        for (ServerPlayer player : players) {
            if (SolCarrotCompat.resetFood(player, item)) changed++;
        }
        success(context, Component.translatable("command.goose_hardtack.hardtack.reset.food", item.getDescription(), changed, players.size()));
        return changed == 0 ? 0 : Command.SINGLE_SUCCESS;
    }

    /**
     * 重置所有食物
     * 
     * @param context 命令上下文
     * @param players 目标玩家集合
     * @return 命令执行结果
     */
    private static int resetAll(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players) {
        int changed = 0;
        for (ServerPlayer player : players) {
            changed += SolCarrotCompat.resetAllFoods(player);
        }
        success(context, Component.translatable("command.goose_hardtack.hardtack.reset.all", changed, players.size()));
        return changed == 0 ? 0 : Command.SINGLE_SUCCESS;
    }

    /**
     * 验证物品是否为有效的食物
     * 
     * @param item 物品
     * @return 如果是可食用且应计入的食物返回 true
     */
    private static boolean isValidFood(Item item) {
        return item.isEdible() && SolCarrotCompat.shouldCount(item);
    }

    /**
     * 发送成功消息
     * 
     * @param context 命令上下文
     * @param message 消息内容
     */
    private static void success(CommandContext<CommandSourceStack> context, Component message) {
        context.getSource().sendSuccess(() -> message.copy().withStyle(ChatFormatting.DARK_AQUA), true);
    }

    /**
     * 发送失败消息
     * 
     * @param context 命令上下文
     * @param message 消息内容
     */
    private static void fail(CommandContext<CommandSourceStack> context, Component message) {
        context.getSource().sendFailure(message.copy().withStyle(ChatFormatting.RED));
    }
}
