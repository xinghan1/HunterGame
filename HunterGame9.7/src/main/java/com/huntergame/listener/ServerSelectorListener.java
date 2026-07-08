package com.huntergame.listener;

import com.huntergame.HunterGame;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.List;
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
      FileConfiguration config = this.plugin.getConfig();
      String materialName = config.getString("BungeeCord.server_selector.material", "ENDER_EYE");
      Material material = Material.matchMaterial(materialName);
      if (material == null) {
         material = Material.ENDER_EYE;
         this.plugin.getLogger().warning("\u914d\u7f6e\u7684\u7269\u54c1\u6750\u8d28 " + materialName + " \u65e0\u6548");
      }

      String displayName = config.getString("BungeeCord.server_selector.display_name", this.plugin.getMessage("server_selector_display_name", "&c&l\u79bb\u5f00\u6e38\u620f"));
      List<String> lore = config.getStringList("BungeeCord.server_selector.lore");
      if (lore.isEmpty()) {
         lore = Arrays.asList(this.plugin.getMessage("server_selector_lore", "&7\u53f3\u952e\u4f20\u9001\u5230\u4e3b\u57ce"));
      }

      int slot = config.getInt("BungeeCord.server_selector.slot", 8);
      if (slot < 0 || slot > 35) {
         this.plugin.getLogger().warning("\u914d\u7f6e\u7684\u69fd\u4f4d " + slot + " \u65e0\u6548\uff08\u9700 0-35\uff09\uff0c\u4f7f\u7528\u9ed8\u8ba4\u69fd\u4f4d 8");
         slot = 8;
      }

      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(displayName);
         meta.setLore(lore);
         meta.getPersistentDataContainer().set(new NamespacedKey(this.plugin, "huntergame_server_selector"), PersistentDataType.BYTE, (byte)1);
         item.setItemMeta(meta);
      }

      player.getInventory().setItem(slot, item);
   }

   @EventHandler
   public void onClick(PlayerInteractEvent event) {
      Action action = event.getAction();
      if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
         Player player = event.getPlayer();
         ItemStack item = event.getItem();
         if (this.isCrossServerItem(item)) {
            event.setCancelled(true);
            if (this.connectToServer(player, this.serverName)) {
               player.sendMessage(this.plugin.getMessage("bungeecord_connecting", "&a\u6b63\u5728\u8fde\u63a5\u5230 " + this.serverName + " \u670d\u52a1\u5668..."));
            } else {
               player.sendMessage(this.plugin.getMessage("bungeecord_connect", "&c\u8de8\u670d\u8fde\u63a5\u5931\u8d25\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\uff01"));
            }

         }
      }
   }

   @EventHandler
   public void onPlayerDropItem(PlayerDropItemEvent event) {
      Player player = event.getPlayer();
      ItemStack item = event.getItemDrop().getItemStack();
      if (this.isCrossServerItem(item)) {
         event.setCancelled(true);
         player.sendMessage(this.plugin.getMessage("cross_server_cannot_drop", "&c\u8de8\u670d\u7269\u54c1\u4e0d\u80fd\u4e22\u5f03\uff01"));
      } else {
         if (this.isNonDroppable(item)) {
            event.setCancelled(true);
            player.sendMessage(this.plugin.getMessage("cannot_drop", "&c\u8be5\u7269\u54c1\u4e0d\u53ef\u4e22\u5f03\uff01"));
         }

      }
   }

   @EventHandler
   public void onDrop(PlayerDropItemEvent event) {
      this.checkAndCancel(event.getItemDrop().getItemStack(), event);
   }

   @EventHandler
   public void onMoveItem(InventoryClickEvent event) {
      if (event.getClickedInventory() != null) {
         ItemStack clickedItem = event.getCurrentItem();
         ItemStack cursorItem = event.getCursor();
         if (!this.isVoteItem(clickedItem) && !this.isVoteItem(cursorItem)) {
            if (!this.isCrossServerItem(clickedItem) && !this.isCrossServerItem(cursorItem)) {
               this.checkAndCancel(clickedItem, event);
               this.checkAndCancel(cursorItem, event);
            } else {
               event.setCancelled(true);
            }
         } else {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler
   public void onOffhandSwap(PlayerSwapHandItemsEvent event) {
      ItemStack mainHand = event.getMainHandItem();
      ItemStack offHand = event.getOffHandItem();
      if (this.isCrossServerItem(mainHand) || this.isCrossServerItem(offHand)) {
         event.setCancelled(true);
      }

      if (this.isVoteItem(mainHand) || this.isVoteItem(offHand)) {
         event.setCancelled(true);
      }

   }

   @EventHandler
   public void onInventoryMove(InventoryMoveItemEvent event) {
      ItemStack item = event.getItem();
      if (this.isCrossServerItem(item)) {
         event.setCancelled(true);
      } else if (this.isVoteItem(item)) {
         event.setCancelled(true);
      } else {
         this.checkAndCancel(item, event);
      }
   }

   public boolean isVoteItem(ItemStack item) {
      return this.hasByteFlag(item, "huntergame_vote_item");
   }

   private boolean isCrossServerItem(ItemStack item) {
      return this.hasByteFlag(item, "huntergame_server_selector");
   }

   private boolean isNonDroppable(ItemStack item) {
      return this.hasByteFlag(item, "non_droppable");
   }

   private void checkAndCancel(ItemStack item, Cancellable event) {
      if (this.hasByteFlag(item, "server_selector")) {
         event.setCancelled(true);
      }

   }

   private boolean hasByteFlag(ItemStack item, String key) {
      if (item != null && item.hasItemMeta()) {
         PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
         return pdc.has(new NamespacedKey(this.plugin, key), PersistentDataType.BYTE);
      } else {
         return false;
      }
   }

   private boolean connectToServer(Player player, String server) {
      try {
         ByteArrayOutputStream output = new ByteArrayOutputStream();
         DataOutputStream dataOutput = new DataOutputStream(output);
         dataOutput.writeUTF("Connect");
         dataOutput.writeUTF(server);
         player.sendPluginMessage(this.plugin, "BungeeCord", output.toByteArray());
         return true;
      } catch (Exception e) {
         this.plugin.getLogger().warning("\u8de8\u670d\u8fde\u63a5\u5931\u8d25\uff1a" + e.getMessage());
         return false;
      }
   }
}
