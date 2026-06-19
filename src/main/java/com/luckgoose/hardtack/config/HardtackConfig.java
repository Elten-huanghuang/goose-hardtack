package com.luckgoose.hardtack.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 压缩饼干配置管理器
 * 
 * <p>管理 mod 的服务端配置选项，包括性能控制和行为调整。
 * 
 * <p><b>配置文件位置：</b>
 * {@code config/goose_hardtack-server.toml}
 * 
 * <p><b>配置项：</b>
 * <ul>
 *   <li><b>foodsPerTick</b>：每 tick 消费的食物数量（-1 = 无限制）</li>
 *   <li><b>eatTimeMode</b>：进食时长计算模式（cumulative/fixed）</li>
 *   <li><b>fixedEatTimeTicks</b>：固定进食时长（当 eatTimeMode = fixed）</li>
 *   <li><b>maxRsScanEntries</b>：RS 网络扫描条目上限（-1 = 无限制）</li>
 *   <li><b>maxRsCollectedFoods</b>：RS 网络收集食物上限（-1 = 无限制）</li>
 * </ul>
 * 
 * @author LuckGoose
 * @see com.luckgoose.hardtack.storage.HardtackEatingSessions 进食会话管理
 * @see com.luckgoose.hardtack.compat.RefinedStorageCompat RS 兼容层
 * @since 1.0.0
 */
public final class HardtackConfig {

    /** 服务端配置规范 */
    public static final ForgeConfigSpec SERVER_SPEC;
    
    /** 每 tick 消费的食物数量（-1 = 无限制，默认 -1） */
    public static final ForgeConfigSpec.IntValue FOODS_PER_TICK;
    
    /** 进食时长计算模式（cumulative 或 fixed，默认 cumulative） */
    public static final ForgeConfigSpec.ConfigValue<String> EAT_TIME_MODE;
    
    /** 固定进食时长（ticks，仅当 eatTimeMode = fixed 时使用，默认 32） */
    public static final ForgeConfigSpec.IntValue FIXED_EAT_TIME_TICKS;
    
    /** RS 网络扫描条目上限（-1 = 无限制，默认 5000） */
    public static final ForgeConfigSpec.IntValue MAX_RS_SCAN_ENTRIES;
    
    /** RS 网络收集食物上限（-1 = 无限制，默认 8192） */
    public static final ForgeConfigSpec.IntValue MAX_RS_COLLECTED_FOODS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("performance");
        
        FOODS_PER_TICK = builder
                .comment("Maximum foods consumed per server tick while eating hardtack. Set to -1 for no limit.")
                .defineInRange("foodsPerTick", -1, -1, Integer.MAX_VALUE);
        
        EAT_TIME_MODE = builder
                .comment("How hardtack use duration is calculated. Allowed values: cumulative, fixed. cumulative sums all stored food use times; fixed uses a single configured duration.")
                .define("eatTimeMode", "cumulative", value -> "cumulative".equals(value) || "fixed".equals(value));
        
        FIXED_EAT_TIME_TICKS = builder
                .comment("Use duration in ticks when eatTimeMode is fixed.")
                .defineInRange("fixedEatTimeTicks", 32, 1, Integer.MAX_VALUE);
        
        MAX_RS_SCAN_ENTRIES = builder
                .comment("Maximum Refined Storage entries scanned per collect action. Set to -1 for no limit. Lowered default to prevent lag. Max: 100000 to prevent OOM.")
                .defineInRange("maxRsScanEntries", 5000, -1, 100000);
        
        MAX_RS_COLLECTED_FOODS = builder
                .comment("Maximum foods collected from Refined Storage per collect action. Set to -1 for no limit. Aligned with memory limits. Max: 50000 to prevent OOM.")
                .defineInRange("maxRsCollectedFoods", 8192, -1, 50000);
        
        builder.pop();
        SERVER_SPEC = builder.build();
    }

    private HardtackConfig() {
    }
}
