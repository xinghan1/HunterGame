package com.huntergame.gui;

import com.huntergame.HunterGame;
import com.huntergame.bedrock.BedrockGuideGUI;
import com.huntergame.util.BedrockSupport;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public class GuideGUI implements Listener {
   private final HunterGame plugin;
   private final File configFile;
   private FileConfiguration config;
   private String guiTitle;
   private int guiSize;
   private ItemStack triggerItem;
   private final List<GuideItem> guideItems = new ArrayList();
   private final String persistentKey = "guide_gui_trigger";
   private int forcedSlot = 8;

   public GuideGUI(HunterGame plugin) {
      this.plugin = plugin;
      this.configFile = new File(plugin.getDataFolder(), "guidegui.yml");
      this.saveDefaultConfig();
      this.loadConfig();
      Bukkit.getPluginManager().registerEvents(this, plugin);
   }

   private void saveDefaultConfig() {
      if (!this.configFile.exists()) {
         this.plugin.saveResource("guidegui.yml", false);
      }

   }

   public void loadConfig() {
      this.config = YamlConfiguration.loadConfiguration(this.configFile);
      this.guideItems.clear();
      this.guiTitle = ChatColor.translateAlternateColorCodes('&', this.config.getString("gui.title", "&6\u73a9\u6cd5\u4ecb\u7ecd"));
      this.guiSize = this.config.getInt("gui.size", 27);
      if (this.guiSize % 9 != 0) {
         this.guiSize = 27;
      }

      this.forcedSlot = this.config.getInt("trigger-item.forced-slot", 8);
      if (this.forcedSlot < 0 || this.forcedSlot > 35) {
         this.forcedSlot = 8;
      }

      this.initTriggerItem();
      this.loadGuideItems();
   }

   private void initTriggerItem() {
      Material material = Material.valueOf(this.config.getString("trigger-item.material", "BOOK").toUpperCase());
      String name = ChatColor.translateAlternateColorCodes('&', this.config.getString("trigger-item.name", "&a\u73a9\u6cd5\u6307\u5357"));
      List<String> lore = new ArrayList();

      for(String line : this.config.getStringList("trigger-item.lore")) {
         lore.add(ChatColor.translateAlternateColorCodes('&', line));
      }

      this.triggerItem = new ItemStack(material);
      ItemMeta meta = this.triggerItem.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(name);
         meta.setLore(lore);
         PersistentDataContainer container = meta.getPersistentDataContainer();
         container.set(new NamespacedKey(this.plugin, "guide_gui_trigger"), PersistentDataType.STRING, "guide_gui_trigger");
         meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS});
         this.triggerItem.setItemMeta(meta);
      }

   }

   private void loadGuideItems() {
      if (this.config.contains("items")) {
         for(String key : this.config.getConfigurationSection("items").getKeys(false)) {
            String path = "items." + key;
            int slot = this.config.getInt(path + ".slot", 0);
            Material material = Material.valueOf(this.config.getString(path + ".material", "PAPER").toUpperCase());
            String name = ChatColor.translateAlternateColorCodes('&', this.config.getString(path + ".name", "\u672a\u547d\u540d\u7269\u54c1"));
            List<String> lore = new ArrayList();

            for(String line : this.config.getStringList(path + ".lore")) {
               lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            String clickAction = this.config.getString(path + ".click-action", "none");
            this.guideItems.add(new GuideItem(slot, material, name, lore, clickAction));
         }

      }
   }

   public void giveTriggerItem(final Player player) {
      if (player != null && player.isOnline()) {
         Inventory inv = player.getInventory();
         ItemStack slotItem = inv.getItem(this.forcedSlot);
         if (slotItem == null || !this.isTriggerItem(slotItem)) {
            inv.setItem(this.forcedSlot, this.triggerItem.clone());
            if (slotItem != null && !slotItem.getType().isAir()) {
               HashMap<Integer, ItemStack> leftover = inv.addItem(new ItemStack[]{slotItem});

               for(ItemStack item : leftover.values()) {
                  player.getWorld().dropItem(player.getLocation(), item);
               }
            }

            (new BukkitRunnable() {
               public void run() {
                  player.updateInventory();
               }
            }).runTaskLater(this.plugin, 1L);
         }
      }
   }

   public void openGuideGUI(Player player) {
      if (player != null && player.isOnline()) {
         InventoryView openView = player.getOpenInventory();
         if (!openView.getTitle().equals(this.guiTitle)) {
            Inventory gui = Bukkit.createInventory((InventoryHolder)null, this.guiSize, this.guiTitle);

            for(GuideItem item : this.guideItems) {
               if (item.slot >= 0 && item.slot < this.guiSize) {
                  ItemStack stack = new ItemStack(item.material);
                  ItemMeta meta = stack.getItemMeta();
                  if (meta != null) {
                     meta.setDisplayName(item.name);
                     meta.setLore(item.lore);
                     stack.setItemMeta(meta);
                  }

                  gui.setItem(item.slot, stack);
               }
            }

            player.openInventory(gui);
         }
      }
   }

   @EventHandler
   public void onTriggerClick(PlayerInteractEvent event) {
      Action action = event.getAction();
      Player player = event.getPlayer();
      ItemStack item = event.getItem();
      if (item != null && this.isTriggerItem(item)) {
         String triggerAction = this.config.getString("trigger-item.trigger-action", "RIGHT_CLICK");
         boolean isRight = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
         boolean isLeft = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
         if (triggerAction.equalsIgnoreCase("RIGHT_CLICK") && isRight || triggerAction.equalsIgnoreCase("LEFT_CLICK") && isLeft || triggerAction.equalsIgnoreCase("BOTH") && (isRight || isLeft)) {
            if (BedrockSupport.isBedrockPlayer(this.plugin, player)) {
               try {
                  BedrockGuideGUI.openBedrockGuide(this.plugin, this.guideItems, this.guiTitle, player);
               } catch (Exception var9) {
                  this.openGuideGUI(player);
               }
            } else {
               this.openGuideGUI(player);
            }

            event.setCancelled(true);
         }

      }
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player) {
         Player player = (Player)event.getWhoClicked();
         InventoryView view = event.getView();
         if (view.getTitle().equals(this.guiTitle)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.hasItemMeta()) {
               String itemName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

               for(GuideItem item : this.guideItems) {
                  if (ChatColor.stripColor(item.name).equals(itemName)) {
                     this.handleItemClick(player, item.clickAction);
                     break;
                  }
               }

            }
         }
      }
   }

   @EventHandler
   public void onItemMove(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player) {
         Player player = (Player)event.getWhoClicked();
         ItemStack current = event.getCurrentItem();
         ItemStack cursor = event.getCursor();
         if (current != null && this.isTriggerItem(current)) {
            event.setCancelled(true);
         } else {
            if (cursor != null && this.isTriggerItem(cursor)) {
               event.setCancelled(true);
            }

         }
      }
   }

   @EventHandler
   public void onItemDrop(PlayerDropItemEvent event) {
      ItemStack item = event.getItemDrop().getItemStack();
      if (this.isTriggerItem(item)) {
         event.setCancelled(true);
      }

   }

   @EventHandler
   public void onOffhandSwap(PlayerSwapHandItemsEvent event) {
      ItemStack mainHand = event.getMainHandItem();
      ItemStack offHand = event.getOffHandItem();
      if (mainHand != null && this.isTriggerItem(mainHand) || offHand != null && this.isTriggerItem(offHand)) {
         event.setCancelled(true);
      }

   }

   @EventHandler
   public void onInventoryChange(InventoryEvent event) {
      Inventory inv = event.getInventory();
      if (inv instanceof PlayerInventory) {
         Player player = (Player)inv.getHolder();
         if (player != null && player.isOnline()) {
            ItemStack correctSlotItem = inv.getItem(this.forcedSlot);
            if (correctSlotItem == null || !this.isTriggerItem(correctSlotItem)) {
               for(int i = 0; i < inv.getSize(); ++i) {
                  ItemStack item = inv.getItem(i);
                  if (item != null && this.isTriggerItem(item)) {
                     ItemStack temp = inv.getItem(this.forcedSlot);
                     inv.setItem(this.forcedSlot, item);
                     inv.setItem(i, temp);
                     break;
                  }
               }
            }

            boolean hasItem = false;

            for(ItemStack item : inv.getContents()) {
               if (item != null && this.isTriggerItem(item)) {
                  hasItem = true;
                  break;
               }
            }

            if (!hasItem) {
               this.giveTriggerItem(player);
            }

         }
      }
   }

   private void handleItemClick(Player player, String action) {
      if (action != null && !action.equalsIgnoreCase("none")) {
         if (action.equalsIgnoreCase("open-submenu")) {
            player.sendMessage(this.plugin.getMessage("guide_open_submenu", "&a\u6253\u5f00\u5b50\u83dc\u5355\uff08\u793a\u4f8b\uff09"));
         }

      }
   }

   private boolean isTriggerItem(ItemStack item) {
      if (!item.hasItemMeta()) {
         return false;
      } else {
         PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
         return container.has(new NamespacedKey(this.plugin, "guide_gui_trigger"), PersistentDataType.STRING);
      }
   }

   public void saveConfig() {
      try {
         this.config.save(this.configFile);
      } catch (IOException e) {
         this.plugin.getLogger().severe("\u65e0\u6cd5\u4fdd\u5b58guidegui.yml: " + e.getMessage());
      }

   }

   public void reloadConfig() {
      this.loadConfig();
   }

   public static class GuideItem {
      public int slot;
      public Material material;
      public String name;
      public List<String> lore;
      public String clickAction;

      GuideItem(int slot, Material material, String name, List<String> lore, String clickAction) {
         this.slot = slot;
         this.material = material;
         this.name = name;
         this.lore = lore;
         this.clickAction = clickAction;
      }
   }
}
