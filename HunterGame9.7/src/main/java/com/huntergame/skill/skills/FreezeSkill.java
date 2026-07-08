package com.huntergame.skill.skills;

import com.huntergame.HunterGame;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class FreezeSkill implements Listener {
   private final HunterGame plugin;
   private final Map<UUID, FreezeData> frozenPlayers = new HashMap();
   private final Map<UUID, Integer> freezeTasks = new HashMap();

   public FreezeSkill(HunterGame plugin) {
      this.plugin = plugin;
   }

   public void unfreezeNow(UUID playerId) {
      this.unfreezePlayer(playerId);
   }

   public void freezeEscapers(Player hunter, int durationTicks) {
      this.freezeEscapers(hunter, durationTicks, (double)40.0F, 254);
   }

   public void freezeEscapers(Player hunter, int durationTicks, double radius, int resistanceAmplifier) {
      this.freezeEscapers(hunter, durationTicks, radius, resistanceAmplifier, true);
   }

   public void freezeAllEscapers(Player hunter, int durationTicks, int resistanceAmplifier) {
      this.freezeEscapers(hunter, durationTicks, (double)0.0F, resistanceAmplifier, false);
   }

   private void freezeEscapers(Player hunter, int durationTicks, double radius, int resistanceAmplifier, boolean checkRadius) {
      UUID hunterId = hunter.getUniqueId();
      Location hunterLoc = hunter.getLocation();
      double radiusSquared = radius * radius;

      for(Player target : Bukkit.getOnlinePlayers()) {
         UUID targetId = target.getUniqueId();
         if (this.plugin.isEscaper(targetId) && (!checkRadius || hunterLoc.distanceSquared(target.getLocation()) <= radiusSquared) && !target.hasPotionEffect(PotionEffectType.INVISIBILITY) && target.getGameMode() != GameMode.SPECTATOR) {
            Location originalLoc = target.getLocation().clone();
            target.setMetadata("frozen_by_perspective", new FixedMetadataValue(this.plugin, hunterId));
            target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 1.0F, 1.5F);
            target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), 20, (double)0.5F, (double)0.5F, (double)0.5F, 0.2);
            target.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, durationTicks, resistanceAmplifier, true, false));
            this.frozenPlayers.put(targetId, new FreezeData(hunterId, durationTicks, originalLoc));
            int taskId = Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.unfreezePlayer(targetId), (long)durationTicks).getTaskId();
            this.freezeTasks.put(targetId, taskId);
         }
      }

   }

   private void unfreezePlayer(UUID playerId) {
      if (this.frozenPlayers.containsKey(playerId)) {
         Player player = Bukkit.getPlayer(playerId);
         if (player != null) {
            player.removeMetadata("frozen_by_perspective", this.plugin);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.8F);
         }

         Integer taskId = (Integer)this.freezeTasks.remove(playerId);
         if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
         }

         this.frozenPlayers.remove(playerId);
      }
   }

   @EventHandler
   public void onPlayerMove(PlayerMoveEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      if (this.isPlayerFrozen(playerId)) {
         FreezeData freezeData = (FreezeData)this.frozenPlayers.get(playerId);
         if (freezeData != null) {
            Location originalLoc = freezeData.getOriginalLocation();
            if (this.isPositionChanged(event.getTo(), originalLoc)) {
               event.setCancelled(true);
               player.teleport(originalLoc);
               player.setRotation(originalLoc.getYaw(), originalLoc.getPitch());
            }

         }
      }
   }

   public boolean isPlayerFrozen(UUID playerId) {
      Player player = Bukkit.getPlayer(playerId);
      return this.frozenPlayers.containsKey(playerId) && player != null && player.hasMetadata("frozen_by_perspective");
   }

   private boolean isPositionChanged(Location newLoc, Location originalLoc) {
      if (newLoc == null) {
         return true;
      } else {
         double tolerance = 0.01;
         return Math.abs(newLoc.getX() - originalLoc.getX()) > tolerance || Math.abs(newLoc.getY() - originalLoc.getY()) > tolerance || Math.abs(newLoc.getZ() - originalLoc.getZ()) > tolerance || Math.abs(newLoc.getYaw() - originalLoc.getYaw()) > 1.0F || Math.abs(newLoc.getPitch() - originalLoc.getPitch()) > 1.0F;
      }
   }

   private static class FreezeData {
      private final UUID hunterId;
      private final int durationTicks;
      private final Location originalLocation;

      FreezeData(UUID hunterId, int durationTicks, Location originalLocation) {
         this.hunterId = hunterId;
         this.durationTicks = durationTicks;
         this.originalLocation = originalLocation;
      }

      public UUID getHunterId() {
         return this.hunterId;
      }

      public int getDurationTicks() {
         return this.durationTicks;
      }

      public Location getOriginalLocation() {
         return this.originalLocation;
      }
   }
}
