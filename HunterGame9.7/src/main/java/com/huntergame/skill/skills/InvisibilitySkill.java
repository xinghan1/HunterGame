package com.huntergame.skill.skills;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class InvisibilitySkill implements HunterSkill {
   private static final String NAME = "\u9690\u8eab";
   private final Map<UUID, ItemStack[]> armorStorage = new HashMap();
   private final Map<UUID, ItemStack> offHandStorage = new HashMap();
   private final Map<UUID, Integer> restoreTasks = new HashMap();

   public String getName() {
      return "\u9690\u8eab";
   }

   public SkillActivationResult activate(Player player, SkillContext context) {
      int duration = context.duration("\u9690\u8eab");
      UUID uuid = player.getUniqueId();
      player.sendTitle(context.message("skill_invisibility_title", "&b\ud83d\udc7b &l\u9690\u8eab &r\ud83d\udc7b"), context.message("skill_duration_subtitle", "&7\u6301\u7eed\u65f6\u95f4: &a%seconds%\u79d2", "%seconds%", duration), 10, 60, 10);
      this.armorStorage.putIfAbsent(uuid, this.cloneArmorContents(player.getInventory().getArmorContents()));
      this.offHandStorage.putIfAbsent(uuid, this.cloneItem(player.getInventory().getItemInOffHand()));
      player.getInventory().setArmorContents(new ItemStack[4]);
      player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
      if (context.plugin().getFreezeSkill().isPlayerFrozen(uuid)) {
         context.plugin().getFreezeSkill().unfreezeNow(uuid);
      }

      player.removePotionEffect(PotionEffectType.GLOWING);
      this.applyTemporaryPotionEffects(player, context, duration * 20);
      this.cancelRestoreTask(uuid);
      int taskId = Bukkit.getScheduler().runTaskLater(context.plugin(), () -> this.restorePlayerVisibility(player, context), (long)duration * 20L).getTaskId();
      this.restoreTasks.put(uuid, taskId);
      context.sendActivationMessage(player);
      return SkillActivationResult.successWithDuration(duration);
   }

   public void onPlayerQuit(PlayerQuitEvent event, SkillContext context) {
      this.restorePlayerVisibility(event.getPlayer(), context);
   }

   public void onPlayerDeath(PlayerDeathEvent event, SkillContext context) {
      Player player = event.getEntity();
      UUID uuid = player.getUniqueId();
      this.cancelRestoreTask(uuid);
      if (event.getKeepInventory()) {
         this.restoreStoredEquipment(player);
      } else {
         this.addStoredEquipmentToDrops(event);
         this.clearStoredEquipment(uuid);
      }

      this.removeTemporaryPotionEffects(player);
   }

   private void restorePlayerVisibility(Player player, SkillContext context) {
      UUID uuid = player.getUniqueId();
      this.cancelRestoreTask(uuid);
      this.restoreStoredEquipment(player);
      this.removeTemporaryPotionEffects(player);
      player.updateInventory();

      for(Player online : Bukkit.getOnlinePlayers()) {
         online.hidePlayer(context.plugin(), player);
         online.showPlayer(context.plugin(), player);
      }

      player.sendActionBar(context.message("skill_invisibility_ended", "&7\u9690\u8eab\u5df2\u7ed3\u675f"));
   }

   private void applyTemporaryPotionEffects(Player player, SkillContext context, int durationTicks) {
      this.addTemporaryPotionEffect(player, PotionEffectType.INVISIBILITY, durationTicks, 0);
      this.addTemporaryPotionEffect(player, PotionEffectType.SPEED, durationTicks, context.intParam("\u9690\u8eab", "speed_amplifier", 0));
      this.addTemporaryPotionEffect(player, PotionEffectType.INCREASE_DAMAGE, durationTicks, context.intParam("\u9690\u8eab", "strength_amplifier", 1));
      this.addTemporaryPotionEffect(player, PotionEffectType.DAMAGE_RESISTANCE, durationTicks, context.intParam("\u9690\u8eab", "resistance_amplifier", 2));
      this.addTemporaryPotionEffect(player, PotionEffectType.FIRE_RESISTANCE, durationTicks, context.intParam("\u9690\u8eab", "fire_resistance_amplifier", 0));
   }

   private void addTemporaryPotionEffect(Player player, PotionEffectType type, int durationTicks, int amplifier) {
      player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, true, false), true);
   }

   private void removeTemporaryPotionEffects(Player player) {
      player.removePotionEffect(PotionEffectType.INVISIBILITY);
      player.removePotionEffect(PotionEffectType.SPEED);
      player.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
      player.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
      player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
   }

   private void cancelRestoreTask(UUID uuid) {
      Integer taskId = (Integer)this.restoreTasks.remove(uuid);
      if (taskId != null) {
         Bukkit.getScheduler().cancelTask(taskId);
      }

   }

   private void restoreStoredEquipment(Player player) {
      UUID uuid = player.getUniqueId();
      ItemStack[] armor = (ItemStack[])this.armorStorage.remove(uuid);
      ItemStack offHand = (ItemStack)this.offHandStorage.remove(uuid);
      if (armor != null) {
         player.getInventory().setArmorContents(this.cloneArmorContents(armor));
      }

      if (offHand != null) {
         player.getInventory().setItemInOffHand(this.cloneItem(offHand));
      }

   }

   private void addStoredEquipmentToDrops(PlayerDeathEvent event) {
      UUID uuid = event.getEntity().getUniqueId();
      ItemStack[] armor = (ItemStack[])this.armorStorage.get(uuid);
      if (armor != null) {
         for(ItemStack item : armor) {
            this.addDrop(event, item);
         }
      }

      this.addDrop(event, (ItemStack)this.offHandStorage.get(uuid));
   }

   private void addDrop(PlayerDeathEvent event, ItemStack item) {
      if (item != null && item.getType() != Material.AIR) {
         event.getDrops().add(item.clone());
      }

   }

   private void clearStoredEquipment(UUID uuid) {
      this.armorStorage.remove(uuid);
      this.offHandStorage.remove(uuid);
   }

   private ItemStack[] cloneArmorContents(ItemStack[] contents) {
      ItemStack[] copy = new ItemStack[contents.length];

      for(int i = 0; i < contents.length; ++i) {
         copy[i] = this.cloneItem(contents[i]);
      }

      return copy;
   }

   private ItemStack cloneItem(ItemStack item) {
      return item == null ? null : item.clone();
   }
}
