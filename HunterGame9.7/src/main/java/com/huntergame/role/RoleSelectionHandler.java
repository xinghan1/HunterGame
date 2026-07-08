package com.huntergame.role;

import com.huntergame.HunterGame;
import com.huntergame.bedrock.BedrockRoleSelectionGUI;
import com.huntergame.gui.RoleSelectionGUI;
import com.huntergame.session.DisconnectProtectionService;
import com.huntergame.util.BedrockSupport;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
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

public class RoleSelectionHandler implements Listener {
   public static final NamespacedKey IS_HUNTER = new NamespacedKey("huntergame", "is_hunter");
   public static final NamespacedKey IS_ESCAPER = new NamespacedKey("huntergame", "is_escaper");
   private final HunterGame plugin;
   private final DisconnectProtectionService disconnectProtection;
   public int GAME_TIME_LIMIT_MINUTES;
   public double ESCAPER_RATIO_THRESHOLD;

   public RoleSelectionHandler(HunterGame plugin, DisconnectProtectionService disconnectProtection) {
      this.plugin = plugin;
      this.disconnectProtection = disconnectProtection;
      FileConfiguration config = plugin.getConfig();
      this.GAME_TIME_LIMIT_MINUTES = config.getInt("game.game_time_limit_minutes", 30);
      this.ESCAPER_RATIO_THRESHOLD = config.getDouble("game.escaper_ratio_threshold", (double)3.0F);
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      if (this.plugin.isDeathescapers(playerId)) {
         player.setGameMode(GameMode.SPECTATOR);
         player.sendMessage(this.plugin.getMessage("game_death_escapers", "&7\u4f60\u5df2\u6b7b\u4ea1\uff0c\u73b0\u5728\u53d8\u6210\u65c1\u89c2\u8005"));
      } else if (this.plugin.getStartGameCommand().isPlayerRespawning(playerId)) {
         player.setGameMode(GameMode.SPECTATOR);
      } else if (this.disconnectProtection.hasOfflineProtectionData(playerId)) {
         this.disconnectProtection.clearOfflineProtectionData(playerId);
         player.setGameMode(GameMode.SURVIVAL);
      } else {
         player.setGameMode(GameMode.SPECTATOR);
         if (this.plugin.isFinalBattleMode()) {
            World targetWorld = Bukkit.getWorld("world_the_end");
            if (targetWorld == null) {
               player.sendMessage(this.plugin.getMessage("target_world_not_found", "&c\u76ee\u6807\u4e16\u754c %world% \u4e0d\u5b58\u5728\uff01").replace("%world%", "world_the_end"));
            } else {
               Location targetLocation = new Location(targetWorld, (double)0.0F, (double)60.0F, (double)0.0F);
               player.teleport(targetLocation);
               player.sendMessage(this.plugin.getMessage("game_final", "&7\u5f53\u524d\u5904\u4e8e\u7ec8\u7ae0\u4e4b\u6218\uff0c\u65e0\u6cd5\u4e2d\u9014\u52a0\u5165"));
            }
         } else {
            if (this.plugin.isGameRunning()) {
               World targetWorld = Bukkit.getWorld("world");
               if (targetWorld == null) {
                  player.sendMessage(this.plugin.getMessage("target_world_not_found", "&c\u76ee\u6807\u4e16\u754c %world% \u4e0d\u5b58\u5728\uff01").replace("%world%", "world"));
               } else {
                  player.teleport(targetWorld.getHighestBlockAt(0, 0).getLocation().add((double)0.5F, (double)1.0F, (double)0.5F));
               }

               long gameDurationMinutes = this.plugin.getElapsedMinutes();
               if (gameDurationMinutes >= (long)this.GAME_TIME_LIMIT_MINUTES) {
                  player.setGameMode(GameMode.SPECTATOR);
                  player.sendMessage(this.plugin.getMessage("game_too_time", "&7\u6e38\u620f\u5df2\u8fdb\u884c %time% \u5206\u949f\uff0c\u65e0\u6cd5\u4e2d\u9014\u52a0\u5165").replace("%time%", String.valueOf(this.GAME_TIME_LIMIT_MINUTES)));
                  return;
               }

               this.plugin.addPlayerWithoutRole(player);
               Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.openRoleSelectionGUI(player), 40L);
            }

         }
      }
   }

   public void openRoleSelectionGUI(Player player) {
      if (BedrockSupport.isBedrockPlayer(this.plugin, player)) {
         try {
            BedrockRoleSelectionGUI.openBedrockRoleSelection(this.plugin, this, player);
         } catch (Throwable var3) {
            RoleSelectionGUI.openRoleSelectionGUI(this.plugin, this, player);
         }
      } else {
         RoleSelectionGUI.openRoleSelectionGUI(this.plugin, this, player);
      }

   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      Player player = (Player)event.getWhoClicked();
      UUID playerId = player.getUniqueId();
      Inventory clickedInventory = event.getClickedInventory();
      if (this.plugin.isPlayerWithoutRole(player)) {
         if (clickedInventory != null && !clickedInventory.equals(player.getInventory())) {
            event.setCancelled(true);
            int hunterSlot = this.plugin.getGuiConfig().getInt("join_midway_role-gui.hunter.slot", 3);
            int escaperSlot = this.plugin.getGuiConfig().getInt("join_midway_role-gui.escaper.slot", 4);
            int spectatorSlot = this.plugin.getGuiConfig().getInt("join_midway_role-gui.spectator.slot", 5);
            double escaperRatioThreshold = this.plugin.getConfig().getDouble("game.escaper_ratio_threshold", this.ESCAPER_RATIO_THRESHOLD);
            int hunterCount = this.plugin.getHunters().size();
            int escaperCount = this.plugin.getEscapers().size();
            double ratio = escaperCount > 0 ? (double)hunterCount / (double)escaperCount : (double)hunterCount;
            boolean showEscaperOption = ratio >= escaperRatioThreshold;
            if (event.getSlot() == hunterSlot) {
               this.selectHunterRole(player, playerId);
               player.getInventory().clear();
            } else if (showEscaperOption && event.getSlot() == escaperSlot) {
               this.selectEscaperRole(player, playerId);
               player.getInventory().clear();
            } else if (event.getSlot() == spectatorSlot) {
               this.selectSpectatorRole(player);
            }

         } else {
            event.setCancelled(true);
         }
      }
   }

   public void selectSpectatorRole(Player player) {
      player.setGameMode(GameMode.SPECTATOR);
      this.plugin.addRealSpectator(player.getUniqueId());
      player.sendMessage(this.plugin.getMessage("choose_spectator", "&7\u4f60\u5df2\u9009\u62e9\u6210\u4e3a\u65c1\u89c2\u8005\uff01"));
      this.plugin.removePlayerWithoutRole(player);
      player.closeInventory();
      this.plugin.teleportSpectatorToRandomPlayer(player);
   }

   public void selectHunterRole(Player player, UUID playerId) {
      player.getInventory().clear();
      player.setGameMode(GameMode.SURVIVAL);
      this.plugin.addHunter(playerId);
      this.plugin.removePlayerWithoutRole(player);
      player.getPersistentDataContainer().set(IS_HUNTER, PersistentDataType.BOOLEAN, true);
      player.getPersistentDataContainer().set(IS_ESCAPER, PersistentDataType.BOOLEAN, false);
      ItemStack compass = new ItemStack(Material.COMPASS);
      ItemMeta meta = compass.getItemMeta();
      meta.setDisplayName(this.plugin.getMessage("tracking_compass_display_name", "&e\u8ffd\u8e2a\u6307\u5357\u9488(\u53f3\u952e\u6253\u5f00)"));
      compass.setItemMeta(meta);
      player.getInventory().setItem(0, compass);
      player.closeInventory();
      Bukkit.broadcastMessage(this.plugin.getMessage("remaining_players", "&e\u5269\u4f59\u73a9\u5bb6: &c%hunters% \u730e\u4eba &e| &b%escapers% \u9003\u751f\u8005").replace("%hunters%", String.valueOf(this.plugin.getHunters().size())).replace("%escapers%", String.valueOf(this.plugin.getEscapers().size())));
      this.teleportNewHunterNearRandomHunter(player);
   }

   public void selectEscaperRole(Player player, UUID playerId) {
      player.getInventory().clear();
      this.plugin.addEscaper(playerId);
      this.plugin.removePlayerWithoutRole(player);
      this.plugin.getStartGameCommand().giveEscaperMark(player);
      player.getInventory().clear();
      player.setSaturation(20.0F);
      this.plugin.getHunterTracker().assignCompassAndTracking(player, true);
      this.plugin.getEscaperQuitCountdown().cancel();
      player.getPersistentDataContainer().set(IS_HUNTER, PersistentDataType.BOOLEAN, false);
      player.getPersistentDataContainer().set(IS_ESCAPER, PersistentDataType.BOOLEAN, true);
      player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 0, false, false));
      player.closeInventory();
      Bukkit.broadcastMessage(this.plugin.getMessage("remaining_players", "&e\u5269\u4f59\u73a9\u5bb6: &c%hunters% \u730e\u4eba &e| &b%escapers% \u9003\u751f\u8005").replace("%hunters%", String.valueOf(this.plugin.getHunters().size())).replace("%escapers%", String.valueOf(this.plugin.getEscapers().size())));
      this.teleportEscaperToRandomLocation(player);
   }

   private void teleportPlayer(Player player, double x, double z) {
      World world = Bukkit.getWorld("world");
      if (world != null) {
         int highestY = world.getHighestBlockYAt((int)x, (int)z);
         Location location = new Location(world, x, (double)(highestY + 1), z);
         player.teleport(location);
      } else {
         player.sendMessage(this.plugin.getMessage("transmit_no_world", "&c\u65e0\u6cd5\u627e\u5230\u9ed8\u8ba4\u4e16\u754c\uff0c\u4f20\u9001\u5931\u8d25\uff01"));
      }

   }

   @EventHandler
   public void onInventoryClose(InventoryCloseEvent event) {
      Player player = (Player)event.getPlayer();
      if (this.plugin.isPlayerWithoutRole(player)) {
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.openRoleSelectionGUI(player), 1L);
         player.sendMessage(this.plugin.getMessage("select_character", "&a\u4f60\u5fc5\u987b\u9009\u62e9\u4e00\u4e2a\u89d2\u8272\u624d\u80fd\u7ee7\u7eed\u6e38\u620f\uff01"));
      }

   }

   private void teleportNewHunterNearRandomHunter(Player newHunter) {
      List<Player> currentHunters = this.plugin.getHunters();
      currentHunters.remove(newHunter);
      if (currentHunters.isEmpty()) {
         this.teleportPlayer(newHunter, (double)0.0F, (double)0.0F);
      } else {
         int index = (int)(Math.random() * (double)currentHunters.size());
         Player targetHunter = (Player)currentHunters.get(index);
         Location targetLoc = targetHunter.getLocation();
         newHunter.teleport(targetLoc);
         newHunter.sendMessage(this.plugin.getMessage("join_game_teleport", "&e\u4f60\u5df2\u4f20\u9001\u5230 " + targetHunter.getName() + " \u7684\u4f4d\u7f6e\uff01"));
      }

   }

   private void teleportEscaperToRandomLocation(Player newEscaper) {
      World world = newEscaper.getWorld();
      int maxAttempts = 50;

      for(int attempt = 0; attempt < maxAttempts; ++attempt) {
         int x = -1000 + (int)(Math.random() * (double)2001.0F);
         int z = -1000 + (int)(Math.random() * (double)2001.0F);
         int y = this.getSafeYCoordinate(world, x, z);
         if (y > 0) {
            Location safeLocation = new Location(world, (double)x, (double)y, (double)z);
            if (this.isSafeLocation(safeLocation)) {
               newEscaper.teleport(safeLocation);
               return;
            }
         }
      }

      this.teleportPlayer(newEscaper, (double)0.0F, (double)0.0F);
      newEscaper.sendMessage(this.plugin.getMessage("safe_location_not_found", "\u672a\u80fd\u627e\u5230\u5b89\u5168\u4f4d\u7f6e\uff0c\u5df2\u4f20\u9001\u5230\u9ed8\u8ba4\u70b9\uff01"));
      newEscaper.setGameMode(GameMode.SURVIVAL);
   }

   private int getSafeYCoordinate(World world, int x, int z) {
      for(int y = 255; y > 0; --y) {
         Block block = world.getBlockAt(x, y, z);
         Block headBlock = world.getBlockAt(x, y + 1, z);
         if (block.getType().isSolid() && !headBlock.getType().isSolid() && !this.isLiquid(headBlock)) {
            return y + 1;
         }
      }

      return -1;
   }

   private boolean isSafeLocation(Location location) {
      if (location == null) {
         return false;
      } else {
         Block feetBlock = location.getBlock();
         Block headBlock = location.clone().add((double)0.0F, (double)1.0F, (double)0.0F).getBlock();
         Block belowBlock = location.clone().add((double)0.0F, (double)-1.0F, (double)0.0F).getBlock();
         return belowBlock.getType().isSolid() && !feetBlock.getType().isSolid() && !headBlock.getType().isSolid() && !this.isLiquid(feetBlock) && !this.isLiquid(headBlock);
      }
   }

   private boolean isLiquid(Block block) {
      Material material = block.getType();
      return material == Material.WATER || material == Material.LAVA;
   }
}
