package com.huntergame.placeholder;

import com.huntergame.HunterGame;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


public class HunterGamePlaceholder extends PlaceholderExpansion implements Listener {

    private final HunterGame plugin;
    private static Location bastionLocation = null;
    private static Location fortressLocation = null;
    private final Map<UUID, Integer> tierCache = new ConcurrentHashMap<>();
    private final AtomicBoolean tierRefreshInProgress = new AtomicBoolean();
    private boolean bastionSearchRunning = false;
    private boolean fortressSearchRunning = false;

    public HunterGamePlaceholder(HunterGame plugin) {
        this.plugin = plugin;
        scheduleWeeklyTierRefresh();
        refreshAllTiers(false);
    }

    /**
     * 每周一凌晨4点刷新全服排名
     */
    private void scheduleWeeklyTierRefresh() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMonday4am = now.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).withHour(4).withMinute(0).withSecond(0).withNano(0);
        // 如果当前就是周一且还没到4点，用本周一
        if (now.getDayOfWeek() == DayOfWeek.MONDAY && now.getHour() < 4) {
            nextMonday4am = now.withHour(4).withMinute(0).withSecond(0).withNano(0);
        }

        long delaySeconds = Duration.between(now, nextMonday4am).getSeconds();
        long delayTicks = delaySeconds * 20L;
        long oneWeekTicks = 7L * 24 * 60 * 60 * 20; // 一周的tick数

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> refreshAllTiers(true), delayTicks, oneWeekTicks);
    }

    /**
     * 批量刷新所有玩家的排名缓存
     */
    public void refreshAllTiers() {
        refreshAllTiers(true);
    }

    public void refreshAllTiersSilently() {
        refreshAllTiers(false);
    }

    private void refreshAllTiers(boolean log) {
        if (!tierRefreshInProgress.compareAndSet(false, true)) return;
        plugin.getDataStorageManager().getAllPlayerTiersAsync().thenAccept(allTiers -> {
            tierCache.clear();
            tierCache.putAll(allTiers);
            if (log) plugin.getLogger().info("猎人游戏全服排名已刷新。");
        }).whenComplete((ignored, error) -> {
            tierRefreshInProgress.set(false);
            if (error != null) plugin.getLogger().warning("全服排名跨服刷新失败: " + error.getMessage());
        });
    }

    @Override
    public @NotNull String getIdentifier() {
        return "huntergame";
    }

    @Override
    public @NotNull String getAuthor() {
        return "你的名字";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return null;
        }
        UUID playerId = player.getUniqueId();

        // 角色
        if (params.equalsIgnoreCase("role")) {
            if (plugin.isHunter(playerId)) {
                return plugin.getMessage("placeholder_role_hunter", "&c猎人");
            } else if (plugin.isEscaper(playerId)) {
                return plugin.getMessage("placeholder_role_escaper", "&b逃生者");
            } else if (plugin.isDeathescapers(playerId)) {
                return plugin.getMessage("placeholder_role_dead_escaper", "&b逃生者 死亡");
            } else {
                return plugin.getMessage("placeholder_role_unassigned", "&7未分配");
            }
        }

        // 模式显示占位符
        if (params.equalsIgnoreCase("mode")) {
            if (plugin.getConfig().getBoolean("mode-selection.toggle.fixed-mode-enabled", false)) {
                int fixedModeId = plugin.getConfig().getInt("mode-selection.toggle.fixed-mode-id", 2);
                String fixedModeName = switch (fixedModeId) {
                    case 2 -> plugin.getMessage("placeholder_mode_final_battle", "&c终章");
                    case 3 -> plugin.getMessage("placeholder_mode_vanilla_hunter", "&a经典猎人");
                    default -> plugin.getMessage("placeholder_mode_not_started", "&c未开始");
                };

                // 根据模式ID匹配对应名称
                return fixedModeName;
            }

            // 判断当前模式并返回对应文本
            if (plugin.isFinalBattleMode()) {
                return plugin.getMessage("placeholder_mode_final_battle", "&c终章");
            } else if (plugin.isVanillaHunterMode()){
                return plugin.getMessage("placeholder_mode_vanilla_hunter", "&a原版猎人");
            } else {
                return plugin.getMessage("placeholder_mode_not_started", "&7未开始");
            }
        }

        // 猎人数量
        if (params.equalsIgnoreCase("hunter_count")) {
            return String.valueOf(plugin.getHunters().size());
        }

        // 逃生者数量
        if (params.equalsIgnoreCase("escaper_count")) {
            return String.valueOf(plugin.getEscapers().size());
        }

        // 击杀数量
        if (params.equalsIgnoreCase("kills")) {
            return String.valueOf(plugin.getDataStorageManager().getKills(player.getUniqueId()));
        }

        // 总击杀数量
        if (params.equalsIgnoreCase("kills_put")) {
            return String.valueOf(plugin.getDataStorageManager().getKillsput(player.getUniqueId()));
        }

        // 死亡次数
        if (params.equalsIgnoreCase("deaths")) {
            return String.valueOf(plugin.getDataStorageManager().getDeaths(player.getUniqueId()));
        }

        // 游戏次数
        if (params.equalsIgnoreCase("games_played")) {
            return String.valueOf(plugin.getDataStorageManager().getGamesPlayed(player.getUniqueId()));
        }

        // 猎人胜利次数
        if (params.equalsIgnoreCase("hunter_wins")) {
            return String.valueOf(plugin.getDataStorageManager().getHunterWin(player.getUniqueId()));
        }

        // 逃生者胜利次数
        if (params.equalsIgnoreCase("escape_wins")) {
            return String.valueOf(plugin.getDataStorageManager().getEscapeWin(player.getUniqueId()));
        }

        // 总胜利次数
        if (params.equalsIgnoreCase("total_wins")) {
            return String.valueOf(plugin.getDataStorageManager().getTotalWins(player.getUniqueId()));
        }

        // 游戏时间
        if (params.equalsIgnoreCase("gametime")) {
            return plugin.getFormattedGameTime();
        }

        // 熟练度
        if (params.equalsIgnoreCase("proficiency")) {
            return String.valueOf(plugin.getDataStorageManager().getProficiency(player));
        }

        // 段位等级
        if (params.equalsIgnoreCase("rank")) {
            double proficiency = plugin.getDataStorageManager().getProficiency(player);
            return plugin.getRankManager().getRankName(proficiency);
        }

        if (params.equalsIgnoreCase("fortress")) {
            return fortressLocation != null ? formatLocation(fortressLocation) : "未找到";
        }

        if (params.equalsIgnoreCase("bastion")) {
            return bastionLocation != null ? formatLocation(bastionLocation) : "未找到";
        }

        if (params.equalsIgnoreCase("portal")) {
            return plugin.getPortalCoordinatesPlaceholder(player);
        }

        // 赛季id
        if (params.equalsIgnoreCase("season")) {
            return String.valueOf(plugin.getSeasonManager().getCurrentSeasonId());
        }

        // 全服熟练度排名
        if (params.equalsIgnoreCase("tier")) {
            Integer tier = tierCache.get(player.getUniqueId());
            return tier != null ? String.valueOf(tier) : "暂无数据";
        }

        return null;
    }



    @EventHandler
    public void onPlayerEnterNether(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        World nether = Bukkit.getWorld("world_nether");

        if (event.getTo() != null && nether != null && event.getTo().getWorld().equals(nether)) {
            startFortressSearchTask(player, nether);
            startBastionSearchTask(player, nether);
        }
    }

    private void startBastionSearchTask(Player player, World nether) {
        if (bastionLocation != null || bastionSearchRunning) {
            return;
        }
        bastionSearchRunning = true;
        new BukkitRunnable() {
            private int attempts = 0;

            @Override
            public void run() {
                if (bastionLocation != null || attempts++ >= 12) {
                    bastionSearchRunning = false;
                    cancel();
                    return;
                }

                Location bastion = nether.locateNearestStructure(player.getLocation(), StructureType.BASTION_REMNANT, 200, false);
                if (bastion != null) {
                    bastionLocation = bastion;
                    bastionSearchRunning = false;
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 20L * 15);
    }

    private void startFortressSearchTask(Player player, World nether) {
        if (fortressLocation != null || fortressSearchRunning) {
            return;
        }
        fortressSearchRunning = true;
        new BukkitRunnable() {
            private int attempts = 0;

            @Override
            public void run() {
                if (fortressLocation != null || attempts++ >= 12) {
                    fortressSearchRunning = false;
                    cancel();
                    return;
                }

                Location fortress = nether.locateNearestStructure(player.getLocation(), StructureType.NETHER_FORTRESS, 200, false);
                if (fortress != null) {
                    fortressLocation = fortress;
                    fortressSearchRunning = false;
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 20L * 15);
    }

    static String formatLocation(Location location) {
        return String.format("%d,%d,%d", location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}

