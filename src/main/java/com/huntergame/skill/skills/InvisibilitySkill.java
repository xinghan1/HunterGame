package com.huntergame.skill.skills;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class InvisibilitySkill implements HunterSkill {
    private static final String NAME = "隐身";

    private final Map<UUID, ItemStack[]> armorStorage = new HashMap<>();
    private final Map<UUID, ItemStack> offHandStorage = new HashMap<>();
    private final Map<UUID, Integer> restoreTasks = new HashMap<>();

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public SkillActivationResult activate(Player player, SkillContext context) {
        int duration = context.duration(NAME);
        UUID uuid = player.getUniqueId();

        player.sendTitle(
                context.message("skill_invisibility_title", "&b👻 &l隐身 &r👻"),
                context.message("skill_duration_subtitle", "&7持续时间: &a%seconds%秒", "%seconds%", duration),
                10, 60, 10
        );

        armorStorage.putIfAbsent(uuid, cloneArmorContents(player.getInventory().getArmorContents()));
        offHandStorage.putIfAbsent(uuid, cloneItem(player.getInventory().getItemInOffHand()));
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));

        if (context.plugin().getFreezeSkill().isPlayerFrozen(uuid)) {
            context.plugin().getFreezeSkill().unfreezeNow(uuid);
        }
        player.removePotionEffect(PotionEffectType.GLOWING);
        applyTemporaryPotionEffects(player, context, duration * 20);

        cancelRestoreTask(uuid);
        int taskId = Bukkit.getScheduler().runTaskLater(context.plugin(), () -> restorePlayerVisibility(player, context), duration * 20L).getTaskId();
        restoreTasks.put(uuid, taskId);

        context.sendActivationMessage(player);
        return SkillActivationResult.successWithDuration(duration);
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event, SkillContext context) {
        restorePlayerVisibility(event.getPlayer(), context);
    }

    @Override
    public void onPlayerDeath(PlayerDeathEvent event, SkillContext context) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        cancelRestoreTask(uuid);
        if (event.getKeepInventory()) {
            restoreStoredEquipment(player);
        } else {
            addStoredEquipmentToDrops(event);
            clearStoredEquipment(uuid);
        }
        removeTemporaryPotionEffects(player);
    }

    private void restorePlayerVisibility(Player player, SkillContext context) {
        UUID uuid = player.getUniqueId();
        cancelRestoreTask(uuid);

        restoreStoredEquipment(player);

        removeTemporaryPotionEffects(player);
        player.updateInventory();
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.hidePlayer(context.plugin(), player);
            online.showPlayer(context.plugin(), player);
        }
        player.sendActionBar(context.message("skill_invisibility_ended", "&7隐身已结束"));
    }

    private void applyTemporaryPotionEffects(Player player, SkillContext context, int durationTicks) {
        addTemporaryPotionEffect(player, PotionEffectType.INVISIBILITY, durationTicks, 0);
        addTemporaryPotionEffect(player, PotionEffectType.SPEED, durationTicks, context.intParam(NAME, "speed_amplifier", 0));
        addTemporaryPotionEffect(player, PotionEffectType.STRENGTH, durationTicks, context.intParam(NAME, "strength_amplifier", 1));
        addTemporaryPotionEffect(player, PotionEffectType.RESISTANCE, durationTicks, context.intParam(NAME, "resistance_amplifier", 2));
        addTemporaryPotionEffect(player, PotionEffectType.FIRE_RESISTANCE, durationTicks, context.intParam(NAME, "fire_resistance_amplifier", 0));
    }

    private void addTemporaryPotionEffect(Player player, PotionEffectType type, int durationTicks, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, true, false), true);
    }

    private void removeTemporaryPotionEffects(Player player) {
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
    }

    private void cancelRestoreTask(UUID uuid) {
        Integer taskId = restoreTasks.remove(uuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void restoreStoredEquipment(Player player) {
        UUID uuid = player.getUniqueId();
        ItemStack[] armor = armorStorage.remove(uuid);
        ItemStack offHand = offHandStorage.remove(uuid);

        if (armor != null) {
            player.getInventory().setArmorContents(cloneArmorContents(armor));
        }
        if (offHand != null) {
            player.getInventory().setItemInOffHand(cloneItem(offHand));
        }
    }

    private void addStoredEquipmentToDrops(PlayerDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        ItemStack[] armor = armorStorage.get(uuid);
        if (armor != null) {
            for (ItemStack item : armor) {
                addDrop(event, item);
            }
        }
        addDrop(event, offHandStorage.get(uuid));
    }

    private void addDrop(PlayerDeathEvent event, ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            event.getDrops().add(item.clone());
        }
    }

    private void clearStoredEquipment(UUID uuid) {
        armorStorage.remove(uuid);
        offHandStorage.remove(uuid);
    }

    private ItemStack[] cloneArmorContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = cloneItem(contents[i]);
        }
        return copy;
    }

    private ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }
}

