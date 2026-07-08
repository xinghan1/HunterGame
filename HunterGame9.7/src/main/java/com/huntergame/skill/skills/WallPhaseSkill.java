package com.huntergame.skill.skills;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public final class WallPhaseSkill implements HunterSkill {
   private static final String NAME = "\u7a7f\u5899";
   private final Map<UUID, Integer> soulTasks = new HashMap();
   private final Map<UUID, Integer> menuRefreshTasks = new HashMap();
   private final Set<UUID> soulMode = new HashSet();
   private final Map<UUID, InventorySnapshot> inventorySnapshots = new HashMap();

   public String getName() {
      return "\u7a7f\u5899";
   }

   public SkillActivationResult activate(Player player, SkillContext context) {
      int duration = context.duration("\u7a7f\u5899");
      UUID uuid = player.getUniqueId();
      this.inventorySnapshots.putIfAbsent(uuid, WallPhaseSkill.InventorySnapshot.capture(player));
      player.setGameMode(GameMode.SPECTATOR);
      this.soulMode.add(uuid);
      player.sendTitle(context.message("skill_wall_phase_title", "&e\u7a7f\u5899"), context.message("skill_wall_phase_subtitle", "&7%seconds%\u79d2\u540e\u56de\u5f52", "%seconds%", duration), 10, 40, 10);
      this.cancelTask(uuid);
      int taskId = Bukkit.getScheduler().runTaskLater(context.plugin(), () -> this.finishWallPhase(player, context), (long)duration * 20L).getTaskId();
      this.soulTasks.put(uuid, taskId);
      this.startMenuRefreshTask(player, context);
      return SkillActivationResult.successWithDuration(duration);
   }

   public void onPlayerQuit(PlayerQuitEvent event, SkillContext context) {
      this.cleanup(event.getPlayer());
   }

   public void onPlayerDeath(PlayerDeathEvent event, SkillContext context) {
      this.cleanup(event.getEntity());
   }

   private void cleanup(Player player) {
      UUID uuid = player.getUniqueId();
      this.cancelTask(uuid);
      this.cancelMenuRefreshTask(uuid);
      this.soulMode.remove(uuid);
      if (player.getGameMode() == GameMode.SPECTATOR) {
         player.setGameMode(GameMode.SURVIVAL);
      }

      this.restoreInventory(player);
   }

   private void cancelTask(UUID uuid) {
      Integer taskId = (Integer)this.soulTasks.remove(uuid);
      if (taskId != null) {
         Bukkit.getScheduler().cancelTask(taskId);
      }

   }

   private void startMenuRefreshTask(Player player, SkillContext context) {
      UUID uuid = player.getUniqueId();
      this.cancelMenuRefreshTask(uuid);
      int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(context.plugin(), () -> {
         if (player.isOnline() && this.soulMode.contains(uuid) && player.getGameMode() == GameMode.SPECTATOR) {
            player.updateInventory();
         } else {
            this.cancelMenuRefreshTask(uuid);
         }
      }, 40L, 40L);
      this.menuRefreshTasks.put(uuid, taskId);
   }

   private void cancelMenuRefreshTask(UUID uuid) {
      Integer taskId = (Integer)this.menuRefreshTasks.remove(uuid);
      if (taskId != null) {
         Bukkit.getScheduler().cancelTask(taskId);
      }

   }

   private void finishWallPhase(Player player, SkillContext context) {
      UUID uuid = player.getUniqueId();
      this.soulTasks.remove(uuid);
      this.cancelMenuRefreshTask(uuid);
      this.soulMode.remove(uuid);
      if (player.isOnline() && player.getGameMode() == GameMode.SPECTATOR) {
         player.setGameMode(GameMode.SURVIVAL);
         player.sendTitle(context.message("skill_wall_phase_return_title", "&a\u56de\u5f52\u73b0\u4e16"), context.message("skill_wall_phase_return_subtitle", ""), 10, 20, 10);
      }

      Bukkit.getScheduler().runTaskLater(context.plugin(), () -> this.restoreInventory(player), 1L);
   }

   private void restoreInventory(Player player) {
      InventorySnapshot snapshot = (InventorySnapshot)this.inventorySnapshots.remove(player.getUniqueId());
      if (snapshot != null) {
         snapshot.restore(player);
      }
   }

   private static class InventorySnapshot {
      private final ItemStack[] storageContents;
      private final ItemStack[] armorContents;
      private final ItemStack offHand;
      private final int heldSlot;

      private InventorySnapshot(ItemStack[] storageContents, ItemStack[] armorContents, ItemStack offHand, int heldSlot) {
         this.storageContents = storageContents;
         this.armorContents = armorContents;
         this.offHand = offHand;
         this.heldSlot = heldSlot;
      }

      private static InventorySnapshot capture(Player player) {
         return new InventorySnapshot(cloneContents(player.getInventory().getStorageContents()), cloneContents(player.getInventory().getArmorContents()), cloneItem(player.getInventory().getItemInOffHand()), player.getInventory().getHeldItemSlot());
      }

      private void restore(Player player) {
         player.getInventory().setStorageContents(cloneContents(this.storageContents));
         player.getInventory().setArmorContents(cloneContents(this.armorContents));
         player.getInventory().setItemInOffHand(cloneItem(this.offHand));
         player.getInventory().setHeldItemSlot(this.heldSlot);
         player.updateInventory();
      }

      private static ItemStack[] cloneContents(ItemStack[] contents) {
         ItemStack[] copy = new ItemStack[contents.length];

         for(int i = 0; i < contents.length; ++i) {
            copy[i] = cloneItem(contents[i]);
         }

         return copy;
      }

      private static ItemStack cloneItem(ItemStack item) {
         return item == null ? null : item.clone();
      }
   }
}
