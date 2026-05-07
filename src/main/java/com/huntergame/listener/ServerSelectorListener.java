package com.huntergame.listener;

import com.huntergame.HunterGame;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.List;

public class ServerSelectorListener implements Listener {
    private static final String LEGACY_PROTECTED_ITEM_KEY = "server_selector";
    private static final String SERVER_SELECTOR_KEY = "huntergame_server_selector";
    private static final String VOTE_ITEM_KEY = "huntergame_vote_item";
    private static final String NON_DROPPABLE_KEY = "non_droppable";

    private final HunterGame plugin;
    private final String serverName;

    public ServerSelectorListener(HunterGame plugin) {
        this.plugin = plugin;
        this.serverName = plugin.getConfig().getString("BungeeCord.server_lobby", "lobby");
    }

    public void giveServerSelector(Player player) {
        FileConfiguration config = plugin.getConfig();
        String materialName = config.getString("BungeeCord.server_selector.material", "ENDER_EYE");
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.ENDER_EYE;
            plugin.getLogger().warning("配置的物品材质 " + materialName + " 无效");
        }

        String displayName = config.getString("BungeeCord.server_selector.display_name",
                plugin.getMessage("server_selector_display_name", "&c&l离开游戏"));
        List<String> lore = config.getStringList("BungeeCord.server_selector.lore");
        if (lore.isEmpty()) {
            lore = Arrays.asList(plugin.getMessage("server_selector_lore", "&7右键传送到主城"));
        }

        int slot = config.getInt("BungeeCord.server_selector.slot", 8);
        if (slot < 0 || slot > 35) {
            plugin.getLogger().warning("配置的槽位 " + slot + " 无效（需 0-35），使用默认槽位 8");
            slot = 8;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, SERVER_SELECTOR_KEY), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        player.getInventory().setItem(slot, item);
    }

    @EventHandler
    public void onClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!isCrossServerItem(item)) {
            return;
        }

        event.setCancelled(true);
        if (connectToServer(player, serverName)) {
            player.sendMessage(plugin.getMessage("bungeecord_connecting", "&a正在连接到 " + serverName + " 服务器..."));
        } else {
            player.sendMessage(plugin.getMessage("bungeecord_connect", "&c跨服连接失败，请联系管理员！"));
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();

        if (isCrossServerItem(item)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessage("cross_server_cannot_drop", "&c跨服物品不能丢弃！"));
            return;
        }

        if (isNonDroppable(item)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessage("cannot_drop", "&c该物品不可丢弃！"));
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        checkAndCancel(event.getItemDrop().getItemStack(), event);
    }

    @EventHandler
    public void onMoveItem(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        if (isVoteItem(clickedItem) || isVoteItem(cursorItem)) {
            event.setCancelled(true);
            return;
        }

        if (isCrossServerItem(clickedItem) || isCrossServerItem(cursorItem)) {
            event.setCancelled(true);
            return;
        }

        checkAndCancel(clickedItem, event);
        checkAndCancel(cursorItem, event);
    }

    @EventHandler
    public void onOffhandSwap(PlayerSwapHandItemsEvent event) {
        ItemStack mainHand = event.getMainHandItem();
        ItemStack offHand = event.getOffHandItem();

        if (isCrossServerItem(mainHand) || isCrossServerItem(offHand)) {
            event.setCancelled(true);
        }

        if (isVoteItem(mainHand) || isVoteItem(offHand)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();

        if (isCrossServerItem(item)) {
            event.setCancelled(true);
            return;
        }

        if (isVoteItem(item)) {
            event.setCancelled(true);
            return;
        }

        checkAndCancel(item, event);
    }

    public boolean isVoteItem(ItemStack item) {
        return hasByteFlag(item, VOTE_ITEM_KEY);
    }

    private boolean isCrossServerItem(ItemStack item) {
        return hasByteFlag(item, SERVER_SELECTOR_KEY);
    }

    private boolean isNonDroppable(ItemStack item) {
        return hasByteFlag(item, NON_DROPPABLE_KEY);
    }

    private void checkAndCancel(ItemStack item, Cancellable event) {
        if (hasByteFlag(item, LEGACY_PROTECTED_ITEM_KEY)) {
            event.setCancelled(true);
        }
    }

    private boolean hasByteFlag(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(new NamespacedKey(plugin, key), PersistentDataType.BYTE);
    }

    private boolean connectToServer(Player player, String server) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DataOutputStream dataOutput = new DataOutputStream(output);
            dataOutput.writeUTF("Connect");
            dataOutput.writeUTF(server);
            player.sendPluginMessage(plugin, "BungeeCord", output.toByteArray());
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("跨服连接失败：" + e.getMessage());
            return false;
        }
    }
}
