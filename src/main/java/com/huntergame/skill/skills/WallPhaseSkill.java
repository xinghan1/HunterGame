package com.huntergame.skill.skills;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WallPhaseSkill implements HunterSkill {
    private static final String NAME = "穿墙";

    private final Map<UUID, Integer> soulTasks = new HashMap<>();
    private final Map<UUID, Integer> menuRefreshTasks = new HashMap<>();
    private final Set<UUID> soulMode = new HashSet<>();
    private final Map<UUID, InventorySnapshot> inventorySnapshots = new HashMap<>();

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public SkillActivationResult activate(Player player, SkillContext context) {
        int duration = context.duration(NAME);
        UUID uuid = player.getUniqueId();

        inventorySnapshots.putIfAbsent(uuid, InventorySnapshot.capture(player));
        player.setGameMode(GameMode.SPECTATOR);
        soulMode.add(uuid);
        player.sendTitle(
                context.message("skill_wall_phase_title", "&e穿墙"),
                context.message("skill_wall_phase_subtitle", "&7%seconds%秒后回归", "%seconds%", duration),
                10, 40, 10
        );

        cancelTask(uuid);
        int taskId = Bukkit.getScheduler().runTaskLater(context.plugin(), () -> finishWallPhase(player, context), duration * 20L).getTaskId();
        soulTasks.put(uuid, taskId);
        startMenuRefreshTask(player, context);

        return SkillActivationResult.successWithDuration(duration);
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event, SkillContext context) {
        cleanup(event.getPlayer());
    }

    @Override
    public void onPlayerDeath(PlayerDeathEvent event, SkillContext context) {
        cleanup(event.getEntity());
    }

    private void cleanup(Player player) {
        UUID uuid = player.getUniqueId();
        cancelTask(uuid);
        cancelMenuRefreshTask(uuid);
        soulMode.remove(uuid);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        restoreInventory(player);
    }

    private void cancelTask(UUID uuid) {
        Integer taskId = soulTasks.remove(uuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void startMenuRefreshTask(Player player, SkillContext context) {
        UUID uuid = player.getUniqueId();
        cancelMenuRefreshTask(uuid);

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(context.plugin(), () -> {
            if (!player.isOnline() || !soulMode.contains(uuid) || player.getGameMode() != GameMode.SPECTATOR) {
                cancelMenuRefreshTask(uuid);
                return;
            }

            player.updateInventory();
        }, 40L, 40L);
        menuRefreshTasks.put(uuid, taskId);
    }

    private void cancelMenuRefreshTask(UUID uuid) {
        Integer taskId = menuRefreshTasks.remove(uuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void finishWallPhase(Player player, SkillContext context) {
        UUID uuid = player.getUniqueId();
        soulTasks.remove(uuid);
        cancelMenuRefreshTask(uuid);
        soulMode.remove(uuid);

        if (player.isOnline() && player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
            player.sendTitle(
                    context.message("skill_wall_phase_return_title", "&a回归现世"),
                    context.message("skill_wall_phase_return_subtitle", ""),
                    10, 20, 10
            );
        }

        Bukkit.getScheduler().runTaskLater(context.plugin(), () -> restoreInventory(player), 1L);
    }

    private void restoreInventory(Player player) {
        InventorySnapshot snapshot = inventorySnapshots.remove(player.getUniqueId());
        if (snapshot == null) {
            return;
        }

        snapshot.restore(player);
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
            return new InventorySnapshot(
                    cloneContents(player.getInventory().getStorageContents()),
                    cloneContents(player.getInventory().getArmorContents()),
                    cloneItem(player.getInventory().getItemInOffHand()),
                    player.getInventory().getHeldItemSlot()
            );
        }

        private void restore(Player player) {
            player.getInventory().setStorageContents(cloneContents(storageContents));
            player.getInventory().setArmorContents(cloneContents(armorContents));
            player.getInventory().setItemInOffHand(cloneItem(offHand));
            player.getInventory().setHeldItemSlot(heldSlot);
            player.updateInventory();
        }

        private static ItemStack[] cloneContents(ItemStack[] contents) {
            ItemStack[] copy = new ItemStack[contents.length];
            for (int i = 0; i < contents.length; i++) {
                copy[i] = cloneItem(contents[i]);
            }
            return copy;
        }

        private static ItemStack cloneItem(ItemStack item) {
            return item == null ? null : item.clone();
        }
    }
}

