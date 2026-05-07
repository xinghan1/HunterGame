# 猎人游戏

## 指令与权限

默认支持 `/huntergame` 和 `/hg` 两种写法。

| 指令 | 权限 | 默认 | 说明 |
| --- | --- | --- | --- |
| `/hg` | `huntergame.help` | `true` | 查看帮助 |
| `/hg help` | `huntergame.help` | `true` | 查看帮助 |
| `/hg setlobby` | `huntergame.setlobby` | `op` | 设置游戏大厅 |
| `/hg startgame` | `huntergame.startgame` | `op` | 强制开始游戏 |
| `/hg reload` | `huntergame.reload` | `op` | 重载配置 |
| `/hg newseason <赛季ID>` | `huntergame.newseason` | `op` | 开启新的赛季 |
| `/hg refreshtier` | `huntergame.refreshtier` | `op` | 立即刷新全服排名 |
| `/hg savejob <职业ID> <显示名>` | `huntergame.saveprofession` | `op` | 保存终章职业物品到配置文件 |
| `/hg editjob <职业ID>` | `huntergame.editprofession` | `op` | 编辑终章职业物品 |

## PAPI 变量

- `%huntergame_role%`：当前角色
- `%huntergame_mode%`：当前模式
- `%huntergame_hunter_count%`：当前猎人数量
- `%huntergame_escaper_count%`：当前逃生者数量
- `%huntergame_kills%`：当前击杀数
- `%huntergame_kills_put%`：总击杀数
- `%huntergame_deaths%`：死亡次数
- `%huntergame_games_played%`：游玩次数
- `%huntergame_hunter_wins%`：猎人胜场
- `%huntergame_escape_wins%`：逃生者胜场
- `%huntergame_total_wins%`：总胜场
- `%huntergame_gametime%`：游戏时间
- `%huntergame_proficiency%`：熟练度
- `%huntergame_rank%`：段位名称
- `%huntergame_fortress%`：下界要塞坐标
- `%huntergame_bastion%`：猪灵堡垒坐标
- `%huntergame_portal%`：末地传送门坐标
- `%huntergame_season%`：当前赛季 ID
- `%huntergame_tier%`：全服熟练度排名

## Config.yml 配置

```yaml
# 配置大厅世界和坐标
lobby:
  world: normal
  x: 0
  y: 100.0
  z: 0

# 数据库信息
database:
  type: "file" # 可选值：mysql（数据库）、file（本地文件）
  host: "localhost"       # 数据库地址
  port: 3306              # 端口
  name: "root"       # 数据库名
  username: ""        # 数据库用户名
  password: ""            # 数据库密码
  timezone: "Asia/Shanghai" # 时区
  use_ssl: false          # 是否启用 SSL
  table_prefix: "hg_"        # 数据库表前缀

game:
  minPlayers: 3        # 最小玩家人数
  countdown: 60        # 倒计时总时间（秒）
  end_delay: 15        # 游戏结束后多少秒开始在线重置
  online_reset:
    player_kick_delay_ticks: 100  # 发送跨服传送后，等待多少tick再踢出仍留在本服的玩家
    post_kick_prepare_delay_ticks: 20 # 踢出玩家后等待多少tick再开始清理游戏数据和卸载世界
    world_step_delay_ticks: 60    # 每个世界重置完成后，等待多少tick再处理下一个世界
    kick_message: "&c服务器正在重置地图，请稍后再加入"
    worlds:
      - name: world
        environment: NORMAL
      - name: world_nether
        environment: NETHER
      - name: world_the_end
        environment: THE_END
  countdownKeyTimes:   # 自定义关键倒计时时间点
    - 60
    - 50
    - 40
    - 30
    - 20
    - 10
    - 5
    - 4
    - 3
    - 2
    - 1
  escapers_quit_countdown: 120  # 所有逃生者已退出，开始结束倒计时
  kickTime: 10 # 无操作踢出时间（分钟）
  compass:  # 是否启用指南针gui
    enable: true
    hunter_tp_compass_cooldown: 300  # 猎人指南针传送 打开冷却（300秒）
    deduct_health: 18  # 猎人传送扣除血量
    detection_distance: 50  # 猎人传送时 检测附近50格内是否有逃生者，如果有不进行传送
  game_time_limit_minutes: 30  # 游戏超过30分钟无法中途加入
  escaper_ratio_threshold: 3.0  # 猎人:逃生者比例
  hunter_shared_backpack: 90  # 猎人共享背包冷却（秒）
  escaper_shared_backpack: 90  # 逃生者共享背包冷却（秒）
  escaper_tracking_enable: true  # 是否启用逃生者队伍跟踪
  hunter_respawn_radius: 200  # 猎人复活配置,复活到逃生者附近的随机半径（格）
  spectator_max_distance: 100 # 旁观者距离观看目标的最大距离（格），超出自动传送回去
  respawn:
    baseRespawnTime: 5  # 最小复活时间(秒)
    maxRespawnTime: 180 # 最大复活时间(秒)
    maxGameTime: 3000 # 最大推移时间(秒)  随着游戏时间增加，复活时间而延长
  persistence_modes:   # 生存战各模式获胜所需存活时间（分钟）
    final_battle_minutes: 10
    vanilla_hunter_minutes: 25

ore_multiplier:
  player_radius_chunks: 1      # 玩家跨区块时，处理周围多少圈已加载区块
  max_chunks_per_tick: 1       # 每 tick 最多处理几个区块，调高会更快但更吃性能
  veins_per_chunk: 18          # 每个区块额外尝试生成的矿脉数，想更夸张可调到 24
  visible_vein_chance: 0.35    # 有多少矿脉优先贴近洞穴表面，方便玩家直接看见

mode-selection:
  toggle:
    fixed-mode-enabled: true  # 开启固定模式,不开启为投票选择
    fixed-mode-id: 2          # 固定模式ID（2=终章，3=原版）

BungeeCord:
  enable: false # 是否启用跨服传送功能
  server_lobby: lobby_1  # 大厅服务器名称
  server_selector: # 跨服传送物品
    material: "ENDER_EYE"
    slot: 8
    display_name: "§c§l离开游戏"
    lore:
      - "§7右键传送到大厅"

# 按总人数配置阵营 (原版猎人使用)
player_counts:
  scaling:
    thresholds:
      15: 5  # 15人以上 -> 5 逃生者
      10: 4  # 10-15人 -> 4 逃生者
      8: 3  # 8-12人 -> 3 逃生者
      5: 2   # 5-8人 -> 2 逃生者
    default: 1 # 5人以下 -> 1 逃生者
  final_battle:
    scaling:
      thresholds:
        12: 3  # 12人及以上 -> 3 逃生者
        7: 2   # 7-11人 -> 2 逃生者
      default: 1 # 7人以下

# 猎人重生补给配置（支持多时间段，游戏时间越长装备越好）
hunter_resupply:
  enable: true
  time_stages:
    # 第一阶段：游戏开始 10-30 分钟
    - name: "初期装备"
      min_minutes: 10
      items:
        - material: LEATHER_HELMET
          amount: 1
          slot: helmet
        - material: LEATHER_CHESTPLATE
          amount: 1
          slot: chestplate
        - material: LEATHER_LEGGINGS
          amount: 1
          slot: leggings
        - material: LEATHER_BOOTS
          amount: 1
          slot: boots
        - material: STONE_SWORD
          amount: 1
          slot: 0
        - material: STONE_AXE
          amount: 1
          slot: 1
        - material: STONE_PICKAXE
          amount: 1
          slot: 2
    # 第二阶段：游戏 30-60 分钟
    - name: "中期装备"
      min_minutes: 30
      items:
        - material: IRON_HELMET
          amount: 1
          slot: helmet
        - material: IRON_CHESTPLATE
          amount: 1
          slot: chestplate
        - material: IRON_LEGGINGS
          amount: 1
          slot: leggings
        - material: IRON_BOOTS
          amount: 1
          slot: boots
        - material: IRON_SWORD
          amount: 1
          slot: 0
        - material: IRON_AXE
          amount: 1
          slot: 1
        - material: IRON_PICKAXE
          amount: 1
          slot: 2
    # 第三阶段：游戏 60 分钟以上
    - name: "后期装备"
      min_minutes: 60
      items:
        - material: DIAMOND_HELMET
          amount: 1
          slot: helmet
        - material: DIAMOND_CHESTPLATE
          amount: 1
          slot: chestplate
        - material: DIAMOND_LEGGINGS
          amount: 1
          slot: leggings
        - material: DIAMOND_BOOTS
          amount: 1
          slot: boots
        - material: DIAMOND_SWORD
          amount: 1
          slot: 0
        - material: DIAMOND_AXE
          amount: 1
          slot: 1
        - material: DIAMOND_PICKAXE
          amount: 1
          slot: 2
        - material: BOW
          amount: 1
          slot: 3
        - material: ARROW
          amount: 10
          slot: 9

# 聊天格式
formats:
  enable: true # 是否开启聊天格式
  admin:
    permission: chatformat.admin
    format: "&7[%huntergame_proficiency%★]%huntergame_role% &c&l%player% -> &c%message%"
  mvp:
    permission: chatformat.mvp
    format: "&7[%huntergame_proficiency%★]%huntergame_role% &6&l%player% -> &6%message%"
  vip:
    permission: chatformat.vip
    format: "&7[%huntergame_proficiency%★]%huntergame_role% &b&l%player% -> &b%message%"
  default:
    permission: null
    format: "&7[%huntergame_proficiency%★]%huntergame_role% &f&l%player% -> &f%message%"


# 定时广播配置
broadcast:
  enable: true                # 是否启用广播
  interval: 240               # 消息间隔（秒）
  messages:
    - "§a小贴士: §c如果你不小心掉线了，可在3分钟内重新连接，回到原来的状态！"
    - "§a小贴士: §c猎人游戏有两大模式: 经典猎人，终章之战。"
    - "§a小贴士: §c原版猎人限时3小时，终章之战限时25分钟。"
    - "§a小贴士: §c本猎人游戏有多个末地传送门哦！末影珍珠只会跟随最近的末地传送门！"
    - "§a小贴士: §c请认真对待每一局游戏，中途退出你将不会受到任何的奖励！但中途无法复活可以提前结算获得奖励哦~"

# 游戏结束结算奖励（中途退出玩家不会获得）
# 公式节点：基础值 = 击杀*kill + 伤害*damage + 死亡*death + 游玩分钟*playtime_minute + 胜利/失败
# 消息/指令可用占位符：%player% 或 {player}，%money% 或 {money}，%exp% 或 {exp}，%proficiency% 或 {proficiency}
# 也支持 kills、damage、deaths、minutes、role、result、hunter_money、hunter_exp、escaper_money、escaper_exp、rewards
settlement_rewards:
  enabled: true
  message:
    enabled: true
    text:
      - "&c&lHUNTERGAME 猎人游戏 &8--- &7游戏结算 &8---"
      - "&7"
      - "&a结果: {result} &8&l| &e时长: {minutes}分钟 &8&l| &c击杀: {kills} &8&l| &7死亡: {deaths} &8&l| &4伤害: {damage}"
      - "&f结算奖励: &c%money%游戏币 &7/ &e%exp%大厅经验 / &f%proficiency%熟练度"
      - "&7"
  formulas:
    escaper:
      money:
        kill: 10.0
        damage: 0.06
        death: -5.0
        win: 80.0
        fail: 0.0
        playtime_minute: 2.0
      exp:
        kill: 20.0
        damage: 0.10
        death: -5.0
        win: 150.0
        fail: 0.0
        playtime_minute: 4.0
    hunter:
      money:
        kill: 25.0
        damage: 0.10
        death: -5.0
        win: 10.0
        fail: 0.0
        playtime_minute: 2.0
      exp:
        kill: 40.0
        damage: 0.12
        death: -5.0
        win: 20.0
        fail: 0.0
        playtime_minute: 4.0
  commands:
    hunter_win:
      - "money give %player% 游戏币 %hunter_money%"
      - "ql addexp %hunter_exp% %player%"
    hunter_fail:
      - "money give %player% 游戏币 %hunter_money%"
      - "ql addexp %hunter_exp% %player%"
    escaper_win:
      - "money give %player% 游戏币 %escaper_money%"
      - "ql addexp %escaper_exp% %player%"
    escaper_fail:
      - "money give %player% 游戏币 %escaper_money%"
      - "ql addexp %escaper_exp% %player%"

scoreboard:
  enabled: true # 是否启用计分板
  waiting: # 等待中的计分板
    title: "&6猎人游戏"
    lines:
      - "&1"
      - '&f赛季: %huntergame_season%'
      - '&f模式: %huntergame_mode%'
      - "&2"
      - "&f总杀敌: %huntergame_kills_put%"
      - "&f游戏次数: %huntergame_games_played%"
      - "&f胜利次数: %huntergame_total_wins%"
      - "&f熟练度: %huntergame_proficiency%"
      - "&3"
      - "&b状态: &a等待中"
      - "&f玩家数量: &a%players%/&c32"
      - "&4"
      - "&bplay.tiancraft.cn"
  game: # 原版猎人的计分板
    title: "&6猎人游戏"
    lines:
      - "&1"
      - "&f角色: %huntergame_role%"
      - "&f模式: %huntergame_mode%"
      - '&f游戏时间: &a%huntergame_gametime%'
      - "&2"
      - "&f下界堡垒: %huntergame_fortress%"
      - "&f猪灵堡垒: %huntergame_bastion%"
      - "&f末地要塞: %huntergame_portal%"
      - "&3"
      - "&f猎人|逃生者: &c%huntergame_hunter_count%/&b%huntergame_escaper_count%"
      - "&f击杀: %huntergame_kills%"
      - "&4"
      - "&bplay.tiancraft.cn"
  final_battle:  # 终章之战 显示的计分板
    title: "&6猎人游戏"
    lines:
      - "&1"
      - "&f角色: %huntergame_role%"
      - "&f模式: %huntergame_mode%"
      - '&f游戏时间: &a%huntergame_gametime%'
      - "&3"
      - "&f猎人|逃生者: &c%huntergame_hunter_count%/&b%huntergame_escaper_count%"
      - "&f击杀: %huntergame_kills%"
      - "&4"
      - "&bplay.tiancraft.cn"


# 终章之战模式配置
final_battle:
  profession_gui:
    title: "&5选择职业"
    rows: 3
  skill_lock_seconds: 40       # 开局多少秒内禁止释放职业技能（0=不限制）
  hunter_max_respawns: 1        # 猎人最大复活次数（0=不复活，1=最多复活1次，以此类推）
  hunter_respawn_seconds: 30    # 猎人复活等待时间（秒）



```
