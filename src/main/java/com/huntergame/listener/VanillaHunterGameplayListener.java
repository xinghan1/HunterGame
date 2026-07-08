package com.huntergame.listener;

import com.huntergame.HunterGame;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class VanillaHunterGameplayListener implements Listener {
    private final HunterGame plugin;

    public VanillaHunterGameplayListener(HunterGame plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isClassicGameRunning() || !plugin.getConfig().getBoolean("game.vanilla_hunter.auto_smelt.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            return;
        }

        Block block = event.getBlock();
        Collection<ItemStack> originalDrops = block.getDrops(tool, player);
        List<ItemStack> finalDrops = new ArrayList<>();
        boolean changed = false;

        for (ItemStack drop : originalDrops) {
            ItemStack smelted = smelt(drop);
            finalDrops.add(smelted);
            changed = changed || smelted.getType() != drop.getType();
        }

        if (!changed) {
            return;
        }

        event.setDropItems(false);
        for (ItemStack drop : finalDrops) {
            block.getWorld().dropItemNaturally(block.getLocation(), drop);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!isClassicGameRunning() || !plugin.getConfig().getBoolean("game.vanilla_hunter.blaze_rod_guarantee.enabled", true)) {
            return;
        }
        if (!(event.getEntity() instanceof Blaze) || event.getEntity().getKiller() == null) {
            return;
        }

        int amount = Math.max(1, plugin.getConfig().getInt("game.vanilla_hunter.blaze_rod_guarantee.amount", 1));
        if (hasBlazeRod(event.getDrops())) {
            return;
        }

        event.getDrops().add(new ItemStack(Material.BLAZE_ROD, amount));
    }

    private boolean isClassicGameRunning() {
        return plugin.isGameRunning() && plugin.isVanillaHunterMode();
    }

    private boolean hasBlazeRod(List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (drop.getType() == Material.BLAZE_ROD && drop.getAmount() > 0) {
                return true;
            }
        }
        return false;
    }

    private ItemStack smelt(ItemStack drop) {
        Material result = switch (drop.getType()) {
            case RAW_IRON, IRON_ORE, DEEPSLATE_IRON_ORE -> Material.IRON_INGOT;
            case RAW_GOLD, GOLD_ORE, DEEPSLATE_GOLD_ORE -> Material.GOLD_INGOT;
            case RAW_COPPER, COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.COPPER_INGOT;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            default -> null;
        };

        return result == null ? drop : new ItemStack(result, drop.getAmount());
    }
}
