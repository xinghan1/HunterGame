package com.huntergame.skill.skills;

import com.huntergame.HunterGame;
import com.huntergame.skill.SkillManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public final class SkillContext {
    private final HunterGame plugin;
    private final SkillManager manager;

    public SkillContext(HunterGame plugin, SkillManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public HunterGame plugin() {
        return plugin;
    }

    public int cooldown(String skill) {
        return manager.getSkillCooldown(skill);
    }

    public int duration(String skill) {
        return manager.getSkillDuration(skill);
    }

    public int intParam(String skill, String key, int fallback) {
        return manager.getSkillInt(skill, key, fallback);
    }

    public double doubleParam(String skill, String key, double fallback) {
        return manager.getSkillDouble(skill, key, fallback);
    }

    public List<Integer> integerListParam(String skill, String key, List<Integer> fallback) {
        return manager.getSkillIntegerList(skill, key, fallback);
    }

    public boolean isWeaponOrTool(Material material) {
        return manager.isWeaponOrTool(material);
    }

    public boolean isSelected(Player player, String skill) {
        String selectedSkill = manager.getSelectedSkill(player);
        return selectedSkill != null && selectedSkill.equals(skill) && manager.isSkillEnabled(skill);
    }

    public boolean isEndGlobalCooldownActive() {
        return manager.isEndGlobalCooldownActive();
    }

    public boolean checkCooldown(Player player, String skill) {
        return manager.checkCooldown(player, skill);
    }

    public void startCooldown(Player player, String skill) {
        manager.startCooldown(player, skill);
    }

    public void sendActivationMessage(Player player) {
        player.sendMessage(plugin.getMessage("activation_skill", "&7技能已激活！"));
    }

    public String message(String key, String defaultValue) {
        return plugin.getMessage(key, defaultValue);
    }

    public String message(String key, String defaultValue, String placeholder, Object value) {
        return message(key, defaultValue).replace(placeholder, String.valueOf(value));
    }
}

