package com.huntergame;

import com.huntergame.BE.BedrockTrackingGUI;
import com.huntergame.Gui.TrackingGUI;
import com.xigua.baseAPI.BaseAPI;
import com.xigua.baseAPI.api.events.ClientLoadAddonFinishEvent;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HunterTracker implements Listener {
    private static final String HUNTER_COMPASS_NAME = ChatColor.YELLOW + "猎人指南针";
    private static final String ESCAPER_COMPASS_NAME = ChatColor.YELLOW + "逃生者指南针";

    private final HunterGame plugin;
    private final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    // 这个 Map 既用于存储谁拥有指南针，也存储猎人的追踪状态（True=队友, False=逃生者）
    private final Map<UUID, Boolean> trackingTeammateStatus = new ConcurrentHashMap<>();

    public boolean COMPASS;
    public long COOLDOWN_TIME;
    public int DEDUCT_HEALTH;
    public int DETECTION_DISTANCE;
    public boolean ESCAPER_TRACKING_ENABLE;

    public HunterTracker(HunterGame plugin) {
        this.plugin = plugin;

        // 1. 启动全局任务：它负责处理所有猎人的 ActionBar 和指南针更新
        startGlobalHunterTrackingTask();

        FileConfiguration config = plugin.getConfig();
        COMPASS = config.getBoolean("game.compass.enable", true);
        COOLDOWN_TIME = config.getLong("game.compass.hunter_tp_cooldown", 300);
        DEDUCT_HEALTH = config.getInt("game.compass.deduct_health", 18);
        DETECTION_DISTANCE = config.getInt("game.compass.detection_distance", 50);
        ESCAPER_TRACKING_ENABLE = config.getBoolean("game.compass.escaper_tracking_enable", true);
    }

    @EventHandler
    public void onCompassUse(PlayerInteractEvent event) {
        if (!COMPASS) return;
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (event.getItem() == null || event.getItem().getType() != Material.COMPASS) return;

        long currentTime = System.currentTimeMillis();
        if (lastClickTime.containsKey(playerId)) {
            if (currentTime - lastClickTime.get(playerId) < 200) {
                event.setCancelled(true);
                return;
            }
        }
        lastClickTime.put(playerId, currentTime);

        boolean isHunter = plugin.isHunter(playerId);
        boolean isEscaper = plugin.isEscaper(playerId);

        if (isHunter) {
            if (event.getAction().toString().contains("RIGHT_CLICK")) {
                openTrackingGUI(player);
            }
            event.setCancelled(true);
        } else if (isEscaper || !plugin.isGameRunning()) {
            event.setCancelled(true);
        }
    }

    public void openTrackingGUI(Player player) {
        if (plugin.getBaseAPI() != null) {
            try {
                BedrockTrackingGUI.openBedrockTrackingMenu(plugin, this, player);
            } catch (Throwable e) {
                TrackingGUI.openTrackingGUI(player);
            }
        } else {
            TrackingGUI.openTrackingGUI(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title.equals("选择操作") || title.equals("选择队友")) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            if (title.equals("选择操作")) {
                if (clickedItem.getType() == Material.ENDER_PEARL) {
                    if (isEscaperNearby(player, DETECTION_DISTANCE)) {
                        player.sendMessage(plugin.getMessage("nearby_escape", "&c附近有逃生者，无法打开队友列表！"));
                        player.closeInventory();
                        return;
                    }
                    TrackingGUI.openTeammateListGUI(plugin, this, player);
                } else if (clickedItem.getType() == Material.COMPASS) {
                    switchToHunterTrackingTarget(player);
                }
            } else if (title.equals("选择队友")) {
                if (clickedItem.getType() == Material.PLAYER_HEAD) {
                    String teammateName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
                    if (isOnCooldown(player)) {
                        long timeLeft = getCooldownTimeLeft(player);
                        player.sendMessage(plugin.getMessage("waiting_countdown_teleport", "&c你需要等待 " + timeLeft + " 秒才能再次传送!"));
                        player.closeInventory();
                        return;
                    }
                    Player teammate = Bukkit.getPlayer(teammateName);
                    if (teammate != null && plugin.isHunter(teammate.getUniqueId()) && teammate.isOnline()) {
                        performTeleport(player, teammate);
                        player.closeInventory();
                    } else {
                        player.sendMessage(plugin.getMessage("Teammate_unavailable", "&c该队友当前不可用！"));
                        player.closeInventory();
                    }
                }
            }
        }
    }

    public boolean isEscaperNearby(Player player, double radius) {
        if (!plugin.isGameRunning()) return false;
        double radiusSquared = radius * radius;
        return player.getWorld().getPlayers().stream()
                .filter(target -> !target.equals(player))
                .filter(target -> plugin.isEscaper(target.getUniqueId()))
                .anyMatch(target -> target.getLocation().distanceSquared(player.getLocation()) <= radiusSquared);
    }

    public void performTeleport(Player player, Player target) {
        player.teleport(target.getLocation());
        player.sendMessage(plugin.getMessage("transferring_teammates", "&a你已传送到队友 " + target.getName() + " 的位置!"));
        double newHealth = player.getHealth() - DEDUCT_HEALTH;
        player.setHealth(Math.max(1, newHealth));
        startCooldown(player);
    }

    private void startCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public boolean isOnCooldown(Player player) {
        if (!cooldowns.containsKey(player.getUniqueId())) return false;
        long cooldownTime = cooldowns.get(player.getUniqueId());
        return System.currentTimeMillis() - cooldownTime < COOLDOWN_TIME * 1000L;
    }

    public long getCooldownTimeLeft(Player player) {
        long cooldownTime = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long timeLeftMillis = COOLDOWN_TIME * 1000L - (System.currentTimeMillis() - cooldownTime);
        return Math.max(0, timeLeftMillis / 1000);
    }

    /**
     * 【重要修改】全局任务：统一管理所有猎人的更新
     * 移除了 startTrackingHunter 单独任务，完全依靠这个循环
     */
    private void startGlobalHunterTrackingTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isGameRunning()) return;

                for (UUID hunterUUID : trackingTeammateStatus.keySet()) {
                    Player player = Bukkit.getPlayer(hunterUUID);
                    if (player == null || !player.isOnline()) {
                        stopTracking(hunterUUID);
                        continue;
                    }

                    if (plugin.isHunter(hunterUUID)) {
                        updateHunterCompassAndActionBar(player);
                    } else if (plugin.isEscaper(hunterUUID) && ESCAPER_TRACKING_ENABLE) {
                        updateEscaperCompassAndActionBar(player);
                    } else {
                        stopTracking(hunterUUID);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void updateHunterCompassAndActionBar(Player hunter) {
        UUID hunterUUID = hunter.getUniqueId();
        // 这里的 false 默认值意味着：如果玩家不在 Map 里，默认追踪逃生者
        boolean trackingTeammate = trackingTeammateStatus.getOrDefault(hunterUUID, false);

        if (hunter.getGameMode() != GameMode.SURVIVAL) {
            if (!plugin.isVanillaHunterMode()) {
                hunter.sendActionBar(plugin.getMessage("tracking_inactive", "&6追踪（非生存模式）"));
            }
            return;
        }

        Player target = trackingTeammate ? getNearestHunterTeammate(hunter) : getNearestEscaper(hunter);

        if (target != null) {
            setPlayerCompassTarget(hunter, target.getLocation());
        } else {
            setPlayerCompassTarget(hunter, hunter.getWorld().getSpawnLocation());
        }

        if (plugin.isVanillaHunterMode()) return;

        if (target != null) {
            double distance = hunter.getLocation().distance(target.getLocation());
            String targetType = trackingTeammate ? "队友" : "逃生者";
            hunter.sendActionBar(
                    plugin.getMessage("tracking_target", "&e正在跟踪: &c%target% &e(%type%) &c| 距离: &c%distance% 米")
                            .replace("%target%", target.getName())
                            .replace("%type%", targetType)
                            .replace("%distance%", String.format("%.1f", distance))
            );
        } else {
            hunter.sendActionBar(plugin.getMessage("no_target", "&6没有可追踪的目标！"));
        }
    }

    public void switchToHunterTrackingTarget(Player player) {
        UUID playerId = player.getUniqueId();
        boolean currentTrackingTeammate = trackingTeammateStatus.getOrDefault(playerId, false);
        boolean newTrackingTeammate = !currentTrackingTeammate;

        trackingTeammateStatus.put(playerId, newTrackingTeammate);
        player.closeInventory();
        String msg = newTrackingTeammate ? "&e已切换追踪目标：&a队友" : "&e已切换追踪目标：&c逃生者";
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));

        // 立即更新一次，无需等待下一秒
        updateHunterCompassAndActionBar(player);
    }

    public void assignCompassAndTracking(Player player, boolean isEscaper) {
        // 先停止任何可能的旧任务
        stopTracking(player.getUniqueId());

        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.setDisplayName(isEscaper ? ESCAPER_COMPASS_NAME : HUNTER_COMPASS_NAME);
        compass.setItemMeta(meta);
        player.getInventory().addItem(compass);

        // 【关键】将玩家加入 Map。
        // 如果是猎人，isEscaper=false。这会让全局任务接管该玩家。
        // putIfAbsent 保证如果玩家已经切换到"追踪队友(true)"模式，不会被重置回"追踪逃生者(false)"。
        trackingTeammateStatus.putIfAbsent(player.getUniqueId(), isEscaper);

        updateTrackingNow(player, isEscaper);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack droppedItem = event.getItemDrop().getItemStack();
        if (isTrackingCompass(droppedItem)) {
            event.setCancelled(true);
        }
    }

    private boolean isTrackingCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName()
                && (HUNTER_COMPASS_NAME.equals(meta.getDisplayName()) || ESCAPER_COMPASS_NAME.equals(meta.getDisplayName()));
    }

    private void stopTracking(UUID playerId) {
        trackingTeammateStatus.remove(playerId);

        lastClickTime.remove(playerId);
        cooldowns.remove(playerId);
    }

    private void updateTrackingNow(Player player, boolean isEscaper) {
        if (isEscaper && ESCAPER_TRACKING_ENABLE) {
            updateEscaperCompassAndActionBar(player);
        } else if (!isEscaper) {
            updateHunterCompassAndActionBar(player);
        }
    }

    public void startTrackingEscaper(Player escaper) {
        if (!ESCAPER_TRACKING_ENABLE) {
            return;
        }
        trackingTeammateStatus.put(escaper.getUniqueId(), true);
        updateEscaperCompassAndActionBar(escaper);
    }

    // --- 逃生者追踪逻辑 ---
    private void updateEscaperCompassAndActionBar(Player escaper) {
        if (escaper.getGameMode() != GameMode.SURVIVAL) {
            if (!plugin.isVanillaHunterMode()) {
                escaper.sendActionBar(plugin.getMessage("tracking_inactive", "&6追踪（非生存模式）"));
            }
            return;
        }

        Player nearestTeammate = getNearestEscaperTeammate(escaper);
        Player nearestHunter = getNearestSurvivalHunter(escaper);
        Location compassTarget = nearestTeammate != null ? nearestTeammate.getLocation() : escaper.getWorld().getSpawnLocation();
        setPlayerCompassTarget(escaper, compassTarget);

        if (plugin.isVanillaHunterMode()) return;

        if (nearestTeammate != null) {
            double teammateDistance = escaper.getLocation().distance(nearestTeammate.getLocation());
            if (nearestHunter != null && escaper.getWorld() == nearestHunter.getWorld()) {
                double hunterDistance = escaper.getLocation().distance(nearestHunter.getLocation());
                if (hunterDistance <= 30) {
                    escaper.sendActionBar(plugin.getMessage("tracking_danger", "&e最近的队友: &a%teammate% &e| 距离: &a%tDistance% 米  &c危险危险！").replace("%teammate%", nearestTeammate.getName()).replace("%tDistance%", String.format("%.1f", teammateDistance)));
                } else if (hunterDistance <= 150) {
                    escaper.sendActionBar(plugin.getMessage("tracking_nearby_hunter", "&e最近的队友: &a%teammate% &e| 距离: &a%tDistance% 米  &6附近有猎人！").replace("%teammate%", nearestTeammate.getName()).replace("%tDistance%", String.format("%.1f", teammateDistance)));
                } else {
                    escaper.sendActionBar(plugin.getMessage("tracking_safe", "&e最近的队友: &a%teammate% &e| 距离: &a%tDistance% 米  &a你很安全！").replace("%teammate%", nearestTeammate.getName()).replace("%tDistance%", String.format("%.1f", teammateDistance)));
                }
            } else {
                escaper.sendActionBar(plugin.getMessage("tracking_safe", "&e最近的队友: &a%teammate% &e| 距离: &a%tDistance% 米  &a你很安全！").replace("%teammate%", nearestTeammate.getName()).replace("%tDistance%", String.format("%.1f", teammateDistance)));
            }
        } else {
            if (nearestHunter != null && escaper.getWorld() == nearestHunter.getWorld()) {
                double hunterDistance = escaper.getLocation().distance(nearestHunter.getLocation());
                if (hunterDistance <= 30) {
                    escaper.sendActionBar(plugin.getMessage("tracking_danger_alone", "&e没有可追踪的队友  &c危险危险！").replace("%hDistance%", String.format("%.1f", hunterDistance)));
                } else if (hunterDistance <= 150) {
                    escaper.sendActionBar(plugin.getMessage("tracking_nearby_hunter_alone", "&e没有可追踪的队友  &6附近有猎人！").replace("%hDistance%", String.format("%.1f", hunterDistance)));
                } else {
                    escaper.sendActionBar(plugin.getMessage("tracking_safe_alone", "&e没有可追踪的队友  &a你很安全！"));
                }
            } else {
                escaper.sendActionBar(plugin.getMessage("tracking_none_teammate", "&e没有可追踪的队友  &a你很安全！"));
            }
        }
    }

    private void setPlayerCompassTarget(Player player, Location location) {
        if (player != null && player.isOnline()) {
            player.setCompassTarget(location);
        }
    }

    // 辅助获取最近玩家方法保持不变...
    private Player getNearestEscaperTeammate(Player escaper) {
        if (!plugin.isGameRunning() || escaper.getGameMode() != GameMode.SURVIVAL) return null;
        Location escaperLocation = escaper.getLocation();
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Player teammate : plugin.getEscapers()) {
            if (!teammate.equals(escaper) && teammate != null && teammate.isOnline() && teammate.getWorld().equals(escaper.getWorld()) && plugin.isEscaper(teammate.getUniqueId()) && teammate.getGameMode() == GameMode.SURVIVAL) {
                double distanceSquared = escaperLocation.distanceSquared(teammate.getLocation());
                if (distanceSquared < nearestDistance) {
                    nearestDistance = distanceSquared;
                    nearest = teammate;
                }
            }
        }
        return nearest;
    }

    private Player getNearestSurvivalHunter(Player escaper) {
        if (!plugin.isGameRunning() || escaper.getGameMode() != GameMode.SURVIVAL) return null;
        Player nearestHunter = null;
        double minDistance = Double.MAX_VALUE;
        for (Player hunter : plugin.getHunters()) {
            if (hunter != null && hunter.isOnline() && hunter.getGameMode() == GameMode.SURVIVAL && hunter.getWorld().equals(escaper.getWorld()) && plugin.isHunter(hunter.getUniqueId()) && !hunter.equals(escaper)) {
                double distanceSquared = escaper.getLocation().distanceSquared(hunter.getLocation());
                if (distanceSquared < minDistance) {
                    minDistance = distanceSquared;
                    nearestHunter = hunter;
                }
            }
        }
        return nearestHunter;
    }

    private Player getNearestEscaper(Player hunter) {
        if (!plugin.isGameRunning() || hunter.getGameMode() != GameMode.SURVIVAL) return null;
        Location hunterLocation = hunter.getLocation();
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Player escaper : plugin.getEscapers()) {
            if (escaper != null && escaper.isOnline() && escaper.getWorld().equals(hunter.getWorld()) && plugin.isEscaper(escaper.getUniqueId()) && escaper.getGameMode() == GameMode.SURVIVAL) {
                double distanceSquared = hunterLocation.distanceSquared(escaper.getLocation());
                if (distanceSquared < nearestDistance) {
                    nearestDistance = distanceSquared;
                    nearest = escaper;
                }
            }
        }
        return nearest;
    }

    private Player getNearestHunterTeammate(Player hunter) {
        if (!plugin.isGameRunning() || hunter.getGameMode() != GameMode.SURVIVAL) return null;
        Location hunterLocation = hunter.getLocation();
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Player teammate : plugin.getHunters()) {
            if (!teammate.equals(hunter) && teammate != null && teammate.isOnline() && teammate.getWorld().equals(hunter.getWorld()) && plugin.isHunter(teammate.getUniqueId()) && teammate.getGameMode() == GameMode.SURVIVAL) {
                double distanceSquared = hunterLocation.distanceSquared(teammate.getLocation());
                if (distanceSquared < nearestDistance) {
                    nearestDistance = distanceSquared;
                    nearest = teammate;
                }
            }
        }
        return nearest;
    }

    public void cleanup() {
        trackingTeammateStatus.clear();
        lastClickTime.clear();
        cooldowns.clear();
        Bukkit.getOnlinePlayers().forEach(player -> {
            player.setCompassTarget(player.getWorld().getSpawnLocation());
        });
    }
}
