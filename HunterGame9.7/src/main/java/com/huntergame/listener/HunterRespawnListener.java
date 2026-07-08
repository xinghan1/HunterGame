package com.huntergame.listener;

import com.huntergame.HunterGame;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
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

public class HunterRespawnListener implements Listener {
   private final HunterGame plugin;

   public HunterRespawnListener(HunterGame plugin) {
      this.plugin = plugin;
   }

   @EventHandler
   public void onPlayerRespawn(PlayerRespawnEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      FileConfiguration config = this.plugin.getConfig();
      if (this.plugin.isGameRunning() && this.plugin.isFinalBattleMode() && (this.plugin.isRealSpectator(playerId) || this.plugin.isDeathescapers(playerId))) {
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (player.isOnline()) {
               player.setAllowFlight(false);
               player.setFlying(false);
               player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            }
         }, 1L);
         return;
      }

      if (!this.plugin.isGameRunning()) {
         Location lobbyLocation = this.plugin.getLobbyLocation();
         if (lobbyLocation != null) {
            Bukkit.getScheduler().runTask(this.plugin, () -> player.teleport(lobbyLocation));
         }

      } else if (this.plugin.isHunter(playerId)) {
         if (!this.plugin.isFinalBattleMode() || !this.plugin.isRealSpectator(playerId)) {
            if (!this.plugin.getStartGameCommand().isPlayerRespawning(playerId)) {
               Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                  if (this.plugin.isFinalBattleMode()) {
                     this.plugin.getFinalBattleProfessionManager().giveSelectedProfessionLoadout(player);
                  } else {
                     ItemStack compass = new ItemStack(Material.COMPASS);
                     ItemMeta meta = compass.getItemMeta();
                     if (meta != null) {
                        meta.setDisplayName(this.plugin.getMessage("tracking_compass_display_name", "&e\u8ffd\u8e2a\u6307\u5357\u9488(\u53f3\u952e\u6253\u5f00)"));
                        compass.setItemMeta(meta);
                     }

                     player.getInventory().setItem(0, compass);
                     this.plugin.giveSharedBackpack(player, true);
                     if (config.getBoolean("hunter_resupply.enable", false)) {
                        this.giveResupplyItems(player);
                     }
                  }
               }, 40L);
            }
         }
      }
   }

   private void giveResupplyItems(Player player) {
      FileConfiguration config = this.plugin.getConfig();
      long gameMinutes = this.plugin.getElapsedMinutes();
      List<Map<?, ?>> timeStages = config.getMapList("hunter_resupply.time_stages");
      if (timeStages.isEmpty()) {
         this.plugin.getLogger().warning("hunter_resupply.time_stages \u914d\u7f6e\u4e3a\u7a7a\uff0c\u65e0\u6cd5\u53d1\u653e\u88c5\u5907\uff01");
      } else {
         Map<?, ?> currentStage = null;

         for(Map<?, ?> stage : timeStages) {
            int minTime = stage.containsKey("min_minutes") ? ((Number)stage.get("min_minutes")).intValue() : 0;
            if (gameMinutes >= (long)minTime) {
               currentStage = stage;
            }
         }

         if (currentStage != null) {
            List<Map<?, ?>> items = (List)currentStage.get("items");
            if (items != null && !items.isEmpty()) {
               PlayerInventory inv = player.getInventory();

               for(Map<?, ?> itemMap : items) {
                  String matName = itemMap.containsKey("material") ? (String)itemMap.get("material") : "STONE";
                  int amount = itemMap.containsKey("amount") ? ((Number)itemMap.get("amount")).intValue() : 1;
                  Material material = Material.matchMaterial(matName);
                  if (material == null) {
                     this.plugin.getLogger().warning("\u65e0\u6548\u7269\u54c1\u6750\u8d28\uff1a" + matName + "\uff0c\u8df3\u8fc7");
                  } else {
                     ItemStack item = new ItemStack(material, amount);
                     ItemMeta meta = item.getItemMeta();
                     if (meta != null) {
                        if (itemMap.containsKey("enchantments")) {
                           for(Map<?, ?> enchantMap : (List<Map<?, ?>>)itemMap.get("enchantments")) {
                              String enchantType = enchantMap.containsKey("type") ? (String)enchantMap.get("type") : "";
                              int enchantLevel = enchantMap.containsKey("level") ? ((Number)enchantMap.get("level")).intValue() : 1;
                              Enchantment enchant = Enchantment.getByName(enchantType.toUpperCase());
                              if (enchant == null) {
                                 this.plugin.getLogger().warning("\u65e0\u6548\u9644\u9b54\uff1a" + enchantType + "\uff0c\u8df3\u8fc7");
                              } else {
                                 int maxLevel = enchant.getMaxLevel();
                                 if (enchantLevel < 1 || enchantLevel > maxLevel) {
                                    enchantLevel = maxLevel;
                                 }

                                 meta.addEnchant(enchant, enchantLevel, false);
                              }
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
                              inv.addItem(new ItemStack[]{item});
                              break;
                           default:
                              try {
                                 int slot = Integer.parseInt(slotStr);
                                 if (slot >= 0 && slot < 36) {
                                    inv.setItem(slot, item);
                                 } else {
                                    inv.addItem(new ItemStack[]{item});
                                 }
                              } catch (NumberFormatException var23) {
                                 inv.addItem(new ItemStack[]{item});
                              }
                        }
                     }
                  }
               }

            }
         }
      }
   }
}
