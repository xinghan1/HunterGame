package com.huntergame.Gui;

import com.huntergame.HunterGame;
import com.huntergame.skill.SkillManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SkillSelectionGUI {

    public static void openSkillSelection(HunterGame plugin, Player player) {
        UUID playerId = player.getUniqueId();
        if (player.getOpenInventory().getTitle().equals("选择你的职业")) return;
        if (plugin.isVanillaHunterMode()) return;

        Inventory gui = Bukkit.createInventory(null, 27, "选择你的职业");

        if (plugin.isEscaper(playerId)) { // 逃生者技能
            gui.setItem(0, createSkillItem(Material.FEATHER, "二段跳"));
            gui.setItem(1, createSkillItem(Material.TNT, "爆破专家"));
            gui.setItem(2, createSkillItem(Material.GLASS, "隐身"));
            gui.setItem(3, createSkillItem(Material.PLAYER_HEAD, "穿墙"));
            gui.setItem(4, createSkillItem(Material.STICK, "击退领域"));
            gui.setItem(5, createSkillItem(Material.LAVA_BUCKET, "熔岩行者"));
            gui.setItem(6, createSkillItem(Material.ENDER_PEARL, "闪现"));
            gui.setItem(7, createSkillItem(Material.SUGAR, "肾上腺爆发"));
        } else { // 猎人技能
            gui.setItem(0, createSkillItem(Material.ELYTRA, "二段跳"));
            gui.setItem(1, createSkillItem(Material.GOLDEN_PICKAXE, "盾构机"));
            gui.setItem(2, createSkillItem(Material.SHIELD, "神龟"));
            gui.setItem(3, createSkillItem(Material.SUGAR, "肾上腺爆发"));
            gui.setItem(4, createSkillItem(Material.ENDER_PEARL, "闪现"));
            gui.setItem(5, createSkillItem(Material.ENDER_EYE, "定身术"));
            gui.setItem(6, createSkillItem(Material.CROSSBOW, "爆炸弩"));
        }

        player.openInventory(gui);
    }

    private static ItemStack createSkillItem(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a" + displayName);
            if (SkillManager.SKILL_LORES.containsKey(displayName)) {
                List<String> lore = new ArrayList<>(SkillManager.SKILL_LORES.get(displayName));
                lore.add("");
                lore.add("§8右键选择技能");
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}