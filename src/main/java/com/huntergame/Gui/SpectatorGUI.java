package com.huntergame.Gui;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;

public class SpectatorGUI implements Listener {

    private final HunterGame plugin;

    public SpectatorGUI(HunterGame plugin) {
        this.plugin = plugin;
    }

    /**
     * 设置旁观者的传送头像（仅操作 9~35 格）
     */
    public void updateSpectatorInventory(Player spectator) {
        // 只对真正的旁观者更新背包
        if (!plugin.isRealSpectator(spectator.getUniqueId())) {
            return;
        }

        PlayerInventory inv = spectator.getInventory();

        // 1. 清空背包区 (9-35)，不碰快捷栏 (0-8)
        for (int i = 9; i <= 35; i++) {
            inv.setItem(i, null);
        }

        // 2. 填充玩家头像（只显示非旁观者）
        int slot = 9;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == spectator) continue;
            // 过滤掉旁观者
            if (p.getGameMode() == GameMode.SPECTATOR) continue;

            if (slot > 35) break; // 背包满了就不加了

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(p);
                meta.setDisplayName("§e点击观看：§a" + p.getName());
                head.setItemMeta(meta);
            }

            inv.setItem(slot, head);
            slot++;
        }

        spectator.updateInventory();
    }

    /**
     * 清理旁观者背包（仅清理 9~35 格）
     */
    public void clearSpectatorSlots(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 9; i <= 35; i++) {
            inv.setItem(i, null);
        }
        player.updateInventory();
    }

    /**
     * 当玩家切换游戏模式
     */
    @EventHandler
    public void onGamemodeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        GameMode newMode = event.getNewGameMode();

        // ---- 进入旁观者 ----
        if (newMode == GameMode.SPECTATOR) {
            // 延迟1tick执行，确保模式切换完成后再操作背包
            Bukkit.getScheduler().runTaskLater(
                    plugin,
                    () -> updateSpectatorInventory(player),
                    1
            );
        }
        // ---- 离开旁观者 ----
        else if (player.getGameMode() == GameMode.SPECTATOR && newMode != GameMode.SPECTATOR) {
            // 离开时，只把 9-35 格的头像清空，0-8 格不动
            Bukkit.getScheduler().runTaskLater(
                    plugin,
                    () -> clearSpectatorSlots(player),
                    1
            );
        }
    }

    /**
     * 点击头像传送
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player spectator = (Player) event.getWhoClicked();

        // 严格检查：只有旁观者模式且点击的是背包区(9-35)才处理
        if (spectator.getGameMode() != GameMode.SPECTATOR) return;

        // 只对真正的旁观者处理
        if (!plugin.isRealSpectator(spectator.getUniqueId())) return;

        int slot = event.getSlot();
        // 如果点击的是快捷栏(0-8)，直接忽略，允许玩家操作快捷栏物品
        if (slot < 9 || slot > 35) return;

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() != Material.PLAYER_HEAD) return;

        event.setCancelled(true); // 禁止拿走头像

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null || meta.getOwningPlayer() == null) return;

        Player target = meta.getOwningPlayer().getPlayer();
        if (target == null || !target.isOnline()) {
            spectator.sendMessage("§c玩家已离线！");
            return;
        }

        // 只传送，不设置观看目标
        spectator.teleport(target.getLocation());
        spectator.sendMessage("§a已传送到 §e" + target.getName() + " §a附近");
    }
}