package com.huntergame.listener;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HunterRespawnListener implements Listener {
    private final HunterGame plugin;

    public HunterRespawnListener(HunterGame plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        FileConfiguration config = plugin.getConfig();

        if (!plugin.isGameRunning()) {
            Location lobbyLocation = plugin.getLobbyLocation();
            if (lobbyLocation != null) {
                Bukkit.getScheduler().runTask(plugin, () -> player.teleport(lobbyLocation));
            }
            return;
        }

        if (!plugin.isHunter(playerId)) return;

        if (plugin.isFinalBattleMode() && plugin.isRealSpectator(playerId)) {
            return;
        }

        // 如果玩家正在复活倒计时中，不在这里给装备
        if (plugin.getStartGameCommand().isPlayerRespawning(playerId)) return;

        // 延迟2秒后给予装备
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.isFinalBattleMode()) {
                plugin.getFinalBattleProfessionManager().giveSelectedProfessionLoadout(player);
                return;
            }

            // 否则使用 hunter_resupply 配置
            if (config.getBoolean("hunter_resupply.enable", false)) {
                giveResupplyItems(player);
            }
            plugin.getHunterTracker().startTrackingHunter(player);
            plugin.giveSharedBackpack(player, true);
            plugin.getPermissionRecipeManager().giveUnlockedRecipeBook(player);
        }, 40L); // 40 ticks = 2秒
    }

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

        PlayerInventory inv = player.getInventory();

        for (Map<?, ?> itemMap : items) {
            String matName = itemMap.containsKey("material") ? (String) itemMap.get("material") : "STONE";
            int amount = itemMap.containsKey("amount") ? ((Number) itemMap.get("amount")).intValue() : 1;

            Material material = Material.matchMaterial(matName);
            if (material == null) {
                plugin.getLogger().warning("无效物品材质：" + matName + "，跳过");
                continue;
            }
            ItemStack item = new ItemStack(material, amount);
            ItemMeta meta = item.getItemMeta();
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

