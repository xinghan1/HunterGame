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
    private static final String NAME = "爆炸弩";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean canActivateByRightClick() {
        return false;
    }

    @Override
    public SkillActivationResult activate(Player player, SkillContext context) {
        if (!context.plugin().isEscaper(player.getUniqueId())) {
            context.plugin().getExplosiveCrossbowListener().giveCrossbowPackage(player);
        }
        return SkillActivationResult.success();
    }

    @Override
    public void onSelected(Player player, SkillContext context) {
        if (!context.plugin().isEscaper(player.getUniqueId())) {
            context.plugin().getExplosiveCrossbowListener().giveCrossbowPackage(player);
        }
    }

    @Override
    public void onPlayerRespawn(PlayerRespawnEvent event, SkillContext context) {
        Player player = event.getPlayer();
        if (context.isSelected(player, NAME)) {
            context.plugin().getExplosiveCrossbowListener().giveCrossbowPackage(player);
        }
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event, SkillContext context) {
        Player player = event.getPlayer();
        if (!context.isSelected(player, NAME)) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isCrossbowSkillItem(item, context)) {
            return;
        }

        if (!context.checkCooldown(player, NAME)) {
            player.sendActionBar(context.message("skill_on_cooldown", "&c技能冷却中！"));
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(context.plugin().getMessage("backpack_full", "背包已满，无法获取弹药！"));
            return;
        }

        int rocketCount = context.intParam(NAME, "rockets_per_supply", 5);
        ItemStack rockets = context.plugin().getExplosiveCrossbowListener().createExplosiveRockets(rocketCount);
        player.getInventory().addItem(rockets);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 0.5f);
        player.spawnParticle(Particle.FIREWORK, player.getEyeLocation(), 20);
        player.sendTitle(
                context.message("skill_explosive_crossbow_title", ""),
                context.message("skill_explosive_crossbow_subtitle", "&e✧ 获得%count%发爆炸火箭 ✧", "%count%", rocketCount),
                10, 40, 10
        );

        context.startCooldown(player, NAME);
    }

    private boolean isCrossbowSkillItem(ItemStack item, SkillContext context) {
        if (item == null || item.getType() != Material.CROSSBOW || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta.hasLore() && meta.getLore().contains(context.plugin().getExplosiveCrossbowListener().getCrossbowSupplyLore());
    }
}

