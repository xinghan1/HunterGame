package com.huntergame.game;

import com.huntergame.HunterGame;
import com.huntergame.role.RoleSelectionHandler;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class FinalBattleManager implements Listener {
    private static final double VANILLA_ENDER_DRAGON_HEALTH = 200.0;

    private final HunterGame plugin;
    private final Map<UUID, Integer> playerVotes;

    private final List<Player> finalBattleEscapers = new ArrayList<>();
    private final List<Player> hunters = new ArrayList<>();

    private boolean gameActive = false;
    private final Map<UUID, Set<Location>> playerCages = new HashMap<>();
    private final Map<UUID, Integer> taskIds = new HashMap<>();
    private final Set<Location> barrierBlocks = new HashSet<>();
    private final Map<UUID, Integer> titleTaskIds = new HashMap<>();

    public FinalBattleManager(HunterGame plugin, Map<UUID, Integer> votes) {
        this.plugin = plugin;
        this.playerVotes = votes;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void startFinalBattle() {
        // 初始化游戏状态
        gameActive = true;
        plugin.setGameInProgress(true);
        plugin.startGame();

        // 角色分配
        assignRoles();

        // 世界准备
        World endWorld = getOrCreateEndWorld();
        if (endWorld == null) {
            Bukkit.broadcastMessage(plugin.getMessage("final_battle_end_world_failed", "&c末地世界加载失败！"));
            return;
        }

        // 开局给予无敌时间
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 35 * 20, 0, false));
        }
        // 传送玩家
        teleportPlayers(endWorld);
        openProfessionSelection();
        createCagesForAllPlayers();
        restoreExistingDragonHealth(endWorld);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.broadcastMessage(plugin.getMessage("final_battle_started", "&a===== 终章之战 已启动 ====="));

            if (plugin.isPersistenceBattle()) {
                int minutes = plugin.getConfig().getInt("game.persistence_modes.final_battle_minutes", 15);
                Bukkit.broadcastMessage(plugin.getMessage("final_battle_persistence_mode_title", "&e【生存战模式】"));
                Bukkit.broadcastMessage(plugin.getMessage("final_battle_persistence_escaper_objective", "&7• 逃生者目标：存活 %minutes% 分钟 •")
                        .replace("%minutes%", String.valueOf(minutes)));
                Bukkit.broadcastMessage(plugin.getMessage("final_battle_persistence_hunter_objective", "&7• 猎人目标：阻止逃生者，歼灭战 •"));
            } else {
                Bukkit.broadcastMessage(plugin.getMessage("final_battle_clearance_mode_title", "&c【通关战模式】"));
                Bukkit.broadcastMessage(plugin.getMessage("final_battle_clearance_escaper_objective", "&7• 逃生者目标：击杀末影龙 •"));
                Bukkit.broadcastMessage(plugin.getMessage("final_battle_clearance_hunter_objective", "&7• 猎人目标：阻止逃生者，歼灭战 •"));
            }


            Bukkit.broadcastMessage(plugin.getMessage("final_battle_team_ratio", "&7• 阵营： %escapers%名逃生者 vs %hunters%名猎人 •")
                    .replace("%escapers%", String.valueOf(finalBattleEscapers.size()))
                    .replace("%hunters%", String.valueOf(hunters.size())));

            for (Player hunter : plugin.getHunters()) {
                hunter.sendMessage(plugin.getMessage("hunter_identity", "&a你是 &c猎人！"));
            }
            for (Player escaper : plugin.getEscapers()) {
                escaper.sendMessage(plugin.getMessage("escaper_identity", "&a你是 &b逃生者！"));
            }
        }, 30L);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    private void restoreExistingDragonHealth(World world) {
        for (Entity entity : world.getEntities()) {
            if (entity instanceof EnderDragon dragon) {
                AttributeInstance maxHealthAttribute = dragon.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (maxHealthAttribute != null) {
                    maxHealthAttribute.setBaseValue(VANILLA_ENDER_DRAGON_HEALTH);
                    dragon.setHealth(VANILLA_ENDER_DRAGON_HEALTH);
                }
                break;
            }
        }
    }

    // 为所有玩家创建屏障
    private void createCagesForAllPlayers() {
        List<Player> allPlayers = new ArrayList<>(hunters);
        allPlayers.addAll(finalBattleEscapers); // 添加所有逃生者

        for (Player player : allPlayers) {
            createCage(player);
        }
    }

    // 创建玩家屏障
    public void createCage(Player player) {
        UUID playerId = player.getUniqueId();
        Location center = player.getLocation().clone();
        center = center.getBlock().getLocation().add(0.5, 2, 0.5); // 整体上抬，避免加厚底板替换下方地形

        Set<Location> cageBlocks = new HashSet<>();
        int innerRadius = 1;
        int barrierThickness = 2;
        int outerRadius = innerRadius + barrierThickness;
        int innerMinY = 0;
        int innerMaxY = 2;
        int outerMinY = innerMinY - barrierThickness;
        int outerMaxY = innerMaxY + barrierThickness;

        player.teleport(center.clone().add(0, innerMinY, 0));

        // 内部空间保持不变，外壳加厚到2格屏障
        for (int x = -outerRadius; x <= outerRadius; x++) {
            for (int y = outerMinY; y <= outerMaxY; y++) {
                for (int z = -outerRadius; z <= outerRadius; z++) {
                    boolean isInnerAir =
                            Math.abs(x) <= innerRadius &&
                                    Math.abs(z) <= innerRadius &&
                                    y >= innerMinY && y <= innerMaxY;

                    Location loc = center.clone().add(x, y, z);
                    Block block = loc.getBlock();

                    if (isInnerAir) {
                        block.setType(Material.AIR);
                    } else {
                        block.setType(Material.BARRIER);
                        cageBlocks.add(loc);
                        barrierBlocks.add(loc);
                    }
                }
            }
        }

        playerCages.put(player.getUniqueId(), cageBlocks);

        // 根据阵营设置不同消失时间（逃生者30秒，猎人35秒）
        int totalSeconds = plugin.isEscaper(playerId) ? 30 : 35;
        int delayTicks = totalSeconds * 20;

        // 显示标题和倒计时
        startTitleCountdown(player, totalSeconds);

        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                removeCage(player);
                taskIds.remove(player.getUniqueId());

                cancelTitleTask(playerId);
                player.sendTitle(
                        plugin.getMessage("final_battle_cage_start_title", "&a游戏已开始！"),
                        plugin.getMessage("final_battle_cage_start_subtitle", ""),
                        10, 40, 10
                );
            }
        }.runTaskLater(plugin, delayTicks).getTaskId();

        taskIds.put(player.getUniqueId(), taskId);
    }

    /**
     * 取消玩家的标题倒计时任务
     */
    private void cancelTitleTask(UUID playerId) {
        if (titleTaskIds.containsKey(playerId)) {
            Bukkit.getScheduler().cancelTask(titleTaskIds.get(playerId));
            titleTaskIds.remove(playerId);
        }
    }

    /**
     * 为单个玩家启动标题倒计时
     */
    private void startTitleCountdown(Player player, int totalSeconds) {
        UUID playerId = player.getUniqueId();

        // 立即显示初始标题
        player.sendTitle(
                plugin.getMessage("final_battle_cage_countdown_title", "&e请注意当前环境是否安全"),
                plugin.getMessage("final_battle_cage_countdown_subtitle", "&c准备开始：%seconds%秒")
                        .replace("%seconds%", String.valueOf(totalSeconds)),
                0, 20, 0
        );

        // 启动倒计时任务
        int titleTaskId = new BukkitRunnable() {
            int remaining = totalSeconds - 1;

            @Override
            public void run() {
                if (remaining <= 0) {
                    this.cancel();
                    return;
                }

                player.sendTitle(
                        plugin.getMessage("final_battle_cage_countdown_title", "&e请注意当前环境是否安全"),
                        plugin.getMessage("final_battle_cage_countdown_subtitle", "&c准备开始：%seconds%秒")
                                .replace("%seconds%", String.valueOf(remaining)),
                        0, 20, 0 // 每次显示1秒
                );
                remaining--;
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId(); // 延迟1秒（20 ticks）后开始，每秒执行

        titleTaskIds.put(playerId, titleTaskId);
    }

    // 移除玩家屏障
    private void removeCage(Player player) {
        UUID playerId = player.getUniqueId();
        if (playerCages.containsKey(playerId)) {
            // 移除屏障方块
            for (Location loc : playerCages.get(playerId)) {
                Block block = loc.getBlock();
                if (block.getType() == Material.BARRIER) {
                    block.setType(Material.AIR);
                }
            }
            playerCages.remove(playerId);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnderPearlUse(PlayerInteractEvent event) {
        if (!isPearlLockedPlayer(event.getPlayer())) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ENDER_PEARL) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(plugin.getMessage("final_battle_pearl_locked", "&c开局保护期间不能使用末影珍珠！"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnderPearlLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl) || !(pearl.getShooter() instanceof Player player)) {
            return;
        }

        if (!isPearlLockedPlayer(player)) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(plugin.getMessage("final_battle_pearl_locked", "&c开局保护期间不能使用末影珍珠！"));
    }

    private boolean isPearlLockedPlayer(Player player) {
        return gameActive && player != null && playerCages.containsKey(player.getUniqueId());
    }

    private void assignRoles() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        finalBattleEscapers.clear();
        hunters.clear();

        // 动态获取需要多少名逃生者
        int playerCount = players.size();
        int targetEscaperCount = getTargetEscaperCount(playerCount);

        // 从投票逃生者中选择
        List<Player> escaperCandidates = players.stream()
                .filter(p -> playerVotes.getOrDefault(p.getUniqueId(), 0) == 1)
                .collect(Collectors.toList());

        Collections.shuffle(escaperCandidates); // 打乱候选人

        // 填充逃生者列表
        // 先从投票者中选
        while (finalBattleEscapers.size() < targetEscaperCount && !escaperCandidates.isEmpty()) {
            finalBattleEscapers.add(escaperCandidates.remove(0));
        }

        // 如果还不够，从剩余玩家中随机选（排除已选的）
        if (finalBattleEscapers.size() < targetEscaperCount) {
            List<Player> remainingPlayers = new ArrayList<>(players);
            remainingPlayers.removeAll(finalBattleEscapers);
            Collections.shuffle(remainingPlayers);

            while (finalBattleEscapers.size() < targetEscaperCount && !remainingPlayers.isEmpty()) {
                finalBattleEscapers.add(remainingPlayers.remove(0));
            }
        }

        // 其余玩家分配为猎人
        for (Player p : players) {
            if (!finalBattleEscapers.contains(p)) {
                hunters.add(p);
                plugin.addHunter(p.getUniqueId());
                p.getPersistentDataContainer().set(RoleSelectionHandler.IS_HUNTER, PersistentDataType.BOOLEAN, true);
            }
        }

        // 初始化逃生者状态
        for (Player esc : finalBattleEscapers) {
            plugin.addEscaper(esc.getUniqueId());
            esc.getPersistentDataContainer().set(RoleSelectionHandler.IS_ESCAPER, PersistentDataType.BOOLEAN, true);
            applyGlowingEffect(esc);
        }
    }

    private int getTargetEscaperCount(int totalPlayers) {
        int count = plugin.getConfig().getInt("player_counts.final_battle.scaling.default", 1);

        ConfigurationSection thresholds = plugin.getConfig().getConfigurationSection("player_counts.final_battle.scaling.thresholds");
        if (thresholds != null) {
            // 获取所有配置的键（例如 "13", "8"），解析为整数
            List<Integer> sortedThresholds = thresholds.getKeys(false).stream()
                    .map(key -> {
                        try {
                            return Integer.parseInt(key);
                        } catch (NumberFormatException e) {
                            return -1; // 忽略非数字键
                        }
                    })
                    .filter(key -> key > 0)
                    .sorted(Collections.reverseOrder()) // 降序排列 (例如: 13, 8)
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
     * 给玩家添加永久发光效果
     */
    private void applyGlowingEffect(Player player) {
        PotionEffect glowing = new PotionEffect(
                PotionEffectType.GLOWING,  // 效果类型：发光
                Integer.MAX_VALUE,         // 持续时间：无限
                0,                         // 等级：0
                false,                     // 是否隐藏粒子效果
                false                      // 是否隐藏效果图标
        );
        player.addPotionEffect(glowing);
    }

    private World getOrCreateEndWorld() {
        World end = Bukkit.getWorld("world_the_end");
        if (end == null) {
            WorldCreator creator = new WorldCreator("world_the_end");
            creator.environment(World.Environment.THE_END);
            end = Bukkit.createWorld(creator);
        }
        if (end != null) {
            end.setTime(6000);
            end.setStorm(false);
            end.setThundering(false);
        }
        return end;
    }

    private void teleportPlayers(World endWorld) {
        // 末地主岛中心坐标
        Location center = new Location(endWorld, 100, 70, 0);

        // 逃生者出生点 (所有逃生者在同一位置)
        Location escLoc = findSafeLocation(endWorld, center, 0);

        for (Player esc : finalBattleEscapers) {
            esc.teleport(escLoc);
            esc.setBedSpawnLocation(escLoc, true);
        }

        // 猎人出生点（距离30米）
        Location huntLoc = findOffsetLocation(endWorld, center, 30);
        for (Player h : hunters) {
            h.teleport(huntLoc);
            h.setBedSpawnLocation(huntLoc, true);
        }
    }

    private void openProfessionSelection() {
        List<Player> players = new ArrayList<>(finalBattleEscapers);
        players.addAll(hunters);
        plugin.getFinalBattleProfessionManager().startSelection(players);
    }


    private Location findSafeLocation(World world, Location center, double offset) {
        Random rand = new Random();
        int attempts = 0;

        while (attempts < 100) {
            double angle = rand.nextDouble() * Math.PI * 2;
            double x = center.getX() + Math.cos(angle) * offset;
            double z = center.getZ() + Math.sin(angle) * offset;

            // 找到最高方块
            int y = world.getHighestBlockYAt((int) x, (int) z);
            Location loc = new Location(world, x, y + 1, z);

            if (isLocationSafe(loc)) {
                return loc;
            }

            attempts++;
        }
        return center;
    }

    private Location findOffsetLocation(World world, Location center, double distance) {
        // 寻找与中心位置相隔指定距离的安全位置
        Random rand = new Random();
        double angle = rand.nextDouble() * Math.PI * 2;

        double x = center.getX() + Math.cos(angle) * distance;
        double z = center.getZ() + Math.sin(angle) * distance;

        // 找到最高方块
        int y = world.getHighestBlockYAt((int) x, (int) z);
        Location loc = new Location(world, x, y + 1, z);

        if (isLocationSafe(loc)) {
            return loc;
        }

        // 如果不安全，尝试其他方法或返回中心位置
        return findSafeLocation(world, center, distance);
    }

    private boolean isLocationSafe(Location loc) {
        // 检查位置是否安全
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        Block below = loc.clone().add(0, -1, 0).getBlock();

        return !feet.getType().isSolid() &&
                !head.getType().isSolid() &&
                below.getType().isSolid() &&
                !feet.isLiquid() &&
                !head.isLiquid();
    }
}

