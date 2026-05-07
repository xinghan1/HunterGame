package com.huntergame.skill.skills;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;

public interface HunterSkill {
    String getName();

    default boolean canActivateByRightClick() {
        return true;
    }

    default boolean canActivateWithItem(ItemStack item, SkillContext context) {
        return item != null && context.isWeaponOrTool(item.getType());
    }

    default SkillActivationResult activate(Player player, SkillContext context) {
        return SkillActivationResult.failure();
    }

    default void onSelected(Player player, SkillContext context) {
    }

    default void onPlayerMove(PlayerMoveEvent event, SkillContext context) {
    }

    default void onPlayerToggleFlight(PlayerToggleFlightEvent event, SkillContext context) {
    }

    default void onLeftClick(PlayerInteractEvent event, SkillContext context) {
    }

    default void onEntityDamageByEntity(EntityDamageByEntityEvent event, SkillContext context) {
    }

    default void onEntityDamage(EntityDamageEvent event, SkillContext context) {
    }

    default void onPlayerRespawn(PlayerRespawnEvent event, SkillContext context) {
    }

    default void onPlayerQuit(PlayerQuitEvent event, SkillContext context) {
    }

    default void onPlayerDeath(PlayerDeathEvent event, SkillContext context) {
    }

}

