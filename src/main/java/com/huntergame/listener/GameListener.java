package com.huntergame.listener;

import com.huntergame.HunterGame;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class GameListener implements Listener {
    private final HunterGame plugin;
    private int shutdownTimerTaskId = -1; // 倒计时任务ID
    private int shutdownSecondsLeft; // 剩余秒数
    private boolean isShutdownScheduled = false; // 是否已启动倒计时

    public boolean isShutdownScheduled() {
        return isShutdownScheduled;
    }

    public void setShutdownScheduled(boolean shutdownScheduled) {
        isShutdownScheduled = shutdownScheduled;
    }

    private static final String ITEM_KEY = "server_selector";
    private String serverName;
    private boolean bungeecord_enable;
    public GameListener(HunterGame plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        serverName = config.getString("BungeeCord.server_lobby", "lobby");
        bungeecord_enable = config.getBoolean("BungeeCord.enable", false);
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (plugin.isServerClosing()) {
            event.disallow(
                    PlayerLoginEvent.Result.KICK_OTHER,
                    ChatColor.RED + "游戏已结束！"
            );
        }
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Location lobbyLocation = plugin.getLobbySpawnLocation(); // 使用新的大厅位置获取方法

        // 如果倒计时进行中且加入的是逃生者
        if (isShutdownScheduled() && plugin.isEscaper(player.getUniqueId())) {
            cancelShutdownTimer();
        }

        // 如果游戏未开始
        if (!plugin.isGameRunning()) {
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }


            player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20); // 设置最大生命值

            if (lobbyLocation != null) {
                player.teleport(lobbyLocation);
                // 设置玩家模式为冒险模式
                player.setGameMode(GameMode.ADVENTURE);
                // player.sendMessage("作者：星汉XING");
                player.sendMessage(plugin.getMessage("welcome_message", "&a欢迎来到猎人游戏！"));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.getInventory().clear();
                    createServerSelector(player); // 给予末影之眼
                    plugin.getGuideManager().giveTriggerItem(player);
                }, 5L);
            } else {
                player.sendMessage(plugin.getMessage("lobby_not_set", "&c大厅位置未正确设置，请联系管理员！"));
            }

            // 自定义加入消息（替换默认的退出消息）
            event.setJoinMessage(
                    plugin.getMessage("player_join", "&a&l[+] &f%player% (&e%online%&a/%max%)")
                            .replace("%player%", event.getPlayer().getName())
                            .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                            .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()))
            );
        } else {
            event.setJoinMessage(plugin.getMessage("player_join_start", "&a&l[+] &e%player%").replace("%player%", event.getPlayer().getName()));
            if (plugin.isEscaper(playerId)){
                plugin.getHunterTracker().startTrackingEscaper(player);
            }


        }



    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        plugin.getDataStorageManager().addRank(playerId, player); // 更新段位在数据库里

        // 清理旁观者标记
        plugin.removeRealSpectator(playerId);

        // 清理角色和模式投票记录
        if (plugin.getStartGameCommand() != null && plugin.getStartGameCommand().getVoteSystem() != null) {
            plugin.getStartGameCommand().getVoteSystem().clearPlayerVote(playerId);
        }

        // 先检查是否是逃生者
        boolean isEscaper = plugin.isEscaper(playerId);

        // 检查是否最后一个逃生者退出
        if (isEscaper) {
            // 获取当前逃生者数量（包含当前退出的玩家）
            int remainingEscapers = plugin.getEscapers().size();

            // 如果当前玩家是最后一个逃生者
            if (remainingEscapers == 1) {
                startShutdownTimer();
            }
        }

        // 如果玩家是逃生者
        if (plugin.isEscaper(playerId)) {
            plugin.removeEscaper(playerId);
        }
        // 如果玩家是猎人
        if (plugin.isHunter(playerId)) {
            plugin.removeHunter(playerId);
        }

        // 判断是否在游戏进行中
        if (!plugin.isGameRunning()) {
            // 等待阶段：显示退出消息
            event.setQuitMessage(
                    plugin.getMessage("player_quit", "&c&l[-] &e%player% (&e%online%&c/%max%)")
                            .replace("%player%", player.getName())
                            .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size() - 1))
                            .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()))
            );

        } else {
            // 游戏进行中：更新剩余玩家数量并广播
            event.setQuitMessage(null); // 禁止默认退出消息
            event.setQuitMessage(plugin.getMessage("player_quit_start", "&c&l[-] &e%player%").replace("%player%", player.getName()));

        }
    }

    // 启动倒计时
    public void startShutdownTimer() {
        if (isShutdownScheduled) return; // 防止重复启动

        isShutdownScheduled = true;
        FileConfiguration config = plugin.getConfig();
        shutdownSecondsLeft = config.getInt("game.escapers_quit_countdown", 120);

        // 广播初始消息
        Bukkit.broadcastMessage(ChatColor.RED + "所有逃生者已退出，" + shutdownSecondsLeft + "秒后猎人胜利！");

        // 启动倒计时任务
        shutdownTimerTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            shutdownSecondsLeft--;

            if (shutdownSecondsLeft <= 0) {
                // 1. 关键：立即取消倒计时任务，防止重复执行
                Bukkit.getScheduler().cancelTask(shutdownTimerTaskId);
                shutdownTimerTaskId = -1; // 重置任务ID，避免后续误操作
                isShutdownScheduled = false; // 重置倒计时状态，标记为已结束

                // 增加所有猎人的胜利次数
                for (Player hunter : plugin.getHunters()) {
                    plugin.getDataStorageManager().addHunterWin(hunter.getUniqueId(), hunter);
                    plugin.getGameRewards().giveHunterReward(hunter); // 执行猎人胜利指令奖励
                }

                // 给所有玩家发送胜利标题
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    plugin.getDataStorageManager().saveTotalWins(onlinePlayer.getUniqueId(), onlinePlayer);
                    onlinePlayer.sendTitle(
                            plugin.getMessage("hunters_victory_title", "&6恭喜！"),
                            plugin.getMessage("hunters_victory_subtitle", "&c猎人胜利！"),
                            10, 70, 20
                    );
                }
                plugin.resetGame();
                return;
            }

            // 每秒广播剩余时间
            if (shutdownSecondsLeft % 5 == 0 || shutdownSecondsLeft <= 5) {
                Bukkit.broadcastMessage(ChatColor.YELLOW + "剩余时间: " + shutdownSecondsLeft + "秒");
            }

        }, 20L, 20L); // 延迟20 ticks（1秒），间隔20 ticks
    }

    // 取消倒计时
    public void cancelShutdownTimer() {
        if (shutdownTimerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(shutdownTimerTaskId);
            shutdownTimerTaskId = -1;
        }
        isShutdownScheduled = false;
        Bukkit.broadcastMessage(ChatColor.GREEN + "逃生者已回归，取消倒计时！");
    }


    // 创建特殊末影之眼
    private void createServerSelector(Player player) {
        // 1. 获取配置文件（从插件主类获取）
        FileConfiguration config = plugin.getConfig();

        String materialStr = config.getString("BungeeCord.server_selector.material", "ENDER_EYE");
        Material material = Material.matchMaterial(materialStr); // 转换为 Material 枚举
        if (material == null) { // 材质无效时用默认值
            material = Material.ENDER_EYE;
            plugin.getLogger().warning("配置的物品材质 " + materialStr + " 无效");
        }

        String displayName = config.getString("BungeeCord.server_selector.display_name", "§c§l离开游戏");

        List<String> lore = config.getStringList("BungeeCord.server_selector.lore");
        if (lore.isEmpty()) {
            lore = Arrays.asList("§7右键传送到主城");
        }
        // 2.4 背包槽位（默认 8，范围 0-35）
        int slot = config.getInt("BungeeCord.server_selector.slot", 8);
        if (slot < 0 || slot > 35) {
            slot = 8;
            plugin.getLogger().warning("配置的槽位 " + slot + " 无效（需 0-35），使用默认槽位 8");
        }
        String pdcKeyStr = "huntergame_server_selector";
        NamespacedKey pdcKey = new NamespacedKey(plugin, pdcKeyStr);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(pdcKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        player.getInventory().setItem(slot, item);
    }



    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();

        // ① 防跨服物品丢弃（判断 PDC 标记）
        if (isCrossServerItem(item)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessage("cross_server_cannot_drop", "&c跨服物品不能丢弃！"));
            return;
        }

        // ② 你原有其他物品的防丢弃逻辑（保留不变）
        if (isNonDroppable(item)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessage("cannot_drop", "&c该物品不可丢弃！"));
        }
    }

    // 判断物品是否不可丢弃
    private boolean isNonDroppable(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        NamespacedKey nonDroppableKey = new NamespacedKey(plugin, "non_droppable");
        return meta.getPersistentDataContainer().has(nonDroppableKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Location lobbyLocation = plugin.getLobbyLocation();


        // 游戏未开始，直接返回
        if (!plugin.isGameRunning()) {
            Bukkit.getScheduler().runTask(plugin, () -> player.teleport(lobbyLocation));
        }

        if (plugin.isFinalBattleMode()) {
           return;
        }

        // 判断玩家是否是猎人
        if (plugin.isHunter(playerId)) {
            // 给猎人指南针
            ItemStack compass = new ItemStack(Material.COMPASS);
            ItemMeta meta = compass.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + "追踪指南针(右键打开)");
            compass.setItemMeta(meta);
            player.getInventory().setItem(0, compass);


            if (!plugin.isVanillaHunterMode()) {
                plugin.giveSharedBackpack(player, true); // 给予猎人背包共享
            }

        }
    }



    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity(); // 死亡玩家
        UUID playerId = player.getUniqueId();
        Player killer = player.getKiller(); // 击杀者
        Location lobbyLocation = plugin.getLobbyLocation();

        // 游戏未开始，直接返回
        if (!plugin.isGameRunning()) {
            player.teleport(lobbyLocation); // 游戏未开始，每次死亡返回等待大厅
            return;
        }

        // 增加击杀者的击杀数
        if (killer != null) {
            plugin.getDataStorageManager().addKill(killer.getUniqueId());
            plugin.getDataStorageManager().addKillput(killer.getUniqueId(), killer);
        }
        plugin.getDataStorageManager().addDeath(player.getUniqueId(), player);

        // 判断死亡玩家是否是逃生者
        if (plugin.isEscaper(playerId)) {
            // 移除逃生者并设置为旁观者模式
            plugin.removeEscaper(playerId);
            plugin.addDeathescapers(playerId); // 标记逃生者死亡
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(plugin.getMessage("death_to_spectator", "&7你已经死亡，现在变成了旁观者。"));

            if (plugin.isFinalBattleMode()) { // 终章
                plugin.getDataStorageManager().addProficiency(player, plugin.getRankManager().getFinalBattle_EscaperDeathReward());
            } else if (plugin.isVanillaHunterMode()) { // 原版
                plugin.getDataStorageManager().addProficiency(player, plugin.getRankManager().getOrdinaryBattle_EscaperDeathReward());
            } else if (plugin.isSkillHunterMode()) { // 技能
                plugin.getDataStorageManager().addProficiency(player, plugin.getRankManager().getSkillBattle_EscaperDeathReward());
            }

            // 广播剩余逃生者人数
            Bukkit.broadcastMessage(
                    plugin.getMessage("remaining_escapers", "&b逃生者还剩: %escapers% 人")
                            .replace("%escapers%", String.valueOf(plugin.getEscapers().size()))
            );

            // 检查逃生者是否全部死亡
            if (plugin.getEscapers().isEmpty()) {
                Bukkit.broadcastMessage(plugin.getMessage("hunters_win", "&c所有逃生者已被猎人击败！恭喜猎人胜利！"));

                // 增加所有猎人的胜利次数
                for (Player hunter : plugin.getHunters()) {
                    plugin.getDataStorageManager().addHunterWin(hunter.getUniqueId(), player);
                    plugin.getGameRewards().giveHunterReward(hunter); // 执行猎人胜利指令奖励
                }

                // 给所有玩家发送胜利标题
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    plugin.getDataStorageManager().saveTotalWins(onlinePlayer.getUniqueId(), onlinePlayer);
                    UUID onlinePlayerId = onlinePlayer.getUniqueId();

                    // 修正判断逻辑：检查当前在线玩家是否是死亡的逃生者
                    boolean isDeathEscaper = plugin.isDeathescapers(onlinePlayerId);

                    if (isDeathEscaper) {

                        if (plugin.isFinalBattleMode()) { // 终章
                            plugin.getDataStorageManager().addProficiency(player, plugin.getRankManager().getFinalBattle_EscaperFailReward());
                        } else if (plugin.isVanillaHunterMode()) { // 原版
                            plugin.getDataStorageManager().addProficiency(player, plugin.getRankManager().getOrdinaryBattle_EscaperFailReward());
                        } else if (plugin.isSkillHunterMode()) { // 技能
                            plugin.getDataStorageManager().addProficiency(player, plugin.getRankManager().getSkillBattle_EscaperFailReward());
                        }

                        // 死亡的逃生者看到失败消息
                        onlinePlayer.sendTitle(
                                plugin.getMessage("escapers_defeat_title", "&c你失败了！"),
                                plugin.getMessage("escapers_defeat_subtitle", "&f未能逃脱猎人的追杀..."),
                                10, 100, 20
                        );
                        // 修复：传递正确的玩家对象给奖励方法
                        plugin.getGameRewards().giveEscaperFailReward(onlinePlayer);
                    } else if (plugin.isHunter(onlinePlayerId)) {

                        if (plugin.isFinalBattleMode()) { // 终章
                            plugin.getDataStorageManager().addProficiency(player, plugin.getRankManager().getFinalBattle_HunterWinReward());
                        } else if (plugin.isVanillaHunterMode()) { // 原版
                            plugin.getDataStorageManager().addProficiency(player, plugin.getRankManager().getOrdinaryBattle_HunterWinReward());
                        } else if (plugin.isSkillHunterMode()) { // 技能
                            plugin.getDataStorageManager().addProficiency(player, plugin.getRankManager().getSkillBattle_HunterWinReward());
                        }
                        // 猎人看到胜利消息
                        onlinePlayer.sendTitle(
                                plugin.getMessage("hunters_victory_title", "&a恭喜你！"),
                                plugin.getMessage("hunters_victory_subtitle", "&f成功追杀所有逃生者"),
                                10, 100, 20
                        );
                    } else {
                        // 其他玩家（旁观者）看到中立消息
                        onlinePlayer.sendTitle(
                                plugin.getMessage("game_over_title", "&6游戏结束！"),
                                plugin.getMessage("game_over_subtitle", "&c猎人获得了胜利！"),
                                10, 100, 20
                        );
                    }
                }

                // 重置游戏
                plugin.resetGame();
            }
        } else {
            if (plugin.isFinalBattleMode()) { // 终章
                plugin.getDataStorageManager().addProficiency(player, plugin.getRankManager().getFinalBattle_HunterDeathReward());
            } else if (plugin.isVanillaHunterMode()) { // 原版
                plugin.getDataStorageManager().addProficiency(player, plugin.getRankManager().getOrdinaryBattle_HunterDeathReward());
            } else if (plugin.isSkillHunterMode()) { // 技能
                plugin.getDataStorageManager().addProficiency(player, plugin.getRankManager().getSkillBattle_HunterDeathReward());
            }
        }
    }



    // 在伤害事件监听器中添加检测逻辑
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // 检查被攻击的是否是末影龙
        if (event.getEntity() instanceof EnderDragon) {
            // 检查攻击者是否是玩家
            if (event.getDamager() instanceof Player) {
                Player attacker = (Player) event.getDamager();
                // 判断该玩家是否为猎人
                if (plugin.isHunter(attacker.getUniqueId())) {
                    // 如果是猎人，设置伤害为0
                    event.setDamage(0.0);
                }
            }
        }
    }


    @EventHandler
    public void onEnderDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) return;

        // 特殊规则：如果是持久战模式 且 是终章之战，击杀龙不直接胜利
        if (plugin.isPersistenceBattle() && plugin.isFinalBattleMode()) {
            Bukkit.broadcastMessage(ChatColor.RED + "末影龙已被击败！");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "注意：当前为持久战模式，逃生者仍需存活至时间结束才能获胜！");

            // 给予一些额外奖励，但不结束游戏
            Player killer = event.getEntity().getKiller();
            if (killer != null && plugin.isEscaper(killer.getUniqueId())) {
                plugin.getDataStorageManager().addProficiency(killer, 1.5); // 额外熟练度
                killer.sendMessage(ChatColor.GOLD + "你击杀了末影龙！获得额外奖励，请继续生存！");
            }
            return;
        }

        Bukkit.broadcastMessage(plugin.getMessage("escaper_win", "&c末影龙已被逃生者击败！逃生者胜利！"));

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            // 胜利归属判断：逃生者 或 死亡逃生者
            if (plugin.getEscapers().contains(player) || plugin.isDeathescapers(uuid)) {
                plugin.getDataStorageManager().addEscapeWin(uuid, player);
                plugin.getGameRewards().giveEscaperReward(player);
            }
            // 保存胜利总数
            plugin.getDataStorageManager().saveTotalWins(uuid, player);
            // 显示标题并给予失败奖励（猎人）
            if (plugin.isHunter(uuid)) {
                if (plugin.isFinalBattleMode()) { // 终章
                    plugin.getDataStorageManager().addProficiency(player, plugin.getRankManager().getFinalBattle_HunterFailReward());
                } else if (plugin.isVanillaHunterMode()) { // 原版
                    plugin.getDataStorageManager().addProficiency(player, plugin.getRankManager().getOrdinaryBattle_HunterFailReward());
                } else if (plugin.isSkillHunterMode()) { // 技能
                    plugin.getDataStorageManager().addProficiency(player, plugin.getRankManager().getSkillBattle_HunterFailReward());
                }
                player.sendTitle(
                        plugin.getMessage("hunters_defeat_title_2", "&c你失败了！"),
                        plugin.getMessage("hunters_defeat_subtitle_2", "&f猎人未能阻止逃生者..."),
                        10, 100, 20
                );
                plugin.getGameRewards().giveHunterFailReward(player); // 给予失败的猎人的奖励
            } else if (plugin.isEscaper(uuid)) {

                if (plugin.isFinalBattleMode()) { // 终章
                    plugin.getDataStorageManager().addProficiency(player, plugin.getRankManager().getFinalBattle_EscaperWinReward());
                } else if (plugin.isVanillaHunterMode()) { // 原版
                    plugin.getDataStorageManager().addProficiency(player, plugin.getRankManager().getOrdinaryBattle_EscaperWinReward());
                } else if (plugin.isSkillHunterMode()) { // 技能
                    plugin.getDataStorageManager().addProficiency(player, plugin.getRankManager().getSkillBattle_EscaperWinReward());
                }
                // 显示胜利标题（逃生者）
                player.sendTitle(
                        plugin.getMessage("escapers_victory_title_2", "&a恭喜你！"),
                        plugin.getMessage("escapers_victory_subtitle_2", "&f成功逃脱猎人的追杀"),
                        10, 100, 20
                );
            } else {
                player.sendTitle(
                        plugin.getMessage("watch_victory_title_2", "&a游戏结束！"),
                        plugin.getMessage("watch_victory_subtitle_2", "&f逃生者获得了胜利！"),
                        10, 100, 20
                );
            }
        }

        // 延迟重置游戏
        plugin.resetGame();
    }



    @EventHandler
    public void onPlayerDamageByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();

        Player attacker = null;

        // 判断攻击者类型
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        // 如果最终确认攻击来源不是玩家，直接返回
        if (attacker == null) {
            return;
        }

        // 防止判断玩家自己攻击自己时触发队友保护
        if (attacker.equals(victim)) {
            return;
        }

        // 屏障期间禁止 PvP
        if (plugin.glassCageManager.isInCage(attacker) || plugin.glassCageManager.isInCage(victim)) {
            event.setCancelled(true);
            return;
        }

        // 队伍逻辑判断
        UUID attackerUUID = attacker.getUniqueId();
        UUID victimUUID = victim.getUniqueId();

        // 如果攻击者和被攻击者是同一阵营，则取消伤害
        if ((plugin.isHunter(attackerUUID) && plugin.isHunter(victimUUID)) ||
                (plugin.isEscaper(attackerUUID) && plugin.isEscaper(victimUUID))) {
            event.setCancelled(true); // 取消伤害
            attacker.sendMessage(plugin.getMessage("team-damage", "&c你不能攻击你的队友！"));
        }
    }

    // 右键触发跨服
    @EventHandler
    public void onClick(PlayerInteractEvent e) {
        // 1. 只处理右键动作（空中/方块）
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK
            && e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = e.getPlayer();
        ItemStack item = e.getItem();

        // 2. 检查物品是否存在且有元数据
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        NamespacedKey crossServerKey = new NamespacedKey(plugin, "huntergame_server_selector");
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        // 4. 检查是否为跨服物品（匹配 PDC 键）
        if (!pdc.has(crossServerKey, PersistentDataType.BYTE)) {
            // 非跨服物品，不处理（可选添加反馈，避免玩家困惑）
            // player.sendMessage(ChatColor.GRAY + "这不是跨服传送物品");
            return;
        }

        // 5. 是跨服物品，取消默认动作（防止放置/使用物品）
        e.setCancelled(true);

        // 6. 调用跨服方法，并添加反馈
        if (connectToServer(player, serverName)) {
            player.sendMessage(plugin.getMessage("bungeecord_connecting", "&a正在连接到 " + serverName + " 服务器..."));
        } else {
            player.sendMessage(plugin.getMessage("bungeecord_connect", "&c跨服连接失败，请联系管理员！"));
        }
    }

    // 跨服传送方法
    private boolean connectToServer(Player player, String server) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect"); // BungeeCord 固定指令，用于切换服务器
            out.writeUTF(server);    // 目标服务器名（从配置读取的 lobby）
            // 关键：发送插件消息时，频道必须是 "BungeeCord"（区分大小写）
            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
            return true; // 连接请求发送成功
        } catch (Exception e) {
            plugin.getLogger().warning("跨服连接失败：" + e.getMessage());
            return false; // 连接请求发送失败
        }
    }

    // 禁止丢弃物品
    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        checkAndCancel(e.getItemDrop().getItemStack(), e);
    }

    // 禁止移动物品
    @EventHandler
    public void onMoveItem(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        if (isVoteItem(clickedItem) || isVoteItem(cursorItem)) {
            event.setCancelled(true);
            return;
        }

        // ① 防跨服物品移动（判断点击的物品或鼠标上的物品）
        if (isCrossServerItem(clickedItem) || isCrossServerItem(cursorItem)) {
            event.setCancelled(true);
            return;
        }

        // ② 你原有其他物品的防移动逻辑（保留不变）
        checkAndCancel(clickedItem, event);
        checkAndCancel(cursorItem, event);
    }

    @EventHandler
    public void onOffhandSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = event.getMainHandItem(); // 主手物品
        ItemStack offHand = event.getOffHandItem();   // 副手物品

        // 主手或副手有跨服物品，都禁止切换
        if (isCrossServerItem(mainHand) || isCrossServerItem(offHand)) {
            event.setCancelled(true);
        }

        if (isVoteItem(mainHand) || isVoteItem(offHand)) {
            event.setCancelled(true);
        }
    }

    // 通用检查方法
    private void checkAndCancel(ItemStack item, Cancellable event) {
        if (item == null || !item.hasItemMeta()) return;

        NamespacedKey key = new NamespacedKey(plugin, ITEM_KEY);
        if (item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    // 补充容器转移检查
    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();

        // ① 防跨服物品通过容器转移
        if (isCrossServerItem(item)) {
            event.setCancelled(true);
            return;
        }

        if (isVoteItem(item)) {
            event.setCancelled(true);
            return;
        }

        // ② 你原有其他物品的防转移逻辑（保留不变）
        checkAndCancel(item, event);
    }

    private boolean isCrossServerItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        // 使用跨服物品的唯一 PDC 键（与 createServerSelector 中一致）
        NamespacedKey crossServerKey = new NamespacedKey(plugin, "huntergame_server_selector");
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        // 检查 PDC 中是否存在跨服物品的标记
        return pdc.has(crossServerKey, PersistentDataType.BYTE);
    }

    public boolean isVoteItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        // 使用投票物品的专属PDC键
        NamespacedKey voteKey = new NamespacedKey(plugin, "huntergame_vote_item");
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(voteKey, PersistentDataType.BYTE);
    }

}
