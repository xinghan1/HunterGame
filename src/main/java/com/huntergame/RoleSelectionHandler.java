package com.huntergame;

import com.huntergame.BE.BedrockRoleSelectionGUI;
import com.huntergame.Gui.RoleSelectionGUI;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.UUID;

public class RoleSelectionHandler implements Listener {
    public static final String ROLE_GUI_TITLE = ChatColor.GOLD + "选择角色";
    public static final NamespacedKey IS_HUNTER = new NamespacedKey("huntergame", "is_hunter");
    public static final NamespacedKey IS_ESCAPER = new NamespacedKey("huntergame", "is_escaper");
    private final HunterGame plugin;
    private final DisconnectProtection disconnectProtection;
    public int GAME_TIME_LIMIT_MINUTES;
    public double ESCAPER_RATIO_THRESHOLD;

    public RoleSelectionHandler(HunterGame plugin, DisconnectProtection disconnectProtection) {
        this.plugin = plugin;
        this.disconnectProtection = disconnectProtection;
        FileConfiguration config = plugin.getConfig();
        GAME_TIME_LIMIT_MINUTES = config.getInt("game.game_time_limit_minutes", 30);
        ESCAPER_RATIO_THRESHOLD = config.getDouble("game.escaper_ratio_threshold", 3.0);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        player.setGameMode(GameMode.SPECTATOR);

        if (plugin.isDeathescapers(playerId)) {
            player.sendMessage(plugin.getMessage("game_death_escapers", "&7你已死亡，现在变成旁观者"));
            return;
        }

        if (plugin.getStartGameCommand().isPlayerRespawning(playerId)) {
            return;
        }
        if (disconnectProtection.hasOfflineProtectionData(player.getUniqueId())) {
            disconnectProtection.cleanupPlayerData(player.getUniqueId());
            player.setGameMode(GameMode.SURVIVAL);
            return;
        }

        if (plugin.isFinalBattleMode()) {
            World targetWorld = Bukkit.getWorld("world_the_end");
            if (targetWorld == null) {
                player.sendMessage("§c目标世界 world_the_end 不存在！");
                return; // 添加返回，避免空指针后续逻辑
            }
            Location targetLocation = new Location(targetWorld, 0, 100, 0);
            player.teleport(targetLocation);
            player.sendMessage(plugin.getMessage("game_final", "&7当前处于终章之战，无法中途加入"));
            return;
        }

        if (plugin.isGameRunning()) {
            World targetWorld = Bukkit.getWorld("world");
            if (targetWorld == null) {
                player.sendMessage("§c目标世界 world 不存在！");
            } else {
                player.teleport(targetWorld.getHighestBlockAt(0, 0).getLocation().add(0.5, 1, 0.5));
            }

            long gameDurationMinutes = plugin.getElapsedMinutes();
            if (gameDurationMinutes >= GAME_TIME_LIMIT_MINUTES) {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(plugin.getMessage("game_too_time", "&7游戏已进行 %time% 分钟，无法中途加入")
                        .replace("%time%", String.valueOf(GAME_TIME_LIMIT_MINUTES)));
                return;
            }

            plugin.playersWithoutRole.add(player);

            // 延迟调用统一的打开方法
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                openRoleSelectionGUI(player);
            }, 100L);
        }
    }

    /**
     * 统一打开角色选择 GUI 的方法
     */
    public void openRoleSelectionGUI(Player player) {
        if (plugin.getBaseAPI() != null) {
            try {
                BedrockRoleSelectionGUI.openBedrockRoleSelection(plugin, this, player);
            } catch (Throwable e) {
                RoleSelectionGUI.openRoleSelectionGUI(plugin, this, player);
            }
        } else {
            RoleSelectionGUI.openRoleSelectionGUI(plugin, this, player);
        }
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        Inventory clickedInventory = event.getClickedInventory();

        // 检查玩家是否在等待选择角色的集合中
        if (!plugin.playersWithoutRole.contains(player)) {
            return; // 不是等待选择角色的玩家，忽略
        }

        // 检查是否点击了玩家自己的背包（上半部分是GUI，下半部分是玩家背包）
        if (clickedInventory == null || clickedInventory.equals(player.getInventory())) {
            event.setCancelled(true); // 取消点击玩家自己的背包
            return;
        }

        // 取消事件，防止拿走物品
        event.setCancelled(true);

        int hunterSlot = plugin.getGuiConfig().getInt("join_midway_role-gui.hunter.slot", 3);
        int escaperSlot = plugin.getGuiConfig().getInt("join_midway_role-gui.escaper.slot", 4);
        int spectatorSlot = plugin.getGuiConfig().getInt("join_midway_role-gui.spectator.slot", 5);

        double escaperRatioThreshold = plugin.getConfig().getDouble("game.escaper_ratio_threshold", ESCAPER_RATIO_THRESHOLD);
        int hunterCount = plugin.getHunters().size();
        int escaperCount = plugin.getEscapers().size();
        double ratio = (escaperCount > 0) ? (double) hunterCount / escaperCount : hunterCount;
        boolean showEscaperOption = ratio >= escaperRatioThreshold;

        if (event.getSlot() == hunterSlot) {
            selectHunterRole(player, playerId);
            player.getInventory().clear();
        } else if (showEscaperOption && event.getSlot() == escaperSlot) {
            selectEscaperRole(player, playerId);
            player.getInventory().clear();
        } else if (event.getSlot() == spectatorSlot) {
            selectSpectatorRole(player);
        }
    }

    // ================== 公共角色选择逻辑方法 (供 GUI 类调用) ==================

    public void selectSpectatorRole(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
        plugin.addRealSpectator(player.getUniqueId()); // 标记为真正的旁观者
        player.sendMessage(plugin.getMessage("choose_spectator", "&7你已选择成为旁观者！"));
        plugin.playersWithoutRole.remove(player);
        player.closeInventory();
        // 传送到随机玩家位置
        plugin.teleportSpectatorToRandomPlayer(player);
    }

    public void selectHunterRole(Player player, UUID playerId) {
        player.getInventory().clear();
        player.setGameMode(GameMode.SURVIVAL);
        plugin.addHunter(playerId);
        plugin.playersWithoutRole.remove(player);
        player.getPersistentDataContainer().set(IS_HUNTER, PersistentDataType.BOOLEAN, true);
        player.getPersistentDataContainer().set(IS_ESCAPER, PersistentDataType.BOOLEAN, false);

        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "追踪指南针(右键打开)");
        compass.setItemMeta(meta);
        player.getInventory().setItem(0, compass);


        player.closeInventory();
        Bukkit.broadcastMessage(
                plugin.getMessage("remaining_players", "&e剩余玩家: &c%hunters% 猎人 &e| &b%escapers% 逃生者")
                        .replace("%hunters%", String.valueOf(plugin.getHunters().size()))
                        .replace("%escapers%", String.valueOf(plugin.getEscapers().size()))
        );
        teleportNewHunterNearRandomHunter(player);

        if (plugin.isSkillHunterMode()) {
            plugin.giveSharedBackpack(player, true);
            plugin.getSkillManager().openSkillSelection(player);
        }
    }

    public void selectEscaperRole(Player player, UUID playerId) {
        player.getInventory().clear();
        plugin.addEscaper(playerId);
        plugin.playersWithoutRole.remove(player);
        plugin.getStartGameCommand().giveEscaperMark(player);
        player.getInventory().clear();
        player.setSaturation(20.0F);
        plugin.hunterTracker.assignCompassAndTracking(player, true);
        plugin.getGameListener().cancelShutdownTimer();

        player.getPersistentDataContainer().set(IS_HUNTER, PersistentDataType.BOOLEAN, false);
        player.getPersistentDataContainer().set(IS_ESCAPER, PersistentDataType.BOOLEAN, true);

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 0, false, false));
        player.closeInventory();

        Bukkit.broadcastMessage(
                plugin.getMessage("remaining_players", "&e剩余玩家: &c%hunters% 猎人 &e| &b%escapers% 逃生者")
                        .replace("%hunters%", String.valueOf(plugin.getHunters().size()))
                        .replace("%escapers%", String.valueOf(plugin.getEscapers().size()))
        );

        teleportEscaperToRandomLocation(player);
        if (plugin.isSkillHunterMode()) {
            plugin.getSkillManager().openSkillSelection(player);
            plugin.giveSharedBackpack(player, false);
        }
    }

    // ==========================================================

    private void teleportPlayer(Player player, double x, double z) {
        World world = Bukkit.getWorld("world");
        if (world != null) {
            int highestY = world.getHighestBlockYAt((int) x, (int) z);
            Location location = new Location(world, x, highestY + 1, z);
            player.teleport(location);
        } else {
            player.sendMessage(plugin.getMessage("transmit_no_world", "&c无法找到默认世界，传送失败！"));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        if (plugin.playersWithoutRole.contains(player)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> openRoleSelectionGUI(player), 1L);
            player.sendMessage(plugin.getMessage("select_character", "&a你必须选择一个角色才能继续游戏！"));
        }
    }

    private void teleportNewHunterNearRandomHunter(Player newHunter) {
        List<Player> currentHunters = plugin.getHunters();
        currentHunters.remove(newHunter);

        if (currentHunters.isEmpty()) {
            teleportPlayer(newHunter, 0, 0);
        } else {
            int index = (int) (Math.random() * currentHunters.size());
            Player targetHunter = currentHunters.get(index);
            Location targetLoc = targetHunter.getLocation();

            newHunter.teleport(targetLoc);
            newHunter.sendMessage(plugin.getMessage("join_game_teleport",  "&e你已传送到 " + targetHunter.getName() + " 的位置！"));
        }
    }

    private void teleportEscaperToRandomLocation(Player newEscaper) {
        World world = newEscaper.getWorld();
        int maxAttempts = 50;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = -1000 + (int) (Math.random() * 2001);
            int z = -1000 + (int) (Math.random() * 2001);
            int y = getSafeYCoordinate(world, x, z);

            if (y > 0) {
                Location safeLocation = new Location(world, x, y, z);
                if (isSafeLocation(safeLocation)) {
                    newEscaper.teleport(safeLocation);
                    return;
                }
            }
        }

        teleportPlayer(newEscaper, 0, 0);
        newEscaper.sendMessage(plugin.getMessage("safe_location_not_found", "未能找到安全位置，已传送到默认点！"));
        newEscaper.setGameMode(GameMode.SURVIVAL);
    }

    private int getSafeYCoordinate(World world, int x, int z) {
        for (int y = 255; y > 0; y--) {
            Block block = world.getBlockAt(x, y, z);
            Block headBlock = world.getBlockAt(x, y + 1, z);
            if (block.getType().isSolid() && !headBlock.getType().isSolid() && !isLiquid(headBlock)) {
                return y + 1;
            }
        }
        return -1;
    }

    private boolean isSafeLocation(Location location) {
        if (location == null) return false;
        Block feetBlock = location.getBlock();
        Block headBlock = location.clone().add(0, 1, 0).getBlock();
        Block belowBlock = location.clone().add(0, -1, 0).getBlock();
        return belowBlock.getType().isSolid()
                && !feetBlock.getType().isSolid()
                && !headBlock.getType().isSolid()
                && !isLiquid(feetBlock)
                && !isLiquid(headBlock);
    }

    private boolean isLiquid(Block block) {
        Material material = block.getType();
        return material == Material.WATER || material == Material.LAVA;
    }
}
