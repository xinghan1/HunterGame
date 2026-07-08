package com.huntergame.tracking;

import com.huntergame.HunterGame;
import com.huntergame.bedrock.BedrockTrackingGUI;
import com.huntergame.gui.TrackingGUI;
import com.huntergame.util.BedrockSupport;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
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

public class HunterTracker implements Listener {
   private final HunterGame plugin;
   private final Map<UUID, Long> lastClickTime = new ConcurrentHashMap();
   private final Map<UUID, Long> cooldowns = new ConcurrentHashMap();
   private final Map<UUID, Boolean> trackingTeammateStatus = new ConcurrentHashMap();
   public boolean COMPASS;
   public long COOLDOWN_TIME;
   public int DEDUCT_HEALTH;
   public int DETECTION_DISTANCE;
   public boolean ESCAPER_TRACKING_ENABLE;

   public HunterTracker(HunterGame plugin) {
      this.plugin = plugin;
      this.startGlobalHunterTrackingTask();
      FileConfiguration config = plugin.getConfig();
      this.COMPASS = config.getBoolean("game.compass.enable", true);
      this.COOLDOWN_TIME = config.getLong("game.compass.hunter_tp_cooldown", 300L);
      this.DEDUCT_HEALTH = config.getInt("game.compass.deduct_health", 18);
      this.DETECTION_DISTANCE = config.getInt("game.compass.detection_distance", 50);
      this.ESCAPER_TRACKING_ENABLE = config.getBoolean("game.compass.escaper_tracking_enable", true);
   }

   @EventHandler
   public void onCompassUse(PlayerInteractEvent event) {
      if (this.COMPASS) {
         Player player = event.getPlayer();
         UUID playerId = player.getUniqueId();
         if (event.getItem() != null && event.getItem().getType() == Material.COMPASS) {
            long currentTime = System.currentTimeMillis();
            if (this.lastClickTime.containsKey(playerId) && currentTime - (Long)this.lastClickTime.get(playerId) < 200L) {
               event.setCancelled(true);
            } else {
               this.lastClickTime.put(playerId, currentTime);
               boolean isHunter = this.plugin.isHunter(playerId);
               boolean isEscaper = this.plugin.isEscaper(playerId);
               if (isHunter) {
                  if (event.getAction().toString().contains("RIGHT_CLICK")) {
                     this.openTrackingGUI(player);
                  }

                  event.setCancelled(true);
               } else if (isEscaper || !this.plugin.isGameRunning()) {
                  event.setCancelled(true);
               }

            }
         }
      }
   }

   public void openTrackingGUI(Player player) {
      if (BedrockSupport.isBedrockPlayer(this.plugin, player)) {
         try {
            BedrockTrackingGUI.openBedrockTrackingMenu(this.plugin, this, player);
         } catch (Throwable var3) {
            TrackingGUI.openTrackingGUI(this.plugin, player);
         }
      } else {
         TrackingGUI.openTrackingGUI(this.plugin, player);
      }

   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      String title = event.getView().getTitle();
      String trackingTitle = this.plugin.getMessage("tracking_gui_title", "\u9009\u62e9\u64cd\u4f5c");
      String teammateTitle = this.plugin.getMessage("tracking_teammate_gui_title", "\u9009\u62e9\u961f\u53cb");
      if (title.equals(trackingTitle) || title.equals(teammateTitle)) {
         event.setCancelled(true);
         Player player = (Player)event.getWhoClicked();
         ItemStack clickedItem = event.getCurrentItem();
         if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
         }

         if (title.equals(trackingTitle)) {
            if (clickedItem.getType() == Material.ENDER_PEARL) {
               if (this.isEscaperNearby(player, (double)this.DETECTION_DISTANCE)) {
                  player.sendMessage(this.plugin.getMessage("nearby_escape", "&c\u9644\u8fd1\u6709\u9003\u751f\u8005\uff0c\u65e0\u6cd5\u6253\u5f00\u961f\u53cb\u5217\u8868\uff01"));
                  player.closeInventory();
                  return;
               }

               TrackingGUI.openTeammateListGUI(this.plugin, this, player);
            } else if (clickedItem.getType() == Material.COMPASS) {
               this.switchToHunterTrackingTarget(player);
            }
         } else if (title.equals(teammateTitle) && clickedItem.getType() == Material.PLAYER_HEAD) {
            String teammateName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
            if (this.isOnCooldown(player)) {
               long timeLeft = this.getCooldownTimeLeft(player);
               player.sendMessage(this.plugin.getMessage("waiting_countdown_teleport", "&c\u4f60\u9700\u8981\u7b49\u5f85 " + timeLeft + " \u79d2\u624d\u80fd\u518d\u6b21\u4f20\u9001!"));
               player.closeInventory();
               return;
            }

            Player teammate = Bukkit.getPlayer(teammateName);
            if (teammate != null && this.plugin.isHunter(teammate.getUniqueId()) && teammate.isOnline()) {
               this.performTeleport(player, teammate);
               player.closeInventory();
            } else {
               player.sendMessage(this.plugin.getMessage("Teammate_unavailable", "&c\u8be5\u961f\u53cb\u5f53\u524d\u4e0d\u53ef\u7528\uff01"));
               player.closeInventory();
            }
         }
      }

   }

   public boolean isEscaperNearby(Player player, double radius) {
      if (!this.plugin.isGameRunning()) {
         return false;
      } else {
         double radiusSquared = radius * radius;
         return player.getWorld().getPlayers().stream().filter((target) -> !target.equals(player)).filter((target) -> this.plugin.isEscaper(target.getUniqueId())).anyMatch((target) -> target.getLocation().distanceSquared(player.getLocation()) <= radiusSquared);
      }
   }

   public void performTeleport(Player player, Player target) {
      player.teleport(target.getLocation());
      player.sendMessage(this.plugin.getMessage("transferring_teammates", "&a\u4f60\u5df2\u4f20\u9001\u5230\u961f\u53cb " + target.getName() + " \u7684\u4f4d\u7f6e!"));
      double newHealth = player.getHealth() - (double)this.DEDUCT_HEALTH;
      player.setHealth(Math.max((double)1.0F, newHealth));
      this.startCooldown(player);
   }

   private void startCooldown(Player player) {
      this.cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
   }

   public boolean isOnCooldown(Player player) {
      if (!this.cooldowns.containsKey(player.getUniqueId())) {
         return false;
      } else {
         long cooldownTime = (Long)this.cooldowns.get(player.getUniqueId());
         return System.currentTimeMillis() - cooldownTime < this.COOLDOWN_TIME * 1000L;
      }
   }

   public long getCooldownTimeLeft(Player player) {
      long cooldownTime = (Long)this.cooldowns.getOrDefault(player.getUniqueId(), 0L);
      long timeLeftMillis = this.COOLDOWN_TIME * 1000L - (System.currentTimeMillis() - cooldownTime);
      return Math.max(0L, timeLeftMillis / 1000L);
   }

   private void startGlobalHunterTrackingTask() {
      (new BukkitRunnable() {
         public void run() {
            if (HunterTracker.this.plugin.isGameRunning()) {
               for(UUID hunterUUID : new ArrayList<>(HunterTracker.this.trackingTeammateStatus.keySet())) {
                  Player player = Bukkit.getPlayer(hunterUUID);
                  if (player != null && player.isOnline()) {
                     if (HunterTracker.this.plugin.isHunter(hunterUUID)) {
                        HunterTracker.this.updateHunterCompassAndActionBar(player);
                     } else if (HunterTracker.this.plugin.isEscaper(hunterUUID) && HunterTracker.this.ESCAPER_TRACKING_ENABLE) {
                        HunterTracker.this.updateEscaperCompassAndActionBar(player);
                     } else {
                        HunterTracker.this.stopTracking(hunterUUID);
                     }
                  } else {
                     HunterTracker.this.stopTracking(hunterUUID);
                  }
               }

            }
         }
      }).runTaskTimer(this.plugin, 0L, 20L);
   }

   private void updateHunterCompassAndActionBar(Player hunter) {
      UUID hunterUUID = hunter.getUniqueId();
      boolean trackingTeammate = (Boolean)this.trackingTeammateStatus.getOrDefault(hunterUUID, false);
      if (hunter.getGameMode() != GameMode.SURVIVAL) {
         hunter.sendActionBar(this.plugin.getMessage("tracking_inactive", "&6\u8ffd\u8e2a\uff08\u975e\u751f\u5b58\u6a21\u5f0f\uff09"));
      } else {
         Player target = trackingTeammate ? this.getNearestHunterTeammate(hunter) : this.getNearestEscaper(hunter);
         if (target != null) {
            this.setPlayerCompassTarget(hunter, target.getLocation());
         } else {
            this.setPlayerCompassTarget(hunter, hunter.getWorld().getSpawnLocation());
         }

         if (target != null) {
            double distance = Math.sqrt(hunter.getLocation().distanceSquared(target.getLocation()));
            String targetType = trackingTeammate ? "\u961f\u53cb" : "\u9003\u751f\u8005";
            hunter.sendActionBar(this.plugin.getMessage("tracking_target", "&e\u6b63\u5728\u8ddf\u8e2a: &c%target% &e(%type%) &c| \u8ddd\u79bb: &c%distance% \u7c73").replace("%target%", target.getName()).replace("%type%", targetType).replace("%distance%", String.format("%.1f", distance)));
         } else {
            hunter.sendActionBar(this.plugin.getMessage("no_target", "&6\u6ca1\u6709\u53ef\u8ffd\u8e2a\u7684\u76ee\u6807\uff01"));
         }

      }
   }

   public void switchToHunterTrackingTarget(Player player) {
      UUID playerId = player.getUniqueId();
      boolean currentTrackingTeammate = (Boolean)this.trackingTeammateStatus.getOrDefault(playerId, false);
      boolean newTrackingTeammate = !currentTrackingTeammate;
      this.trackingTeammateStatus.put(playerId, newTrackingTeammate);
      player.closeInventory();
      String key = newTrackingTeammate ? "tracking_switched_teammate" : "tracking_switched_escaper";
      String fallback = newTrackingTeammate ? "&e\u5df2\u5207\u6362\u8ffd\u8e2a\u76ee\u6807\uff1a&a\u961f\u53cb" : "&e\u5df2\u5207\u6362\u8ffd\u8e2a\u76ee\u6807\uff1a&c\u9003\u751f\u8005";
      player.sendMessage(this.plugin.getMessage(key, fallback));
      this.updateHunterCompassAndActionBar(player);
   }

   public void assignCompassAndTracking(Player player, boolean isEscaper) {
      this.stopTracking(player.getUniqueId());
      ItemStack compass = new ItemStack(Material.COMPASS);
      ItemMeta meta = compass.getItemMeta();
      meta.setDisplayName(this.getCompassName(isEscaper));
      compass.setItemMeta(meta);
      player.getInventory().addItem(new ItemStack[]{compass});
      this.trackingTeammateStatus.putIfAbsent(player.getUniqueId(), isEscaper);
      this.updateTrackingNow(player, isEscaper);
   }

   @EventHandler
   public void onPlayerDropItem(PlayerDropItemEvent event) {
      ItemStack droppedItem = event.getItemDrop().getItemStack();
      if (this.isTrackingCompass(droppedItem)) {
         event.setCancelled(true);
      }

   }

   private boolean isTrackingCompass(ItemStack item) {
      if (item != null && item.getType() == Material.COMPASS && item.hasItemMeta()) {
         ItemMeta meta = item.getItemMeta();
         return meta.hasDisplayName() && (this.getCompassName(false).equals(meta.getDisplayName()) || this.getCompassName(true).equals(meta.getDisplayName()));
      } else {
         return false;
      }
   }

   private String getCompassName(boolean isEscaper) {
      return isEscaper ? this.plugin.getMessage("escaper_compass_name", "&e\u9003\u751f\u8005\u6307\u5357\u9488") : this.plugin.getMessage("hunter_compass_name", "&e\u730e\u4eba\u6307\u5357\u9488");
   }

   private void stopTracking(UUID playerId) {
      this.trackingTeammateStatus.remove(playerId);
      this.lastClickTime.remove(playerId);
      this.cooldowns.remove(playerId);
   }

   private void updateTrackingNow(Player player, boolean isEscaper) {
      if (isEscaper && this.ESCAPER_TRACKING_ENABLE) {
         this.updateEscaperCompassAndActionBar(player);
      } else if (!isEscaper) {
         this.updateHunterCompassAndActionBar(player);
      }

   }

   public void startTrackingEscaper(Player escaper) {
      if (this.ESCAPER_TRACKING_ENABLE) {
         this.trackingTeammateStatus.put(escaper.getUniqueId(), true);
         this.updateEscaperCompassAndActionBar(escaper);
      }
   }

   private void updateEscaperCompassAndActionBar(Player escaper) {
      if (escaper.getGameMode() != GameMode.SURVIVAL) {
         escaper.sendActionBar(this.plugin.getMessage("tracking_inactive", "&6\u8ffd\u8e2a\uff08\u975e\u751f\u5b58\u6a21\u5f0f\uff09"));
      } else {
         Player nearestTeammate = this.getNearestEscaperTeammate(escaper);
         Player nearestHunter = this.getNearestSurvivalHunter(escaper);
         Location compassTarget = nearestTeammate != null ? nearestTeammate.getLocation() : escaper.getWorld().getSpawnLocation();
         this.setPlayerCompassTarget(escaper, compassTarget);
         if (nearestTeammate != null) {
            Location escaperLocation = escaper.getLocation();
            double teammateDistance = Math.sqrt(escaperLocation.distanceSquared(nearestTeammate.getLocation()));
            if (nearestHunter != null && escaper.getWorld() == nearestHunter.getWorld()) {
               double hunterDistance = Math.sqrt(escaperLocation.distanceSquared(nearestHunter.getLocation()));
               if (hunterDistance <= (double)30.0F) {
                  escaper.sendActionBar(this.plugin.getMessage("tracking_danger", "&e\u6700\u8fd1\u7684\u961f\u53cb: &a%teammate% &e| \u8ddd\u79bb: &a%tDistance% \u7c73  &c\u5371\u9669\u5371\u9669\uff01").replace("%teammate%", nearestTeammate.getName()).replace("%tDistance%", String.format("%.1f", teammateDistance)));
               } else if (hunterDistance <= (double)150.0F) {
                  escaper.sendActionBar(this.plugin.getMessage("tracking_nearby_hunter", "&e\u6700\u8fd1\u7684\u961f\u53cb: &a%teammate% &e| \u8ddd\u79bb: &a%tDistance% \u7c73  &6\u9644\u8fd1\u6709\u730e\u4eba\uff01").replace("%teammate%", nearestTeammate.getName()).replace("%tDistance%", String.format("%.1f", teammateDistance)));
               } else {
                  escaper.sendActionBar(this.plugin.getMessage("tracking_safe", "&e\u6700\u8fd1\u7684\u961f\u53cb: &a%teammate% &e| \u8ddd\u79bb: &a%tDistance% \u7c73  &a\u4f60\u5f88\u5b89\u5168\uff01").replace("%teammate%", nearestTeammate.getName()).replace("%tDistance%", String.format("%.1f", teammateDistance)));
               }
            } else {
               escaper.sendActionBar(this.plugin.getMessage("tracking_safe", "&e\u6700\u8fd1\u7684\u961f\u53cb: &a%teammate% &e| \u8ddd\u79bb: &a%tDistance% \u7c73  &a\u4f60\u5f88\u5b89\u5168\uff01").replace("%teammate%", nearestTeammate.getName()).replace("%tDistance%", String.format("%.1f", teammateDistance)));
            }
         } else if (nearestHunter != null && escaper.getWorld() == nearestHunter.getWorld()) {
            double hunterDistance = Math.sqrt(escaper.getLocation().distanceSquared(nearestHunter.getLocation()));
            if (hunterDistance <= (double)30.0F) {
               escaper.sendActionBar(this.plugin.getMessage("tracking_danger_alone", "&e\u6ca1\u6709\u53ef\u8ffd\u8e2a\u7684\u961f\u53cb  &c\u5371\u9669\u5371\u9669\uff01").replace("%hDistance%", String.format("%.1f", hunterDistance)));
            } else if (hunterDistance <= (double)150.0F) {
               escaper.sendActionBar(this.plugin.getMessage("tracking_nearby_hunter_alone", "&e\u6ca1\u6709\u53ef\u8ffd\u8e2a\u7684\u961f\u53cb  &6\u9644\u8fd1\u6709\u730e\u4eba\uff01").replace("%hDistance%", String.format("%.1f", hunterDistance)));
            } else {
               escaper.sendActionBar(this.plugin.getMessage("tracking_safe_alone", "&e\u6ca1\u6709\u53ef\u8ffd\u8e2a\u7684\u961f\u53cb  &a\u4f60\u5f88\u5b89\u5168\uff01"));
            }
         } else {
            escaper.sendActionBar(this.plugin.getMessage("tracking_none_teammate", "&e\u6ca1\u6709\u53ef\u8ffd\u8e2a\u7684\u961f\u53cb  &a\u4f60\u5f88\u5b89\u5168\uff01"));
         }

      }
   }

   private void setPlayerCompassTarget(Player player, Location location) {
      if (player != null && player.isOnline()) {
         player.setCompassTarget(location);
      }

   }

   private Player getNearestEscaperTeammate(Player escaper) {
      if (this.plugin.isGameRunning() && escaper.getGameMode() == GameMode.SURVIVAL) {
         Location escaperLocation = escaper.getLocation();
         Player nearest = null;
         double nearestDistance = Double.MAX_VALUE;

         for(Player teammate : this.plugin.getEscapers()) {
            if (teammate != null && !teammate.equals(escaper) && teammate.isOnline() && teammate.getWorld().equals(escaper.getWorld()) && this.plugin.isEscaper(teammate.getUniqueId()) && teammate.getGameMode() == GameMode.SURVIVAL) {
               double distanceSquared = escaperLocation.distanceSquared(teammate.getLocation());
               if (distanceSquared < nearestDistance) {
                  nearestDistance = distanceSquared;
                  nearest = teammate;
               }
            }
         }

         return nearest;
      } else {
         return null;
      }
   }

   private Player getNearestSurvivalHunter(Player escaper) {
      if (this.plugin.isGameRunning() && escaper.getGameMode() == GameMode.SURVIVAL) {
         Player nearestHunter = null;
         double minDistance = Double.MAX_VALUE;

         for(Player hunter : this.plugin.getHunters()) {
            if (hunter != null && hunter.isOnline() && hunter.getGameMode() == GameMode.SURVIVAL && hunter.getWorld().equals(escaper.getWorld()) && this.plugin.isHunter(hunter.getUniqueId()) && !hunter.equals(escaper)) {
               double distanceSquared = escaper.getLocation().distanceSquared(hunter.getLocation());
               if (distanceSquared < minDistance) {
                  minDistance = distanceSquared;
                  nearestHunter = hunter;
               }
            }
         }

         return nearestHunter;
      } else {
         return null;
      }
   }

   private Player getNearestEscaper(Player hunter) {
      if (this.plugin.isGameRunning() && hunter.getGameMode() == GameMode.SURVIVAL) {
         Location hunterLocation = hunter.getLocation();
         Player nearest = null;
         double nearestDistance = Double.MAX_VALUE;

         for(Player escaper : this.plugin.getEscapers()) {
            if (escaper != null && escaper.isOnline() && escaper.getWorld().equals(hunter.getWorld()) && this.plugin.isEscaper(escaper.getUniqueId()) && escaper.getGameMode() == GameMode.SURVIVAL) {
               double distanceSquared = hunterLocation.distanceSquared(escaper.getLocation());
               if (distanceSquared < nearestDistance) {
                  nearestDistance = distanceSquared;
                  nearest = escaper;
               }
            }
         }

         return nearest;
      } else {
         return null;
      }
   }

   private Player getNearestHunterTeammate(Player hunter) {
      if (this.plugin.isGameRunning() && hunter.getGameMode() == GameMode.SURVIVAL) {
         Location hunterLocation = hunter.getLocation();
         Player nearest = null;
         double nearestDistance = Double.MAX_VALUE;

         for(Player teammate : this.plugin.getHunters()) {
            if (teammate != null && !teammate.equals(hunter) && teammate.isOnline() && teammate.getWorld().equals(hunter.getWorld()) && this.plugin.isHunter(teammate.getUniqueId()) && teammate.getGameMode() == GameMode.SURVIVAL) {
               double distanceSquared = hunterLocation.distanceSquared(teammate.getLocation());
               if (distanceSquared < nearestDistance) {
                  nearestDistance = distanceSquared;
                  nearest = teammate;
               }
            }
         }

         return nearest;
      } else {
         return null;
      }
   }

   public void cleanup() {
      this.trackingTeammateStatus.clear();
      this.lastClickTime.clear();
      this.cooldowns.clear();
      Bukkit.getOnlinePlayers().forEach((player) -> player.setCompassTarget(player.getWorld().getSpawnLocation()));
   }
}
