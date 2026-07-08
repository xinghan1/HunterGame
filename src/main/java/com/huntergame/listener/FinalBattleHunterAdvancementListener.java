package com.huntergame.listener;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import com.huntergame.HunterGame;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.util.ArrayList;

public class FinalBattleHunterAdvancementListener implements Listener {
    private static final String VANILLA_ADVANCEMENT_NAMESPACE = "minecraft";

    private final HunterGame plugin;

    public FinalBattleHunterAdvancementListener(HunterGame plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCriterionGrant(PlayerAdvancementCriterionGrantEvent event) {
        if (!shouldBlock(event.getPlayer(), event.getAdvancement())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        if (!shouldBlock(event.getPlayer(), event.getAdvancement())) {
            return;
        }

        event.message(null);
        revokeAdvancementNextTick(event.getPlayer(), event.getAdvancement());
    }

    private boolean shouldBlock(Player player, Advancement advancement) {
        if (!plugin.isGameRunning() || !plugin.isFinalBattleMode()) {
            return false;
        }
        if (!plugin.isHunter(player.getUniqueId())) {
            return false;
        }
        return VANILLA_ADVANCEMENT_NAMESPACE.equalsIgnoreCase(advancement.getKey().getNamespace());
    }

    private void revokeAdvancementNextTick(Player player, Advancement advancement) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criterion : new ArrayList<>(progress.getAwardedCriteria())) {
                progress.revokeCriteria(criterion);
            }
        });
    }
}
