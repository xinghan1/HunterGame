package com.huntergame.gui;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;

public class SpectatorGUI implements Listener {
   private final HunterGame plugin;

   public SpectatorGUI(HunterGame plugin) {
      this.plugin = plugin;
   }

   public void updateSpectatorInventory(Player spectator) {
      if (spectator != null && spectator.isOnline() && spectator.getGameMode() == GameMode.SPECTATOR) {
         if (this.plugin.isRealSpectator(spectator.getUniqueId())) {
            PlayerInventory inv = spectator.getInventory();

            for(int i = 9; i <= 35; ++i) {
               inv.setItem(i, (ItemStack)null);
            }

            int slot = 9;

            for(Player p : Bukkit.getOnlinePlayers()) {
               if (p != spectator && p.getGameMode() != GameMode.SPECTATOR) {
                  if (slot > 35) {
                     break;
                  }

                  ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                  SkullMeta meta = (SkullMeta)head.getItemMeta();
                  if (meta != null) {
                     meta.setOwningPlayer(p);
                     meta.setDisplayName(this.plugin.getMessage("spectator_head_display_name", "&e\u70b9\u51fb\u89c2\u770b\uff1a&a%player%").replace("%player%", p.getName()));
                     head.setItemMeta(meta);
                  }

                  inv.setItem(slot, head);
                  ++slot;
               }
            }

            spectator.updateInventory();
         }
      }
   }

   public void updateAllSpectatorInventories() {
      for(Player player : Bukkit.getOnlinePlayers()) {
         this.updateSpectatorInventory(player);
      }

   }

   public void clearSpectatorSlots(Player player) {
      PlayerInventory inv = player.getInventory();

      for(int i = 9; i <= 35; ++i) {
         inv.setItem(i, (ItemStack)null);
      }

      player.updateInventory();
   }

   public void clearAllSpectatorSlots() {
      for(Player player : Bukkit.getOnlinePlayers()) {
         if (player.getGameMode() == GameMode.SPECTATOR) {
            this.clearSpectatorSlots(player);
         }
      }

   }

   @EventHandler
   public void onGamemodeChange(PlayerGameModeChangeEvent event) {
      Player player = event.getPlayer();
      GameMode newMode = event.getNewGameMode();
      boolean wasRealSpectator = this.plugin.isRealSpectator(player.getUniqueId());
      Bukkit.getScheduler().runTaskLater(this.plugin, this::updateAllSpectatorInventories, 1L);
      if (newMode == GameMode.SPECTATOR) {
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.updateSpectatorInventory(player), 1L);
      } else if (player.getGameMode() == GameMode.SPECTATOR && newMode != GameMode.SPECTATOR && wasRealSpectator) {
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.clearSpectatorSlots(player), 1L);
      }

   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      Bukkit.getScheduler().runTaskLater(this.plugin, this::updateAllSpectatorInventories, 1L);
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player) {
         Player spectator = (Player)event.getWhoClicked();
         if (spectator.getGameMode() == GameMode.SPECTATOR) {
            if (this.plugin.isRealSpectator(spectator.getUniqueId())) {
               int slot = event.getSlot();
               if (slot >= 9 && slot <= 35) {
                  ItemStack item = event.getCurrentItem();
                  if (item != null && item.getType() == Material.PLAYER_HEAD) {
                     event.setCancelled(true);
                     SkullMeta meta = (SkullMeta)item.getItemMeta();
                     if (meta != null && meta.getOwningPlayer() != null) {
                        Player target = meta.getOwningPlayer().getPlayer();
                        if (target != null && target.isOnline()) {
                           spectator.teleport(target.getLocation());
                           spectator.sendMessage(this.plugin.getMessage("spectator_teleported_to_player", "&a\u5df2\u4f20\u9001\u5230 &e%player% &a\u9644\u8fd1").replace("%player%", target.getName()));
                        } else {
                           spectator.sendMessage(this.plugin.getMessage("spectator_target_offline", "&c\u73a9\u5bb6\u5df2\u79bb\u7ebf\uff01"));
                        }
                     }
                  }
               }
            }
         }
      }
   }
}
