package com.huntergame.listener;

import com.huntergame.HunterGame;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
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

        // 检查是否为猎人
        if (!plugin.isHunter(playerId)) return;

        // 如果游戏未运行，不给装备
        if (!plugin.isGameRunning()) return;

        // 如果玩家正在复活倒计时中，不在这里给装备（会在 respawnPlayer 方法中给）
        if (plugin.getStartGameCommand().isPlayerRespawning(playerId)) return;

        // 延迟2秒后给予装备（用于非倒计时的普通重生，如游戏开始时）
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // 如果是终章模式，使用 final_battle.hunter 配置
            if (plugin.isFinalBattleMode()) {
                giveFinalBattleEquipment(player);
                return;
            }

            // 否则使用 hunter_resupply 配置
            if (!config.getBoolean("hunter_resupply.enable", false)) return;
            giveResupplyItems(player);
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
        int triggerTime = 0;
        for (Map<?, ?> stage : timeStages) {
            int minTime = stage.containsKey("min_minutes") ? ((Number) stage.get("min_minutes")).intValue() : 0;
            if (gameMinutes >= minTime) {
                currentStage = stage;
                triggerTime = minTime;
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

    /**
     * 给予终章模式猎人装备
     */
    private void giveFinalBattleEquipment(Player player) {
        FileConfiguration config = plugin.getConfig();
        String configPath = "final_battle.hunter";

        // 发放武器
        if (config.contains(configPath + ".weapon")) {
            String weapon = config.getString(configPath + ".weapon");
            ItemStack weaponItem = parseItem(weapon);
            if (weaponItem != null) {
                player.getInventory().setItemInMainHand(weaponItem);
            }
        }

        // 发放盔甲
        String[] armorSlots = {"helmet", "chestplate", "leggings", "boots"};
        EquipmentSlot[] equipmentSlots = {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };

        for (int i = 0; i < armorSlots.length; i++) {
            String part = armorSlots[i];
            if (config.contains(configPath + "." + part)) {
                String armorConfig = config.getString(configPath + "." + part);
                ItemStack armorItem = parseItem(armorConfig);
                if (armorItem != null) {
                    player.getEquipment().setItem(equipmentSlots[i], armorItem);
                }
            }
        }

        // 发放其他物品
        if (config.contains(configPath + ".items")) {
            for (String itemConfig : config.getStringList(configPath + ".items")) {
                ItemStack item = parseItem(itemConfig);
                if (item != null) {
                    player.getInventory().addItem(item);
                }
            }
        }
    }

    /**
     * 解析物品配置字符串（格式: 物品类型:数量:附魔1=等级,附魔2=等级）
     */
    private ItemStack parseItem(String configStr) {
        String[] parts = configStr.split(":");
        if (parts.length < 1) return null;

        // 解析物品类型
        Material material;
        try {
            material = Material.valueOf(parts[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("无效物品类型: " + parts[0]);
            return null;
        }

        // 解析数量
        int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
        ItemStack item = new ItemStack(material, amount);

        // 解析附魔
        if (parts.length > 2) {
            String[] enchants = parts[2].split(",");
            for (String ench : enchants) {
                String[] enchParts = ench.split("=");
                if (enchParts.length == 2) {
                    try {
                        Enchantment enchantment = Enchantment.getByKey(
                                NamespacedKey.minecraft(enchParts[0].toLowerCase())
                        );
                        int level = Integer.parseInt(enchParts[1]);
                        if (enchantment != null) {
                            item.addUnsafeEnchantment(enchantment, level);
                        }
                    } catch (Exception ex) {
                        plugin.getLogger().warning("无效附魔配置: " + ench + "（物品: " + configStr + "）");
                    }
                }
            }
        }

        return item;
    }
}