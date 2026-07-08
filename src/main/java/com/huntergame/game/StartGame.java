package com.huntergame.game;

import com.huntergame.HunterGame;
import com.huntergame.role.RoleSelectionHandler;
import com.huntergame.util.SafeLocationFinder;
import com.huntergame.vote.VoteSystem;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;


import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

// 用于启动游戏，分配角色，以及游戏倒计时
public class StartGame implements Listener {
    private final HunterGame plugin;
    private boolean countdownInProgress = false; // 标志当前是否正在倒计时
    private int countdownTaskId = -1; // 保存当前倒计时任务的 ID
    private final Map<UUID, Integer> respawnTimers = new HashMap<>(); // 存储玩家 UUID 和剩余复活时间
    private final Map<UUID, BukkitRunnable> respawnTasks = new HashMap<>(); // 存储玩家 UUID 和复活任务
    private final Map<UUID, Integer> finalBattleHunterRespawns = new HashMap<>();
    // 记录玩家最后一次打开背包的时间
    private final Map<UUID, Long> hunterBackpackCooldown = new HashMap<>();
    private final Map<UUID, Long> escaperBackpackCooldown = new HashMap<>();
    private long HUNTER_SHARED_BACKPACK_COOLDOWN; // 90秒
    private long ESCAPER_SHARED_BACKPACK_COOLDOWN; // 90秒
    private Map<UUID, Integer> playerVotes = new HashMap<>(); // 玩家投票记录
    private static final int VOTE_ESCAPER = 1; // 投票逃生者
    private static final int VOTE_HUNTER = 2; // 投票猎人
    private final VoteSystem voteSystem; // 引入投票系统实例
    private final SafeLocationFinder locationFinder;

    public StartGame(HunterGame plugin) {
        this.plugin = plugin;
        this.voteSystem = new VoteSystem(plugin);
        this.locationFinder = new SafeLocationFinder(plugin);

        FileConfiguration config = plugin.getConfig();
        HUNTER_SHARED_BACKPACK_COOLDOWN = config.getInt("game.hunter_shared_backpack", config.getInt("hunter_shared_backpack", 90));
        ESCAPER_SHARED_BACKPACK_COOLDOWN = config.getInt("game.escaper_shared_backpack", config.getInt("escaper_shared_backpack", 90));
    }


    /**
     * 设置所有玩家为生存模式
     */
    private void setAllPlayersToSurvival() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    /**
     * 玩家加入时检查并启动倒计时
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (!plugin.isGameRunning()) {
            checkAndStartGame();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                voteSystem.giveVoteItemToPlayer(player); // 发放投票物品
            }, 5L);
        }

        // 当玩家重新加入时，检查他们是否在复活中
        if (respawnTimers.containsKey(playerId)) {
            int remainingTime = respawnTimers.get(playerId);
            player.sendMessage(plugin.getMessage("resurrection_countdown", "&e你还有 %remainingTime% 秒复活！")
                    .replace("%remainingTime%", String.valueOf(remainingTime)));
            player.setGameMode(GameMode.SPECTATOR); // 确保玩家仍然是观察者模式

            // 重新启动复活倒计时任务
            startRespawnCountdown(player, remainingTime);
        }
    }

    /**
     * 玩家退出时检查并取消倒计时
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        checkAndCancelCountdownIfNeeded();
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 当玩家退出时，取消他们的复活任务
        if (respawnTasks.containsKey(playerId)) {
            respawnTasks.get(playerId).cancel();
            respawnTasks.remove(playerId);
        }
    }


    /**
     * 检查在线人数并启动游戏
     */
    public void checkAndStartGame() {
        if (countdownInProgress) return; // 如果倒计时已经在进行，则直接返回

        int minPlayers = plugin.getConfig().getInt("game.minPlayers", 2);
        int countdownTime = plugin.getConfig().getInt("game.countdown", 60); // 默认 60 秒

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.size() < minPlayers) {
            Bukkit.broadcastMessage(plugin.getMessage("not_enough_players_start", "&c当前游戏最少需要 %min_players% 人才能开始！")
                    .replace("%min_players%", String.valueOf(minPlayers)));
            return;
        }
        startCountdown(countdownTime);
    }

    /**
     * 启动倒计时逻辑
     */
    private void startCountdown(int seconds) {
        countdownInProgress = true;

        List<Integer> keyTimes = plugin.getConfig().getIntegerList("game.countdownKeyTimes");
        if (keyTimes.isEmpty()) {
            keyTimes = Arrays.asList(60, 30, 10, 5, 4, 3, 2, 1);
        }

        List<Integer> finalKeyTimes = keyTimes;
        countdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            private int timeLeft = seconds;

            @Override
            public void run() {
                if (plugin.isGameRunning()) {
                    return;
                }
                // 动态检查玩家数量
                int minPlayers = plugin.getConfig().getInt("game.minPlayers", 2);

                if (Bukkit.getOnlinePlayers().size() < minPlayers) {
                    Bukkit.broadcastMessage(plugin.getMessage("cancel_game_countdown", "&c由于玩家人数不足，无法启用游戏倒计时！"));
                    cancelCountdown();
                    return;
                }

                if (finalKeyTimes.contains(timeLeft)) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendTitle(
                                plugin.getMessage("countdown_title", "&e游戏即将开始"), // 从语言配置文件加载主标题
                                plugin.getMessage("countdown_subtitle", "&c%time% 秒！")
                                        .replace("%time%", String.valueOf(timeLeft)),
                                10, // 淡入时间（ticks）
                                20, // 显示时间（ticks）
                                10  // 淡出时间（ticks）
                        );


                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                    }
                    Bukkit.broadcastMessage(
                            plugin.getMessage("game_starting_soon", "&6游戏将在 %time% 秒后开始！")
                                    .replace("%time%", String.valueOf(timeLeft))
                    );

                }

                if (timeLeft <= 0) {
                    Bukkit.getScheduler().cancelTask(countdownTaskId);
                    startGame();
                }
                timeLeft--;
            }
        }, 0L, 20L); // 每秒运行一次
    }

    /**
     * 取消当前倒计时
     */
    public void cancelCountdown() {
        if (countdownInProgress) {
            Bukkit.getScheduler().cancelTask(countdownTaskId);
            countdownInProgress = false;
            countdownTaskId = -1;
        }
    }

    /**
     * 清空终章模式的复活次数记录
     */
    public void clearFinalBattleRespawnData() {
        finalBattleHunterRespawns.clear();
    }

    public void resetRuntimeData() {
        cancelCountdown();
        for (BukkitRunnable task : respawnTasks.values()) {
            task.cancel();
        }
        respawnTasks.clear();
        respawnTimers.clear();
        finalBattleHunterRespawns.clear();
        hunterBackpackCooldown.clear();
        escaperBackpackCooldown.clear();
        playerVotes.clear();
        assignedEscapers.clear();
        assignedHunters.clear();
        voteSystem.resetVoteData();
    }

    /**
     * 玩家退出时检查并取消倒计时
     */
    private void checkAndCancelCountdownIfNeeded() {
        if (plugin.isGameRunning()) return;
        if (plugin.isResetting()) return;
        if (plugin.isGameEnded()) return;

        int minPlayers = plugin.getConfig().getInt("game.minPlayers", 2);
        if (Bukkit.getOnlinePlayers().size() < minPlayers) {
            cancelCountdown();
            Bukkit.broadcastMessage(plugin.getMessage("cancel_game_countdown", "&c由于玩家人数不足，无法启用游戏倒计时！"));
        }
    }


    public void giveHunterMark(Player targetPlayer) {
        if (targetPlayer != null) {
            PersistentDataContainer container = targetPlayer.getPersistentDataContainer();
            container.set(RoleSelectionHandler.IS_HUNTER, PersistentDataType.BOOLEAN, true);
        }
    }

    public void giveEscaperMark(Player targetPlayer) {
        if (targetPlayer != null) {
            PersistentDataContainer container = targetPlayer.getPersistentDataContainer();
            container.set(RoleSelectionHandler.IS_ESCAPER, PersistentDataType.BOOLEAN, true);
        }
    }



    private List<Player> assignedEscapers = new ArrayList<>();
    private List<Player> assignedHunters = new ArrayList<>();

    // 改进的角色分配方法
    private void assignRoles(List<Player> allPlayers) {
        // 1. 获取投票数据
        Map<UUID, Integer> playerVotes = voteSystem.getPlayerRoleVotes();

        // 2. 分离已投票和未投票玩家
        List<Player> votedEscapers = new ArrayList<>();
        List<Player> votedHunters = new ArrayList<>();
        List<Player> unvotedPlayers = new ArrayList<>();

        for (Player player : allPlayers) {
            UUID uuid = player.getUniqueId();
            if (playerVotes.containsKey(uuid)) {
                if (playerVotes.get(uuid) == VoteSystem.VOTE_ESCAPER) {
                    votedEscapers.add(player);
                } else if (playerVotes.get(uuid) == VoteSystem.VOTE_HUNTER) {
                    votedHunters.add(player);
                }
            } else {
                unvotedPlayers.add(player);
            }
        }

        // 3. 从配置读取对应玩家数的阵营分配 (动态计算)
        int totalPlayers = allPlayers.size();
        int targetEscapers = getTargetEscaperCount(totalPlayers);
        int targetHunters = totalPlayers - targetEscapers;

        // 4. 先分配已投票玩家
        assignedHunters.addAll(votedHunters);
        assignedEscapers.addAll(votedEscapers);

        // 随机打乱，保证调整公平
        Collections.shuffle(assignedHunters);
        Collections.shuffle(assignedEscapers);

        // 5. 如果猎人过多 → 随机转为逃生者
        while (assignedHunters.size() > targetHunters) {
            int randomIndex = ThreadLocalRandom.current().nextInt(assignedHunters.size());
            Player excessHunter = assignedHunters.remove(randomIndex);
            assignedEscapers.add(excessHunter);
        }

        // 6. 如果猎人过少 → 从未投票或逃生者中随机补充
        int needHunters = targetHunters - assignedHunters.size();
        if (needHunters > 0) {
            // 先从未投票玩家中随机选
            Collections.shuffle(unvotedPlayers);
            int takeFromUnvoted = Math.min(needHunters, unvotedPlayers.size());
            for (int i = 0; i < takeFromUnvoted; i++) {
                Player newHunter = unvotedPlayers.remove(0);
                assignedHunters.add(newHunter);
            }

            // 还不够则从逃生者中随机选
            needHunters -= takeFromUnvoted;
            if (needHunters > 0) {
                Collections.shuffle(assignedEscapers);
                for (int i = 0; i < needHunters; i++) {
                    Player adjustedHunter = assignedEscapers.remove(0);
                    assignedHunters.add(adjustedHunter);
                }
            }
        }

        // 7. 剩余未投票玩家全部分配为逃生者
        assignedEscapers.addAll(unvotedPlayers);

        // 8. 最终校验：至少 1 猎人 + 1 逃生者
        if (assignedHunters.isEmpty()) {
            Player randomHunter = assignedEscapers.remove(ThreadLocalRandom.current().nextInt(assignedEscapers.size()));
            assignedHunters.add(randomHunter);
        }
        if (assignedEscapers.isEmpty()) {
            Player randomEscaper = assignedHunters.remove(ThreadLocalRandom.current().nextInt(assignedHunters.size()));
            assignedEscapers.add(randomEscaper);
        }

        // 9. 最后再打乱一次，避免阵营顺序可预测
        Collections.shuffle(assignedHunters);
        Collections.shuffle(assignedEscapers);
    }

    // 动态获取逃生者数量
    private int getTargetEscaperCount(int totalPlayers) {
        int count = plugin.getConfig().getInt("player_counts.scaling.default", 1);

        ConfigurationSection thresholds = plugin.getConfig().getConfigurationSection("player_counts.scaling.thresholds");

        if (thresholds != null) {
            // 获取所有配置的键（例如 "12", "7"），解析为整数并降序排列
            List<Integer> sortedThresholds = thresholds.getKeys(false).stream()
                    .map(key -> {
                        try {
                            return Integer.parseInt(key);
                        } catch (NumberFormatException e) {
                            return -1; // 忽略非数字键
                        }
                    })
                    .filter(key -> key > 0)
                    .sorted(Collections.reverseOrder()) // 降序排列 (例如: 12, 7)
                    .collect(Collectors.toList());

            // 遍历排序后的阈值
            for (int threshold : sortedThresholds) {
                // 如果当前玩家数 >= 阈值，就使用该阈值对应的逃生者数量
                if (totalPlayers >= threshold) {
                    return thresholds.getInt(String.valueOf(threshold), 1);
                }
            }
        }
        return count;
    }


    /**
     * 开始游戏逻辑
     */
    public void startGame() {
        if (plugin.isGameRunning()) {
            Bukkit.broadcastMessage(plugin.getMessage("game_progress", "&c游戏已经在进行中，不能重复启动！"));
            return;
        }

        plugin.getGameSettlement().resetStats();

        // 获取投票结果并确定模式
        int selectedMode = voteSystem.determineFinalGameMode();
        int selectedType = voteSystem.determineFinalBattleType();
        plugin.setBattleType(selectedType);

        // 启动对应模式
        if (selectedMode == VoteSystem.MODE_VANILLA_HUNTER) {
            plugin.setGameMode(HunterGame.MODE_VANILLA_HUNTER);
            startVanillaHunterMode();
        } else {
            plugin.setGameMode(HunterGame.MODE_FINAL_BATTLE);
            new FinalBattleManager(plugin, voteSystem.getPlayerRoleVotes()).startFinalBattle();
        }

    }

    /**
     * 统一处理已分配角色的初始化
     */
    public void startAssigned() {
        // 1. 获取配置数据
        double hunterHealth = 20.0;
        double escaperHealth = 20.0;

        // 预加载消息，避免循环内重复获取
        String hunterTitle = plugin.getMessage("hunter_title", "&a你是 &c猎人！");
        String hunterMsg = plugin.getMessage("hunter_identity", "&a你是 &c猎人！ ");

        String escaperTitle = plugin.getMessage("escaper_title", "&a你是 &b逃生者！");
        String escaperMsg = plugin.getMessage("escaper_identity", "&a你是 &b逃生者！");

        // 初始化猎人
        for (Player hunter : assignedHunters) {
            plugin.addHunter(hunter.getUniqueId());
            giveHunterMark(hunter);
            hunter.getInventory().clear();

            // 属性设置
            hunter.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(hunterHealth);
            hunter.setHealth(hunterHealth);
            hunter.setSaturation(20.0F);

            // 功能性物品
            plugin.getHunterTracker().assignCompassAndTracking(hunter, false);
            plugin.giveSharedBackpack(hunter, true);
            plugin.getPermissionRecipeManager().giveUnlockedRecipeBook(hunter);

            // 消息与奖励
            plugin.getDataStorageManager().addProficiency(hunter, plugin.getRankManager().getGameStartReward());
            hunter.sendTitle(hunterTitle, "", 10, 100, 10);
            hunter.sendMessage(hunterMsg);
        }

        // 3. 初始化逃生者
        for (Player escaper : assignedEscapers) {
            plugin.addEscaper(escaper.getUniqueId());
            giveEscaperMark(escaper);
            escaper.getInventory().clear();

            // 属性设置
            escaper.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(escaperHealth);
            escaper.setHealth(escaperHealth);
            escaper.setSaturation(20.0F);

            // 功能性物品
            plugin.getHunterTracker().assignCompassAndTracking(escaper, true); // 逃生者不需要指南针，但可能需要注册被追踪状态
            plugin.giveSharedBackpack(escaper, false);
            plugin.getPermissionRecipeManager().giveUnlockedRecipeBook(escaper);
            escaper.getInventory().addItem(new ItemStack(Material.BREAD, 3));

            // 消息与奖励
            plugin.getDataStorageManager().addProficiency(escaper, plugin.getRankManager().getGameStartReward());
            escaper.sendTitle(escaperTitle, "", 10, 100, 10);
            escaper.sendMessage(escaperMsg);
        }
    }

    public void startVanillaHunterMode() {
        if (!prepareCommonGameLogic()) return;
        startAssigned();

        World world = Bukkit.getWorld("world");
        Location center = world.getSpawnLocation();

        // 原版猎人：所有玩家传送到同一地点，关在同一屏障笼子里
        plugin.setPvpLocked(true);
        locationFinder.findLocation(world, center, 100, 1000, (spawnLocation) -> {
            // 将原版猎人开局屏障整体上抬 2 格，避免笼子与地形重叠
            Location cageCenter = spawnLocation.clone().add(0, 2, 0);
            List<Player> allPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
            for (Player player : allPlayers) {
                player.teleport(cageCenter);
            }
            world.setSpawnLocation(spawnLocation);
            broadcastPlayerCounts();
            plugin.setGameInProgress(true);
            plugin.glassCageManager.createGroupCage(allPlayers, cageCenter);
            plugin.setPvpLocked(false);
        });

        Bukkit.broadcastMessage(plugin.getMessage("vanilla_hunter_started", "&7===== 经典猎人 已启动 ====="));

        // 根据战役类型广播不同的目标
        if (plugin.isPersistenceBattle()) {
            int minutes = plugin.getConfig().getInt("game.persistence_modes.vanilla_hunter_minutes", 30);
            Bukkit.broadcastMessage(plugin.getMessage("vanilla_persistence_mode_title", "&e【生存战模式】"));
            Bukkit.broadcastMessage(plugin.getMessage("vanilla_persistence_escaper_objective", "&7• 逃生者目标：存活 %minutes% 分钟 或 击杀末影龙 •")
                    .replace("%minutes%", String.valueOf(minutes)));
            Bukkit.broadcastMessage(plugin.getMessage("vanilla_persistence_hunter_objective", "&7• 猎人目标：击杀全部逃生者 •"));
        } else {
            Bukkit.broadcastMessage(plugin.getMessage("vanilla_clearance_mode_title", "&c【通关战模式】"));
            Bukkit.broadcastMessage(plugin.getMessage("vanilla_clearance_escaper_objective", "&7• 逃生者目标：击杀末影龙 •"));
            Bukkit.broadcastMessage(plugin.getMessage("vanilla_clearance_hunter_objective", "&7• 猎人目标：击杀全部逃生者 •"));
        }
        broadcastTeamRatio();
    }

    /**
     * 通用的游戏准备逻辑：检查世界、重置状态、分配角色
     * @return 如果准备成功返回 true，失败（如世界不存在）返回 false
     */
    private boolean prepareCommonGameLogic() {
        countdownInProgress = false; // 重置倒计时标志
        World world = Bukkit.getWorld("world");
        if (world == null) {
            Bukkit.broadcastMessage(plugin.getMessage("no_world", "&c游戏世界未找到，无法开始游戏！"));
            return false;
        }

        plugin.setGameInProgress(true); // 提前标记游戏开始
        plugin.startGame(); // 游戏计时
        world.setTime(100); // 设置时间为晴天上午

        // 打乱并分配角色
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        Collections.shuffle(players);
        assignRoles(players);

        plugin.clearGameData(); // 清空之前的游戏数据
        setAllPlayersToSurvival();

        return true;
    }

    private void broadcastPlayerCounts() {
        Bukkit.broadcastMessage(
                plugin.getMessage("players_count", "&c猎人数量: %hunters% &8&l| &b逃生者数量: %escapers%")
                        .replace("%hunters%", String.valueOf(assignedHunters.size()))
                        .replace("%escapers%", String.valueOf(assignedEscapers.size()))
        );
    }

    private void broadcastTeamRatio() {
        Bukkit.broadcastMessage(plugin.getMessage("team_ratio", "&7• 阵营：%escapers%名逃生者 vs %hunters%名猎人 •")
                .replace("%escapers%", String.valueOf(plugin.getEscapers().size()))
                .replace("%hunters%", String.valueOf(plugin.getHunters().size())));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        ItemStack item = event.getItem();

        if (item == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        // 处理猎人共享背包
        if (plugin.isHunterSharedBackpack(item) && plugin.isHunter(playerId)) {
            if (checkCooldown(playerId, hunterBackpackCooldown, HUNTER_SHARED_BACKPACK_COOLDOWN * 1000L)) {
                int remaining = getRemainingCooldown(playerId, hunterBackpackCooldown, HUNTER_SHARED_BACKPACK_COOLDOWN * 1000L);
                // 冷却中，提示玩家
                player.sendMessage(plugin.getMessage("shared_backpack_countdown", "&c背包仍在冷却中，剩余 %remaining% 秒！")
                        .replace("%remaining%", String.valueOf(remaining)));
                event.setCancelled(true);
                return;
            }

            // 打开背包并设置冷却时间
            player.openInventory(plugin.getHunterSharedInventory());
            hunterBackpackCooldown.put(playerId, currentTime);
            event.setCancelled(true);
            return;
        }

        // 处理逃生者共享背包
        if (plugin.isEscaperSharedBackpack(item) && plugin.isEscaper(playerId)) {
            if (checkCooldown(playerId, escaperBackpackCooldown, ESCAPER_SHARED_BACKPACK_COOLDOWN * 1000L)) {
                int remaining = getRemainingCooldown(playerId, escaperBackpackCooldown, ESCAPER_SHARED_BACKPACK_COOLDOWN * 1000L);
                player.sendMessage(plugin.getMessage("shared_backpack_countdown", "&c背包仍在冷却中，剩余 %remaining% 秒！")
                        .replace("%remaining%", String.valueOf(remaining)));
                event.setCancelled(true);
                return;
            }

            // 打开背包并设置冷却时间
            player.openInventory(plugin.getEscaperSharedInventory());
            escaperBackpackCooldown.put(playerId, currentTime);
            event.setCancelled(true);
            return;
        }

        // 非所属阵营的背包提示
        if (plugin.isHunterSharedBackpack(item)) {
            player.sendMessage(plugin.getMessage("unable_open_hunter_backpack","&c你无法打开猎人的共享背包！"));
        } else if (plugin.isEscaperSharedBackpack(item)) {
            player.sendMessage(plugin.getMessage("unable_open_escape_backpack","&c你无法打开逃生者的共享背包！"));
        }
        event.setCancelled(true);
    }

    // 检查玩家是否在冷却中
    private boolean checkCooldown(UUID playerId, Map<UUID, Long> cooldownMap, long cooldownTime) {
        Long lastUseTime = cooldownMap.get(playerId);
        if (lastUseTime == null) return false; // 首次使用，无冷却

        // 计算剩余冷却时间（毫秒）
        long remainingCooldown = lastUseTime + cooldownTime - System.currentTimeMillis();
        return remainingCooldown > 0; // 大于0表示仍在冷却中
    }

    // 获取剩余冷却时间（秒）
    private int getRemainingCooldown(UUID playerId, Map<UUID, Long> cooldownMap, long cooldownTime) {
        Long lastUseTime = cooldownMap.get(playerId);
        if (lastUseTime == null) return 0;

        long remainingMillis = lastUseTime + cooldownTime - System.currentTimeMillis();
        return (int) Math.max(0, remainingMillis / 1000); // 转换为秒
    }


    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();

        // 自动重生
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        }, 1L);

        if (plugin.isFinalBattleMode()) {
            if (plugin.isHunter(playerId)) {
                int maxRespawns = plugin.getConfig().getInt("final_battle.hunter_max_respawns", 0);
                int usedRespawns = finalBattleHunterRespawns.getOrDefault(playerId, 0);
                player.setGameMode(GameMode.SPECTATOR);
                plugin.addRealSpectator(playerId);

                if (usedRespawns < maxRespawns) {
                    int respawnTime = Math.max(0, plugin.getConfig().getInt("final_battle.hunter_respawn_seconds", 30));
                    finalBattleHunterRespawns.put(playerId, usedRespawns + 1);
                    respawnTimers.put(playerId, respawnTime);
                    startRespawnCountdown(player, respawnTime);
                    player.sendMessage(plugin.getMessage("resurrection_countdown", "&e你还有 %remainingTime% 秒复活！")
                            .replace("%remainingTime%", String.valueOf(respawnTime)));
                } else {
                    player.sendMessage(plugin.getMessage("final_battle_no_respawn", "&c终章模式猎人复活次数已用完！"));
                }

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.teleportSpectatorToRandomPlayer(player);
                }, 20L);
                return;
            } else {
                // 逃生者死亡
                player.setGameMode(GameMode.SPECTATOR);
                plugin.addRealSpectator(playerId); // 标记为真正的旁观者
                player.sendMessage(plugin.getMessage("game_death_escapers", "&7你已死亡，现在成为旁观者"));
                // 传送到随机玩家位置
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.teleportSpectatorToRandomPlayer(player);
                }, 20L);
            }
            return;
        }

        if (plugin.isHunter(playerId)) {
            long gameTime = plugin.getElapsedSeconds(); // 游戏时间（秒）
            int respawnTime = calculateRespawnTime(gameTime); // 计算复活时间

            player.setGameMode(GameMode.SPECTATOR);
            plugin.addRealSpectator(playerId); // 标记为真正的旁观者（复活中）
            player.sendMessage(plugin.getMessage("resurrection_countdown", "&e你还有 %remainingTime% 秒复活！")
                    .replace("%remainingTime%", String.valueOf(respawnTime)));
            // 存储剩余复活时间
            respawnTimers.put(playerId, respawnTime);

            // 启动复活倒计时任务R
            startRespawnCountdown(player, respawnTime);
            // 传送到随机玩家位置
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.teleportSpectatorToRandomPlayer(player);
            }, 20L);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!plugin.isGameRunning()) {
            return;
        }

        boolean shouldStaySpectator = respawnTimers.containsKey(playerId) || plugin.isRealSpectator(playerId);
        if (!shouldStaySpectator) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> forceSpectatorWhileWaiting(player), 1L);
    }

    private void forceSpectatorWhileWaiting(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        boolean shouldStaySpectator = respawnTimers.containsKey(playerId) || plugin.isRealSpectator(playerId);
        if (!shouldStaySpectator) {
            return;
        }

        player.setGameMode(GameMode.SPECTATOR);
        plugin.addRealSpectator(playerId);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.getGameMode() == GameMode.SPECTATOR && plugin.isRealSpectator(playerId)) {
                plugin.teleportSpectatorToRandomPlayer(player);
            }
        }, 2L);
    }



    int calculateRespawnTime(long gameTime) {
        int baseRespawnTime = Math.max(30, plugin.getConfig().getInt("game.respawn.baseRespawnTime", 30)); // 基础复活时间（秒）
        int maxRespawnTime = plugin.getConfig().getInt("game.respawn.maxRespawnTime", 180); // 最大复活时间（秒）
        long maxGameTime = plugin.getConfig().getInt("game.respawn.maxGameTime", 3000); // 最大推移时间（秒）

        if (gameTime >= maxGameTime) {
            return maxRespawnTime;
        } else {
            double progress = (double) gameTime / maxGameTime;
            return baseRespawnTime + (int) (progress * (maxRespawnTime - baseRespawnTime));
        }
    }

    void startRespawnCountdown(Player player, int respawnTime) {
        UUID playerId = player.getUniqueId();
        BukkitRunnable task = new BukkitRunnable() {
            int timeLeft = respawnTime;

            @Override
            public void run() {
                if (timeLeft <= 0) {
                    // 复活玩家
                    respawnPlayer(player);
                    respawnTimers.remove(playerId); // 移除复活记录
                    respawnTasks.remove(playerId); // 移除任务记录
                    this.cancel();
                    return;
                }

                // 显示关键倒计时
                if (timeLeft == 180 || timeLeft == 150 || timeLeft == 120 || timeLeft == 90 ||
                        timeLeft == 60 || timeLeft == 30 || timeLeft <= 10) {
                    player.sendTitle(
                            plugin.getMessage("cage_countdown_title", "&c%time%")
                                    .replace("%time%", String.valueOf(timeLeft)),
                            plugin.getMessage("cage_countdown_subtitle", ""),
                            0, 20, 0
                    );
                }

                respawnTimers.put(playerId, timeLeft); // 更新剩余时间
                timeLeft--;
            }
        };
        task.runTaskTimer(plugin, 0, 20); // 每秒执行一次
        respawnTasks.put(playerId, task); // 存储任务
    }

    // 随机偏移范围（±100格）
    private static final int OFFSET_RANGE = 100;
    private void respawnPlayer(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 10, 255)); // 给予10秒无敌
        player.setGameMode(GameMode.SURVIVAL);
        plugin.removeRealSpectator(player.getUniqueId()); // 移除旁观者标记

        // 如果是终章模式，强制复活在末地
        if (plugin.isFinalBattleMode()) {
            World endWorld = Bukkit.getWorld("world_the_end");
            if (endWorld != null) {
                // 获取玩家设置的床重生点（末地床）
                Location bedSpawn = player.getBedSpawnLocation();
                if (bedSpawn != null && bedSpawn.getWorld().getEnvironment() == World.Environment.THE_END) {
                    // 如果玩家有末地床重生点，则传送至床
                    player.teleport(bedSpawn);
                } else {
                    // 找到最近的逃生者位置（末地）
                    Location escaperLocation = findClosestEscaperInEnd();
                    if (escaperLocation != null) {
                        int respawnRadius = plugin.getConfig().getInt("game.hunter_respawn_radius", 50);
                        Location respawnLocation = findSafeLocationInEnd(endWorld, escaperLocation, respawnRadius);
                        player.teleport(respawnLocation);
                    } else {
                        // 没有逃生者，传送到末地安全出生点
                        Location endSpawn = findSafeLocationInEnd(endWorld, new Location(endWorld, 100, 70, 0), 10);
                        player.teleport(endSpawn);
                    }
                }
            }
        } else {
            World world = Bukkit.getWorld("world");
            if (world != null) {
                Location bedSpawn = player.getBedSpawnLocation();
                if (isValidVanillaHunterBedSpawn(bedSpawn, world)) {
                    player.teleport(bedSpawn);
                } else {
                    player.teleport(world.getSpawnLocation());
                }
            }
        }
        player.sendTitle(plugin.getMessage("resurrection", "&a你已复活！"), "", 10, 40, 10);

        // 延迟2秒后给予装备和特殊效果
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // 给予复活装备
            if (plugin.isFinalBattleMode()) {
                plugin.getFinalBattleProfessionManager().giveSelectedProfessionLoadout(player);
            } else {
                // 其他模式使用 hunter_resupply 配置
                if (plugin.getConfig().getBoolean("hunter_resupply.enable", false)) {
                    giveResupplyItems(player);
                }
                plugin.getHunterTracker().startTrackingHunter(player);
                plugin.giveSharedBackpack(player, true);
            }


            // 检查玩家是否选择了"爆炸弩"技能
            if ("爆炸弩".equals(plugin.getSkillManager().getSelectedSkill(player))
                    && plugin.getSkillManager().isSkillEnabled("爆炸弩")) {
                plugin.getExplosiveCrossbowListener().giveCrossbowPackage(player);
            }
        }, 40L); // 40 ticks = 2秒
    }


    private boolean isValidVanillaHunterBedSpawn(Location bedSpawn, World mainWorld) {
        return bedSpawn != null
                && bedSpawn.getWorld() != null
                && bedSpawn.getWorld().equals(mainWorld);
    }

    /**
     * 找到末地中最近的逃生者位置
     */
    private Location findClosestEscaperInEnd() {
        World endWorld = Bukkit.getWorld("world_the_end");
        if (endWorld == null) return null;

        Player closestEscaper = null;
        double minDistance = Double.MAX_VALUE;
        Location endCenter = new Location(endWorld, 100, 70, 0);

        // 查找末地的逃生者
        for (Player escaper : plugin.getEscapers()) {
            if (escaper == null || !escaper.isOnline()) continue;

            // 只计算末地的逃生者
            if (escaper.getWorld().getEnvironment() == World.Environment.THE_END) {
                double distance = escaper.getLocation().distance(endCenter);

                if (distance < minDistance) {
                    minDistance = distance;
                    closestEscaper = escaper;
                }
            }
        }

        return closestEscaper != null ? closestEscaper.getLocation() : null;
    }

    /**
     * 在末地中查找安全的复活位置
     */
    private Location findSafeLocationInEnd(World endWorld, Location center, int radius) {
        Random random = ThreadLocalRandom.current();
        int maxAttempts = 50;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // 随机偏移
            int offsetX = random.nextInt(radius * 2) - radius;
            int offsetZ = random.nextInt(radius * 2) - radius;

            int x = center.getBlockX() + offsetX;
            int z = center.getBlockZ() + offsetZ;

            // 从较高位置开始向下搜索安全位置
            for (int y = 120; y > 0; y--) {
                Location testLoc = new Location(endWorld, x + 0.5, y, z + 0.5);

                // 检查是否是安全位置
                if (isSafeLocation(testLoc)) {
                    return testLoc;
                }
            }
        }

        // 如果找不到安全位置，返回末地主岛中心的安全位置
        Location fallback = new Location(endWorld, 100.5, 70, 0.5);
        // 向上搜索安全位置
        for (int y = 70; y < 120; y++) {
            fallback.setY(y);
            if (isSafeLocation(fallback)) {
                return fallback;
            }
        }

        // 最后的保底位置
        return new Location(endWorld, 100.5, 80, 0.5);
    }

    /**
     * 检查位置是否安全（脚下有方块，头部和脚部位置是空气）
     */
    private boolean isSafeLocation(Location loc) {
        if (loc.getWorld() == null) return false;

        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        Block below = loc.clone().add(0, -1, 0).getBlock();

        // 脚下必须有固体方块，脚部和头部必须是空气或非固体方块
        return below.getType().isSolid() &&
               !feet.getType().isSolid() &&
               !head.getType().isSolid() &&
               !feet.isLiquid() &&
               !head.isLiquid() &&
               loc.getY() > 0; // 确保不在虚空
    }

    /**
     * 找到离逃生者最近的存活猎人位置（只在主世界，排除正在复活的猎人）
     */
    private Location findClosestHunterToEscaper() {
        Player closestHunter = null;
        double minDistance = Double.MAX_VALUE;
        World mainWorld = Bukkit.getWorld("world");

        // 遍历所有猎人，找到离任意逃生者最近的那个存活猎人
        for (Player hunter : plugin.getHunters()) {
            if (hunter == null || !hunter.isOnline()) continue;
            // 排除正在复活中的猎人（旁观者模式）
            if (hunter.getGameMode() == GameMode.SPECTATOR) continue;
            // 只考虑主世界的猎人
            if (!hunter.getWorld().equals(mainWorld)) continue;

            for (Player escaper : plugin.getEscapers()) {
                if (escaper == null || !escaper.isOnline()) continue;
                if (!hunter.getWorld().equals(escaper.getWorld())) continue;

                double distance = hunter.getLocation().distance(escaper.getLocation());
                if (distance < minDistance) {
                    minDistance = distance;
                    closestHunter = hunter;
                }
            }
        }

        return closestHunter != null ? closestHunter.getLocation() : null;
    }


    /**
     * 将玩家传送到指定位置的最高处，并在X/Z轴添加随机偏移
     * @param player 要传送的玩家
     * @param spawnLocation 基础传送位置
     */
    public void teleportWithRandomOffset(Player player, Location spawnLocation) {
        if (player == null || spawnLocation == null || spawnLocation.getWorld() == null) {
            return; // 安全检查：无效参数直接返回
        }

        World world = spawnLocation.getWorld();

        // 1. 计算该位置的最高Y坐标（避免传送到地下）
        int highestY = world.getHighestBlockYAt(spawnLocation) + 1; // +1防止卡在方块里
        Location highestLocation = new Location(world, spawnLocation.getX(), highestY, spawnLocation.getZ());

        // 2. 生成X/Z轴随机偏移（±100格）
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double xOffset = random.nextDouble(-OFFSET_RANGE, OFFSET_RANGE + 1); // -100到100（包含边界）
        double zOffset = random.nextDouble(-OFFSET_RANGE, OFFSET_RANGE + 1);

        // 3. 应用偏移并克隆位置（避免修改原始位置）
        Location offsetLocation = highestLocation.clone()
                .add(xOffset, 0, zOffset); // 仅偏移X和Z轴

        // 4. 二次安全检查：确保偏移后位置有效
        validateAndAdjustLocation(offsetLocation);

        // 5. 执行传送
        player.teleport(offsetLocation);
        player.sendMessage(plugin.getMessage("resurrection", "&a你已复活！"));
    }

    /**
     * 验证并调整位置，确保传送安全
     * @param location 需要检查的位置
     */
    private void validateAndAdjustLocation(Location location) {
        if (location.getWorld() == null) return;

        World world = location.getWorld();

        // 检查是否在虚空（Y轴过低）
        if (location.getY() < 1) {
            location.setY(world.getHighestBlockYAt(location) + 1);
        }

        // 检查是否在固体方块内
        while (location.getBlock().getType().isSolid()) {
            location.setY(location.getY() + 1); // 向上移动直到离开固体方块
        }

        // 检查上方是否有方块（防止头顶卡方块）
        Location headLocation = location.clone().add(0, 1.8, 0); // 玩家头部位置
        if (headLocation.getBlock().getType().isSolid()) {
            location.setY(location.getY() + 2); // 向上抬升2格
        }
    }

    public boolean isPlayerRespawning(UUID playerId) {
        return respawnTimers.containsKey(playerId);
    }

    public VoteSystem getVoteSystem() {
        return voteSystem;
    }

    /**
     * 给予复活装备（hunter_resupply配置）
     */
    private void giveResupplyItems(Player player) {
        FileConfiguration config = plugin.getConfig();
        long gameMinutes = plugin.getElapsedMinutes();

        // 获取所有时间段配置
        List<Map<?, ?>> timeStages = config.getMapList("hunter_resupply.time_stages");
        if (timeStages.isEmpty()) {
            plugin.getLogger().warning("hunter_resupply.time_stages 配置为空，无法发放装备！");
            return;
        }

        // 找到当前游戏时间对应的装备阶段（从后往前找，取最大满足条件的）
        Map<?, ?> currentStage = null;
        for (Map<?, ?> stage : timeStages) {
            int minTime = stage.containsKey("min_minutes") ? ((Number) stage.get("min_minutes")).intValue() : 0;
            if (gameMinutes >= minTime) {
                currentStage = stage;
            }
        }

        if (currentStage == null) {
            return; // 游戏时间未达到任何阶段
        }

        // 发放该阶段的装备
        List<Map<?, ?>> items = (List<Map<?, ?>>) currentStage.get("items");
        if (items == null || items.isEmpty()) return;

        org.bukkit.inventory.PlayerInventory inv = player.getInventory();

        for (Map<?, ?> itemMap : items) {
            String matName = itemMap.containsKey("material") ? (String) itemMap.get("material") : "STONE";
            int amount = itemMap.containsKey("amount") ? ((Number) itemMap.get("amount")).intValue() : 1;

            Material material = Material.matchMaterial(matName);
            if (material == null) {
                plugin.getLogger().warning("无效物品材质：" + matName + "，跳过");
                continue;
            }
            ItemStack item = new ItemStack(material, amount);
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            if (itemMap.containsKey("enchantments")) {
                List<Map<?, ?>> enchantList = (List<Map<?, ?>>) itemMap.get("enchantments");
                for (Map<?, ?> enchantMap : enchantList) {
                    String enchantType = enchantMap.containsKey("type") ? (String) enchantMap.get("type") : "";
                    int enchantLevel = enchantMap.containsKey("level") ? ((Number) enchantMap.get("level")).intValue() : 1;

                    Enchantment enchant = Enchantment.getByName(enchantType.toUpperCase());
                    if (enchant == null) {
                        plugin.getLogger().warning("无效附魔：" + enchantType + "，跳过");
                        continue;
                    }
                    int maxLevel = enchant.getMaxLevel();
                    if (enchantLevel < 1 || enchantLevel > maxLevel) {
                        enchantLevel = maxLevel;
                    }
                    meta.addEnchant(enchant, enchantLevel, false);
                }
                item.setItemMeta(meta);
            }

            String slotStr = itemMap.containsKey("slot") ? String.valueOf(itemMap.get("slot")) : "-1";
            switch (slotStr.toLowerCase()) {
                case "helmet":
                    inv.setHelmet(item);
                    break;
                case "chestplate":
                    inv.setChestplate(item);
                    break;
                case "leggings":
                    inv.setLeggings(item);
                    break;
                case "boots":
                    inv.setBoots(item);
                    break;
                case "-1":
                    inv.addItem(item);
                    break;
                default:
                    try {
                        int slot = Integer.parseInt(slotStr);
                        if (slot >= 0 && slot < 36) {
                            inv.setItem(slot, item);
                        } else {
                            inv.addItem(item);
                        }
                    } catch (NumberFormatException e) {
                        inv.addItem(item);
                    }
                    break;
            }
        }
    }

}

