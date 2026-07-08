package com.huntergame.skill.skills;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ExplosiveCrossbowSkill implements HunterSkill {
   private static final String NAME = "\u7206\u70b8\u5f29";

   public String getName() {
      return "\u7206\u70b8\u5f29";
   }

   public boolean canActivateByRightClick() {
      return false;
   }

   public SkillActivationResult activate(Player player, SkillContext context) {
      if (!context.plugin().isEscaper(player.getUniqueId())) {
         context.plugin().getExplosiveCrossbowListener().giveCrossbowPackage(player);
      }

      return SkillActivationResult.success();
   }

   public void onSelected(Player player, SkillContext context) {
      if (!context.plugin().isEscaper(player.getUniqueId())) {
         context.plugin().getExplosiveCrossbowListener().giveCrossbowPackage(player);
      }

   }

   public void onPlayerRespawn(PlayerRespawnEvent event, SkillContext context) {
      Player player = event.getPlayer();
      if (context.isSelected(player, "\u7206\u70b8\u5f29")) {
         context.plugin().getExplosiveCrossbowListener().giveCrossbowPackage(player);
      }

   }

   public void onLeftClick(PlayerInteractEvent event, SkillContext context) {
      Player player = event.getPlayer();
      if (context.isSelected(player, "\u7206\u70b8\u5f29")) {
         ItemStack item = player.getInventory().getItemInMainHand();
         if (this.isCrossbowSkillItem(item, context)) {
            if (!context.checkCooldown(player, "\u7206\u70b8\u5f29")) {
               player.sendActionBar(context.message("skill_on_cooldown", "&c\u6280\u80fd\u51b7\u5374\u4e2d\uff01"));
            } else if (player.getInventory().firstEmpty() == -1) {
               player.sendMessage(context.plugin().getMessage("backpack_full", "\u80cc\u5305\u5df2\u6ee1\uff0c\u65e0\u6cd5\u83b7\u53d6\u5f39\u836f\uff01"));
            } else {
               int rocketCount = context.intParam("\u7206\u70b8\u5f29", "rockets_per_supply", 5);
               ItemStack rockets = context.plugin().getExplosiveCrossbowListener().createExplosiveRockets(rocketCount);
               player.getInventory().addItem(new ItemStack[]{rockets});
               player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0F, 0.5F);
               player.spawnParticle(Particle.FIREWORKS_SPARK, player.getEyeLocation(), 20);
               player.sendTitle(context.message("skill_explosive_crossbow_title", ""), context.message("skill_explosive_crossbow_subtitle", "&e\u2727 \u83b7\u5f97%count%\u53d1\u7206\u70b8\u706b\u7bad \u2727", "%count%", rocketCount), 10, 40, 10);
               context.startCooldown(player, "\u7206\u70b8\u5f29");
            }
         }
      }
   }

   private boolean isCrossbowSkillItem(ItemStack item, SkillContext context) {
      if (item != null && item.getType() == Material.CROSSBOW && item.hasItemMeta()) {
         ItemMeta meta = item.getItemMeta();
         return meta.hasLore() && meta.getLore().contains(context.plugin().getExplosiveCrossbowListener().getCrossbowSupplyLore());
      } else {
         return false;
      }
   }
}
