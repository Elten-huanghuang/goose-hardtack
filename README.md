# Goose Hardtack

一个为 Minecraft 1.20.1 Forge 制作的压缩饼干模组，帮助你快速解锁 Spice of Life: Carrot Edition 的食物里程碑。

## 简介

大鹅的压缩饼干是一个魔法物品，可以从各种容器中收集未品尝过的食物，并支持批量进食，让你轻松获得生命值加成。

### 核心功能

- **智能收集**：左键点击任意容器（箱子、桶等），自动收集其中所有未品尝过的食物（每种 1 个）
- **批量进食**：右键长按 3 秒，自动连续进食压缩饼干内的所有食物
- **黑名单配置**：潜行状态下右键打开黑名单界面，设置不想收集的食物（27 个槽位）
- **食物归还**：对已收集过的容器左键点击，可将食物归还回去
- **Refined Storage 支持**：可直接从 RS 网络中收集和归还食物

### 使用方法

1. **收集食物**
   - 手持压缩饼干，左键点击箱子、桶等容器
   - 自动收集容器中所有未品尝的食物（每种 1 个）
   - 如果压缩饼干已有食物，左键可归还食物到容器
2. **批量进食**
   - 右键并长按压缩饼干 3 秒
   - 自动连续进食所有收集的食物
   - 完成后获得对应的最大生命值加成
3. **配置黑名单**
   - 潜行状态下右键压缩饼干
   - 将不想收集的食物放入黑名单界面（最多 27 种）
   - 收集时会自动跳过黑名单中的食物
4. **管理命令**（需要管理员权限）
   - `/goose hardtack unlock <food>` - 标记指定食物为已品尝
   - `/goose hardtack unlock all` - 解锁所有食物进度
   - `/goose hardtack reset <food>` - 重置指定食物的品尝记录
   - `/goose hardtack reset all` - 重置所有食物品尝记录

## 依赖

### 必需模组

- **Minecraft** 1.20.1
- **Forge** 47.3.0+
- **[Spice of Life: Carrot Edition](https://www.curseforge.com/minecraft/mc-mods/spice-of-life-carrot-edition)** 1.15.0+

### 可选模组

- **[JEI (Just Enough Items)](https://www.curseforge.com/minecraft/mc-mods/jei)** 15.0.0+ - 提供合成配方查询支持
- **[Refined Storage](https://www.curseforge.com/minecraft/mc-mods/refined-storage)** 1.12.0+ - 支持直接从 RS 网络收集食物

## 安装

1. 安装 Forge 1.20.1-47.3.0+
2. 下载并安装所有依赖模组
3. 将本模组放入 mods 文件夹

## 配置

配置文件位于 `.minecraft/config/goose/goose-hardtack.toml`

可配置项：

- `foodsPerTick` - 每 tick 消费的食物数量（-1 = 无限制，默认 -1）
- `eatTimeMode` - 进食时长计算模式（cumulative/fixed，默认 cumulative）
- `fixedEatTimeTicks` - 固定进食时长（仅当 eatTimeMode = fixed 时使用，默认 32）
- `maxRsScanEntries` - RS 网络扫描条目上限（默认 5000）
- `maxRsCollectedFoods` - RS 网络收集食物上限（默认 8192）

## 构建

```bash
git clone https://github.com/LuckBigGoose/goose-hardtack.git
cd goose-hardtack
./gradlew build
```

构建完成后，jar 文件位于 `build/libs/` 目录。

## 贡献

欢迎提交 Issue 和 Pull Request。

## 许可证

本项目采用 MIT License - 详见 [LICENSE](LICENSE) 文件

## 作者

**LuckGoose**

