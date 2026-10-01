![Logo](./.github/assets/logo_open_source.png)

# BedWars1058 25.2 中文修复版（fix 分支）

本仓库是 [andrei1058/BedWars1058](https://github.com/andrei1058/BedWars1058) **25.2** 的修复分支。官方原版自 2021 年 11 月 1 日起以 **GNU GPL 3.0** 协议开源，如果你是开发者，欢迎直接向上游提交 pull request，让所有人都能用上更新，而不是做几百个 fork。

[![Discord](https://discordapp.com/api/guilds/201345265821679617/widget.png?style=shield)](https://discord.gg/XdJfN2X)

[![Crowdin](https://support.crowdin.com/assets/badges/localization-at-white-rounded-bordered@1x.svg)](https://crowdin.com/project/bedwars1058)

## 这个分支有什么不同

- 只做 **bug 修复**，不改玩法、不改配置格式，尽量与官方 25.2 保持一致（与官方 jar 逐类对比，只有修复涉及的那几个类不同）。
- 修好的 jar 发布在 [Releases](https://github.com/cpy-cpyserver/BedWars1058_fix_pro/releases) 页面，文件名格式为 `bedwars1058-plugin-25.2-fix-<修复版本号>.jar`。
- 当前版本：**1.7**，对应文件 `bedwars1058-plugin-25.2-fix-1.7.jar`。

## 修复列表（相对官方 25.2）

### fix 1.7
- **地图方块保护（水瓶变泥巴、斧头去皮等）**：商店卖鱼竿和水桶，玩家钓鱼钓到水瓶后可以对着地图里的泥土使用，把泥土变成泥巴；同理还能用斧头把地图里的原木变成去皮原木、用锹把草方块变成土径、用锄把泥土变成耕地。这些操作都不触发放置/破坏事件，所以插件原本“只能破坏玩家放置的方块”的保护拦不住，地图会被改坏。现在这些交互会被直接拦下并提示不能破坏（`interact-cant-break`），只有玩家自己放下的方块才能这样改造；地图配置里 `allow-map-break: true`（允许破坏地图）的竞技场不受影响。

### fix 1.6
- **队伍倒计时不会停止**：2 人组队 + 1 个路人把 2v2 地图推进到倒计时后，如果那名路人中途退出，倒计时仍会继续，最后只开出一支队伍（组队的两人）的对局。现在只要场上人数低于地图的 `minPlayers`，**不管场上有没有队伍**，倒计时都会立刻停止并提示玩家不足；队长离开并解散队伍时同样生效。

### fix 1.5
- **玩家重连报错**：修复玩家重连（`PlayerReJoinEvent`）时 `BwTabList` 的空指针异常（此时玩家还没回到队伍，现在按旁观者排序处理）。

### fix 1.4 及更早
- **副手无限刷防御塔**：弹出式防御塔改为从**实际放置方块的那只手**扣物品，不能再把塔物品放在副手、主手拿别的物品来无限放塔。
- **主城随意开关活板门/栅栏门**：大厅（等待阶段）禁止开关活板门与栅栏门，普通门、按钮、拉杆不受影响。
- **烈焰弹炸方块卡服**：`Arena#isBlockPlaced` 改为 O(1) 查表，`BlastProtectionUtil` 的射线检测去重并提前结束，保护判定结果与原版完全一致，查询速度约快 485 倍，爆炸时 MSPT 明显下降。
- **HealPool 报错**：改用 `CopyOnWriteArrayList` 与安全的任务取消，修复并发修改异常（ConcurrentModificationException）和“取消尚未调度的任务”异常。
- **掉线击杀事件顺序**：把 `PlayerKillEvent` 提前到玩家被移出队伍之前触发，其他插件仍能通过 `IArena#getTeam` 拿到阵亡者的队伍。
- **构建修复**：`mvn clean package` 现在可以直接构建（补齐官方 25.2 用到的 sidebar 24.2、flow-nbt 2.0.0 等已下架依赖，做法见 [libs/README.md](libs/README.md)），并把产物名改成 `bedwars1058-plugin-<版本>-fix-<修复版本>`。

> 说明：计分板/床破坏显示的改动已按需求还原成原版逻辑，本分支不再包含该改动。

## 安装

1. 到 [Releases](https://github.com/cpy-cpyserver/BedWars1058_fix_pro/releases) 下载最新的 `bedwars1058-plugin-25.2-fix-1.7.jar`；
2. 放进服务器 `plugins/` 目录，并把旧的 `bedwars1058-plugin-25.2.jar` 删除或改名（**同一个插件不要同时放两个 jar**，否则会提示重复加载）；
3. 重启服务器即可。原来 `plugins/BedWars1058/` 里的配置、地图、语言文件都不用动。

## 自行构建

需要 **JDK 11 以上**（用 JDK 21 构建出来的产物与官方 25.2 最接近）和 Maven 3.6 以上：

```bash
mvn clean package -DskipTests
```

产物在 `bedwars-plugin/target/bedwars1058-plugin-25.2-fix-1.7.jar`，版本号由根目录 `pom.xml` 里的 `fix.version` 控制。

> 依赖说明：官方 25.2 使用的 `sidebar-base:24.2` 等库已经下架，本仓库把它们放在了 `libs/repo` 本地仓库里（生成方式见 [libs/README.md](libs/README.md)），所以离线也能正常构建。

## 系统要求

本插件运行在 [Spigot](https://www.spigotmc.org/) 和带 NMS 的服务端上，不编译 NMS 的 Spigot 分支不受支持。官方支持的服务端是 [Spigot](https://www.spigotmc.org/) 和 [Paper](https://papermc.io/)，需要 **Java 11 或更高**。

内置的地图还原系统基于压缩/解压地图，如果还在用机械硬盘或 CPU 较弱会比较吃力。想要更快、更轻的还原，推荐使用下面任意一种方案：
- [SlimeWorldManager](https://www.spigotmc.org/resources/slimeworldmanager.69974/) 插件（**仅支持 v2.2.1**）
- [AdvancedWorldManager](https://www.spigotmc.org/resources/advanced-slimeworldmanager.87209/) 插件（**仅支持 v2.8.0**）
- [AdvancedSlimePaper](https://github.com/InfernalSuite/AdvancedSlimePaper) 服务端（**1.20 及以上**）

BedWars1058 会自动挂钩使用，不需要额外配置。

## 游戏简介

BedWars（起床战争）是一款守护自己的床、破坏敌人床的小游戏：床被破坏后，死亡就无法复活。

## 现成配置与社区插件

现成的服务端整合包和大量社区插件可以在 [BedWars1058 Wiki](https://wiki.andrei1058.com/docs/BedWars1058/addons) 找到。

## 主要功能

> 以下是原版 25.2 的功能，本分支没有改动。

###### 运行模式 | 灵活
- **SHARED**：可以和其他小游戏跑在同一个 Spigot 实例上，只能通过命令进入游戏。
- **MULTIARENA**：独占一个服务端实例来承载小游戏，会保护大厅世界，可以通过命令、NPC、告示牌和 GUI 进入游戏。
- **BUNGEE-LEGACY**：经典的 Bungee 模式，一个游戏对应一个服务端实例，进入服务器即进入游戏，竞技场状态显示在 MOTD 上。
- **BUNGEE**：全新的可扩展 Bungee 模式，同一个服务端可以承载多个竞技场，需要时自动克隆并开启新场次，玩够一定局数后可以自动重启服务器。大厅服需要安装 [BedWarsProxy](https://www.spigotmc.org/resources/bedwarsproxy.66642/)，竞技场服数量不限。

###### 语言 | 每个玩家独立的语言系统
- 每位玩家都能用自己的语言接收消息、全息、GUI 等内容，命令为 `/bw lang`。
- 可以删除语言，也可以新增语言。
- 队名、分组名、商店内容等都可以在语言文件里翻译。
- 可以为[开局倒计时](https://gitlab.com/andrei1058/BedWars1058/-/wikis/language-configuration#custom-title-sub-title-for-arena-countdown)配置自定义标题和副标题。

###### 大厅移除 | 可选
游戏开始后，可以移除地图里的等待大厅。

###### 竞技场分组 | 可自定义
- 可以按类型给竞技场分组（4v4、50v50 等），组名随意。
- 每个分组可以有独立的计分板布局、队伍升级、开局物品和刷怪设置。
- 可以按分组加入地图：`/bw join Solo`、`/bw gui Solo`。

###### 商店 | 可自定义
- 可以配置快速购买的默认物品。
- 可以增删分类。
- 可以新增商店物品，或者在购买时执行命令。
- 永久物品会在玩家复活后发放。
- 永久物品可以设置为降级物品，每次死亡降低一级。
- 物品可以设置权重，避免买到比当前更弱的物品。
- 特殊物品：蠹虫（BedBug）、铁傀儡（Dream Defender）、搭桥蛋（Egg Bridge）、TNT 跳（TNT Jump）和直射火球（Straight Fireball）。
- 支持快速购买，并且在 Bungee 模式下可以跨节点同步。

###### 队伍升级 | 可自定义
- 不同的竞技场分组可以使用不同的队伍升级。
- 可以增删分类和内容。
- 升级元素可以做到：给物品附魔、给予药水效果（队友/基地/进入基地的敌人）、修改刷怪设置、调整突然死亡阶段的末影龙数量。
- 可以新增陷阱：解除附魔（剑、护甲、弓）、给予药水效果（队友/基地/进入基地的敌人）、敌人进入己方岛屿范围时移除其药水效果、执行命令。

###### 加入游戏的方式
- 竞技场选择器（可配置）：`/bw gui` 显示所有分组，`/bw gui Solo` 显示 Solo 分组，`/bw gui Solo+4v4` 显示 Solo 和 4v4 两个分组。
- 安装 Citizens 后可以通过 NPC 加入游戏。
- 支持加入告示牌和状态方块。
- 也可以直接用命令：`/bw join random` 进入人最多的场次，`/bw join 地图名` 进入指定地图，`/bw join 分组名+分组名2` 从指定分组中进入地图。

###### 竞技场设置 | 可自定义
- 可以设置用于告示牌、GUI 等的显示名。
- 可以设置最少人数、最多人数和队伍人数。
- 可以开关：是否允许旁观、空队伍是否关闭刷怪、空队伍是否关闭 NPC、是否关闭内置掉落管理、是否使用床全息。
- 可以设置队伍出生点和队伍 NPC 的保护范围。
- 可以设置岛屿半径（用于陷阱触发和地图边界）。
- 可以按 Y 坐标实现虚空秒杀。
- 队伍数量不限。
- 可以允许像空岛战争那样破坏地图。
- 可以开关刷怪拆分。
- 可以为每张地图设置独立的游戏规则。
- 每个队伍可以有无限量的铁/金/绿宝石刷怪点（绿宝石需要通过升级解锁）。

###### VIP 踢人 | 特权
拥有 `bw.vip` 权限的玩家可以在倒计时阶段进入已满的房间，并踢出一名没有该权限的玩家。

###### 玩家数据
- 本插件不提供排行榜全息，可以用 ajLeaderboards 或 LeaderHeads 配合我们提供的占位符实现。
- 玩家可以通过 `/bw stats` 查看内置的统计 GUI，界面可自定义。

###### 队伍系统
- 内置简单可用的组队系统，方便和朋友同队、同场游戏。
- 同时支持 AlessioDP 的 Parties 和 Simonsator 的 Party and Friends，大型网络用它们会更合适。

###### 挂机检测
超过 45 秒没有操作的玩家，无法拾取刷怪点生成的物品。

###### 自定义加入物品
- 可以增删进服时获得的物品（仅 MULTIARENA 模式），以及进入等待/开局阶段、以旁观身份加入时获得的物品。
- 加入物品可以执行命令。

###### 地图还原系统
- 默认的还原方式：卸载地图 → 解压备份 → 重新加载，硬件较差的服务器会比较吃力，建议使用游戏级 CPU 和 SSD。
- 为了提高性能，我们支持 SlimeWorldManager，用 slime 格式加载地图更快、对性能影响更小，强烈建议安装，无需手动转换，BedWars1058 会自动处理。
- 也可以通过 API 实现自己的地图适配器。
- 看起来比其他插件重，是因为我们不做简单的方块记录：服务器允许玩家像空岛战争一样破坏地图，所以必须整图还原。刷怪点、NPC、队伍出生点等区域会受保护。

###### 重新加入 | 功能
如果掉线，或者主动退出（可配置），可以通过命令或重新进入服务器回到原对局。Bungee 可扩展模式同样支持。

###### TNT 跳 | 功能
- 玩家可以做 TNT 跳，数值可配置。
- 背包里有 TNT 的玩家头顶会显示红色粒子（可配置）。

###### 节日活动
万圣节特殊玩法，会根据机器时区自动开启并提供特效。

## 参与贡献

欢迎任何形式的帮助，动手之前先看一眼 [CONTRIBUTING.md](https://github.com/andrei1058/BedWars1058/blob/master/CONTRIBUTING.md)。

如果你不是开发者，也可以在 [Issues](https://github.com/andrei1058/BedWars1058/issues) 里帮忙回答别人的问题，或者在 [Crowdin 上帮忙翻译插件](https://crowdin.com/project/bedwars1058)。

### 翻译进度
[Translation Chart](https://badges.awesome-crowdin.com/translation-12780139-594479.png)

## 第三方库
- [bStats](https://bstats.org/getting-started/include-metrics)
- [SidebarLib](https://github.com/andrei1058/SiderbarLib)
- [Commons IO](https://mvnrepository.com/artifact/commons-io/commons-io)
- [HikariCP](https://mvnrepository.com/artifact/com.zaxxer/HikariCP)
- [SLF4J](http://www.slf4j.org/)

## 协议与来源

本项目遵循 **GNU GPL 3.0** 协议，详见 [LICENSE](./LICENSE)。原作者是 Andrei Dascălu（andrei1058），本仓库仅在其基础上修复 bug，版权与开源协议保持不变。

## 联系方式

[![Discord Server](https://discordapp.com/api/guilds/201345265821679617/widget.png?style=banner3)](https://discord.gg/XdJfN2X)
