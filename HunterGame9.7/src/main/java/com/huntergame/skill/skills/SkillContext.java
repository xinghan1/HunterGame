package com.huntergame.skill.skills;

import com.huntergame.HunterGame;
import com.huntergame.skill.SkillManager;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class SkillContext {
   private final HunterGame plugin;
   private final SkillManager manager;

   public SkillContext(HunterGame plugin, SkillManager manager) {
      this.plugin = plugin;
      this.manager = manager;
   }

   public HunterGame plugin() {
      return this.plugin;
   }

   public int cooldown(String skill) {
      return this.manager.getSkillCooldown(skill);
   }

   public int duration(String skill) {
      return this.manager.getSkillDuration(skill);
   }

   public int intParam(String skill, String key, int fallback) {
      return this.manager.getSkillInt(skill, key, fallback);
   }

   public double doubleParam(String skill, String key, double fallback) {
      return this.manager.getSkillDouble(skill, key, fallback);
   }

   public List<Integer> integerListParam(String skill, String key, List<Integer> fallback) {
      return this.manager.getSkillIntegerList(skill, key, fallback);
   }

   public boolean isWeaponOrTool(Material material) {
      return this.manager.isWeaponOrTool(material);
   }

   public boolean isSelected(Player player, String skill) {
      String selectedSkill = this.manager.getSelectedSkill(player);
      return selectedSkill != null && selectedSkill.equals(skill) && this.manager.isSkillEnabled(skill);
   }

   public boolean isEndGlobalCooldownActive() {
      return this.manager.isEndGlobalCooldownActive();
   }

   public boolean checkCooldown(Player player, String skill) {
      return this.manager.checkCooldown(player, skill);
   }

   public void startCooldown(Player player, String skill) {
      this.manager.startCooldown(player, skill);
   }

   public void sendActivationMessage(Player player) {
      player.sendMessage(this.plugin.getMessage("activation_skill", "&7\u6280\u80fd\u5df2\u6fc0\u6d3b\uff01"));
   }

   public String message(String key, String defaultValue) {
      return this.plugin.getMessage(key, defaultValue);
   }

   public String message(String key, String defaultValue, String placeholder, Object value) {
      return this.message(key, defaultValue).replace(placeholder, String.valueOf(value));
   }
}
