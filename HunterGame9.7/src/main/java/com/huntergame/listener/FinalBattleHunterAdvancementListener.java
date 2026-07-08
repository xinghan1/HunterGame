package com.huntergame.listener;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import com.huntergame.HunterGame;
import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

public class FinalBattleHunterAdvancementListener implements Listener {
   private static final String VANILLA_ADVANCEMENT_NAMESPACE = "minecraft";
   private final HunterGame plugin;

   public FinalBattleHunterAdvancementListener(HunterGame plugin) {
      this.plugin = plugin;
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onCriterionGrant(PlayerAdvancementCriterionGrantEvent event) {
      if (this.shouldBlock(event.getPlayer(), event.getAdvancement())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
      if (this.shouldBlock(event.getPlayer(), event.getAdvancement())) {
         event.message((Component)null);
         this.revokeAdvancementNextTick(event.getPlayer(), event.getAdvancement());
      }
   }

   private boolean shouldBlock(Player player, Advancement advancement) {
      if (this.plugin.isGameRunning() && this.plugin.isFinalBattleMode()) {
         return !this.plugin.isHunter(player.getUniqueId()) ? false : "minecraft".equalsIgnoreCase(advancement.getKey().getNamespace());
      } else {
         return false;
      }
   }

   private void revokeAdvancementNextTick(Player player, Advancement advancement) {
      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
         AdvancementProgress progress = player.getAdvancementProgress(advancement);

         for(String criterion : new ArrayList<String>(progress.getAwardedCriteria())) {
            progress.revokeCriteria(criterion);
         }

      });
   }
}
