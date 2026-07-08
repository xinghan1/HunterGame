package com.huntergame.vote;

import com.huntergame.HunterGame;
import com.huntergame.bedrock.BedrockVoteSystemGUI;
import com.huntergame.gui.VoteSystemGUI;
import com.huntergame.util.BedrockSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public class VoteSystem implements Listener {
   public static final int VOTE_ESCAPER = 1;
   public static final int VOTE_HUNTER = 2;
   public static final int MODE_FINAL_BATTLE = 2;
   public static final int TYPE_PERSISTENCE = 1;
   public static final int TYPE_CLEARANCE = 2;
   private final HunterGame plugin;
   private final Map<UUID, Integer> playerVotes = new HashMap();
   public final Map<UUID, Integer> playerModeChoice = new HashMap();
   private final Map<UUID, Integer> playerTypeChoice = new HashMap();
   private ConfigurationSection voteGuiConfig;
   private ConfigurationSection voteItemConfig;

   public VoteSystem(HunterGame plugin) {
      this.plugin = plugin;
      this.loadConfig();
      Bukkit.getPluginManager().registerEvents(this, plugin);
   }

   private void loadConfig() {
      this.plugin.saveDefaultConfig();
      this.voteGuiConfig = this.plugin.getGuiConfig().getConfigurationSection("vote-gui");
      this.voteItemConfig = this.plugin.getGuiConfig().getConfigurationSection("vote-item");
      if (this.voteGuiConfig == null) {
         this.voteGuiConfig = this.plugin.getGuiConfig().createSection("vote-gui");
         this.initDefaultVoteGuiConfig();
      }

      if (this.voteItemConfig == null) {
         this.voteItemConfig = this.plugin.getGuiConfig().createSection("vote-item");
         this.initDefaultVoteItemConfig();
      }

      this.plugin.saveConfig();
   }

   public boolean isFixedModeEnabled() {
      return true;
   }

   public Object[] getFixedModeInfo() {
      return new Object[]{2, "终章之战"};
   }

   private void initDefaultVoteGuiConfig() {
      this.voteGuiConfig.set("title", "\u6a21\u5f0f\u4e0e\u89d2\u8272\u9009\u62e9");
      this.voteGuiConfig.set("size", 36);
      this.voteGuiConfig.set("messages.no-mode-selected", "&e\u8bf7\u5148\u9009\u62e9\u6e38\u620f\u6a21\u5f0f\uff01");
      ConfigurationSection typeSection = this.voteGuiConfig.createSection("type-section");
      typeSection.set("slot", 3);
      ConfigurationSection typeItems = typeSection.createSection("items");
      ConfigurationSection persistence = typeItems.createSection("0");
      persistence.set("id", 1);
      persistence.set("material", "CLOCK");
      persistence.set("name", "&e\u751f\u5b58\u6218");
      ConfigurationSection clearance = typeItems.createSection("1");
      clearance.set("id", 2);
      clearance.set("material", "DIAMOND_SWORD");
      clearance.set("name", "&a\u901a\u5173\u6218");
      ConfigurationSection roleSection = this.voteGuiConfig.createSection("role-section");
      roleSection.set("slot", 21);
      ConfigurationSection roleItems = roleSection.createSection("items");
      ConfigurationSection escaper = roleItems.createSection("0");
      escaper.set("id", 1);
      escaper.set("material", "EMERALD");
      escaper.set("name", "&a\u9003\u751f\u8005\u89d2\u8272");
      ConfigurationSection hunter = roleItems.createSection("1");
      hunter.set("id", 2);
      hunter.set("material", "REDSTONE");
      hunter.set("name", "&c\u730e\u4eba\u89d2\u8272");
      ConfigurationSection divider = this.voteGuiConfig.createSection("divider");
      divider.set("material", "GRAY_STAINED_GLASS_PANE");
      divider.set("name", " ");
   }

   private void initDefaultVoteItemConfig() {
      this.voteItemConfig.set("material", "NAME_TAG");
      this.voteItemConfig.set("name", "&6\u6295\u7968");
      this.voteItemConfig.set("lore", Collections.singletonList("&7\u53f3\u952e\u70b9\u51fb\u9009\u62e9\u4f60\u60f3\u52a0\u5165\u7684\u9635\u8425"));
      this.voteItemConfig.set("slot", 1);
      this.voteItemConfig.set("persistent-key", "campaign_vote");
   }

   public void giveVoteItemToPlayer(Player player) {
      if (player != null && player.isOnline()) {
         ItemStack voteItem = this.createVoteItem();
         int slot = this.voteItemConfig.getInt("slot", 1);
         player.getInventory().setItem(slot, voteItem);
      }
   }

   private ItemStack createVoteItem() {
      Material material = Material.valueOf(this.voteItemConfig.getString("material", "NAME_TAG"));
      String name = ChatColor.translateAlternateColorCodes('&', this.voteItemConfig.getString("name", "&6\u6295\u7968"));
      List<String> lore = (List)this.voteItemConfig.getStringList("lore").stream().map((line) -> ChatColor.translateAlternateColorCodes('&', line)).collect(Collectors.toList());
      String persistentKey = this.voteItemConfig.getString("persistent-key", "campaign_vote");
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(name);
         meta.setLore(lore);
         NamespacedKey voteKey = new NamespacedKey(this.plugin, "huntergame_vote_item");
         PersistentDataContainer container = meta.getPersistentDataContainer();
         container.set(new NamespacedKey(this.plugin, persistentKey), PersistentDataType.STRING, persistentKey);
         meta.getPersistentDataContainer().set(voteKey, PersistentDataType.BYTE, (byte)1);
         item.setItemMeta(meta);
      }

      return item;
   }

   public void addTypeItemsToGUI(Inventory gui) {
      ConfigurationSection typeSection = this.voteGuiConfig.getConfigurationSection("type-section");
      if (typeSection != null) {
         int baseSlot = typeSection.getInt("slot", 3);
         List<ConfigurationSection> typeItems = this.getConfigItems(typeSection.getConfigurationSection("items"));

         for(int i = 0; i < typeItems.size(); ++i) {
            ConfigurationSection itemConfig = (ConfigurationSection)typeItems.get(i);
            ItemStack item = this.createConfigItem(itemConfig);
            this.addVoteCountToItemLore(item, itemConfig.getInt("id"), "type");
            int targetSlot = baseSlot + i * 2;
            if (targetSlot < gui.getSize()) {
               gui.setItem(targetSlot, item);
            }
         }

      }
   }

   public void addModeItemsToGUI(Inventory gui) {
   }

   public void addRoleItemsToGUI(Inventory gui) {
      ConfigurationSection roleSection = this.voteGuiConfig.getConfigurationSection("role-section");
      if (roleSection != null) {
         int baseSlot = roleSection.getInt("slot", 21);
         List<ConfigurationSection> roleItems = this.getConfigItems(roleSection.getConfigurationSection("items"));

         for(int i = 0; i < roleItems.size(); ++i) {
            ConfigurationSection itemConfig = (ConfigurationSection)roleItems.get(i);
            ItemStack item = this.createConfigItem(itemConfig);
            this.addVoteCountToItemLore(item, itemConfig.getInt("id"), "role");
            int targetSlot = baseSlot + i * 2;
            if (targetSlot < gui.getSize()) {
               gui.setItem(targetSlot, item);
            }
         }

      }
   }

   public void fillDividerItems(Inventory gui) {
      ConfigurationSection dividerConfig = this.voteGuiConfig.getConfigurationSection("divider");
      if (dividerConfig != null) {
         Material material = Material.valueOf(dividerConfig.getString("material", "GRAY_STAINED_GLASS_PANE"));
         String name = ChatColor.translateAlternateColorCodes('&', dividerConfig.getString("name", " "));
         ItemStack divider = new ItemStack(material);
         ItemMeta meta = divider.getItemMeta();
         if (meta != null) {
            meta.setDisplayName(name);
            divider.setItemMeta(meta);
         }

         for(int i = 0; i < gui.getSize(); ++i) {
            if (gui.getItem(i) == null) {
               gui.setItem(i, divider);
            }
         }


      }
   }

   public List<ConfigurationSection> getConfigItems(ConfigurationSection itemsSection) {
      List<ConfigurationSection> items = new ArrayList();
      if (itemsSection == null) {
         return items;
      } else {
         for(String key : itemsSection.getKeys(false)) {
            ConfigurationSection item = itemsSection.getConfigurationSection(key);
            if (item != null) {
               items.add(item);
            }
         }

         return items;
      }
   }

   private ItemStack createConfigItem(ConfigurationSection itemConfig) {
      Material material = Material.valueOf(itemConfig.getString("material", "STONE"));
      String name = ChatColor.translateAlternateColorCodes('&', itemConfig.getString("name", "\u672a\u547d\u540d\u7269\u54c1"));
      List<String> lore = (List)itemConfig.getStringList("lore").stream().map((line) -> ChatColor.translateAlternateColorCodes('&', line)).collect(Collectors.toList());
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(name);
         meta.setLore(lore);
         item.setItemMeta(meta);
      }

      return item;
   }

   private void addVoteCountToItemLore(ItemStack item, int targetId, String type) {
      if (item != null && item.hasItemMeta()) {
         ItemMeta meta = item.getItemMeta();
         List<String> lore = meta.getLore() != null ? new ArrayList(meta.getLore()) : new ArrayList();
         int count = 0;
         if (type.equals("mode")) {
            count = this.getModeVoteCount(targetId);
         } else if (type.equals("role")) {
            count = this.getRoleVoteCount(targetId);
         } else if (type.equals("type")) {
            count = this.getTypeVoteCount(targetId);
         }

         String currentSelectionFormat = this.voteGuiConfig.getString("lore.current-selection", "&7\u5f53\u524d\u9009\u62e9: %count%");
         currentSelectionFormat = ChatColor.translateAlternateColorCodes('&', currentSelectionFormat).replace("%count%", String.valueOf(count));
         lore.add(currentSelectionFormat);
         meta.setLore(lore);
         item.setItemMeta(meta);
      }
   }

   public void recordPlayerModeChoice(Player player, int mode) {
      if (player != null) {
         this.playerModeChoice.put(player.getUniqueId(), mode);
      }
   }

   public void recordPlayerRoleVote(Player player, int role) {
      if (player != null) {
         this.playerVotes.put(player.getUniqueId(), role);
      }
   }

   public void recordPlayerTypeVote(Player player, int type) {
      if (player != null) {
         this.playerTypeChoice.put(player.getUniqueId(), type);
      }
   }

   public int getModeVoteCount(int mode) {
      return (int)this.playerModeChoice.values().stream().filter((vote) -> vote == mode).count();
   }

   public int getRoleVoteCount(int role) {
      return (int)this.playerVotes.values().stream().filter((vote) -> vote == role).count();
   }

   public int getTypeVoteCount(int type) {
      return (int)this.playerTypeChoice.values().stream().filter((vote) -> vote == type).count();
   }

   public int determineFinalGameMode() {
      return 2;
   }

   public int determineFinalBattleType() {
      int persistenceCount = this.getTypeVoteCount(1);
      int clearanceCount = this.getTypeVoteCount(2);
      return persistenceCount >= clearanceCount ? 1 : 2;
   }

   public Map<UUID, Integer> getPlayerRoleVotes() {
      return new HashMap(this.playerVotes);
   }

   public HunterGame getPlugin() {
      return this.plugin;
   }

   public void resetVoteData() {
      this.playerVotes.clear();
      this.playerTypeChoice.clear();
   }

   public void clearPlayerVote(UUID playerId) {
      this.playerVotes.remove(playerId);
      this.playerTypeChoice.remove(playerId);
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player) {
         Player player = (Player)event.getWhoClicked();
         ItemStack item = event.getCurrentItem();
         InventoryView view = event.getView();
         String guiTitle = ChatColor.translateAlternateColorCodes('&', this.voteGuiConfig.getString("title", "\u6a21\u5f0f\u4e0e\u89d2\u8272\u9009\u62e9"));
         if (view.getTitle().equals(guiTitle)) {
            event.setCancelled(true);
            if (item != null && item.hasItemMeta()) {
               int slot = event.getRawSlot();
               this.handleTypeSelection(player, slot);
               this.handleRoleSelection(player, slot);
            }
         }
      }
   }

   private void handleTypeSelection(Player player, int slot) {
      ConfigurationSection typeSection = this.voteGuiConfig.getConfigurationSection("type-section");
      if (typeSection != null) {
         int baseSlot = typeSection.getInt("slot", 3);
         List<ConfigurationSection> typeItems = this.getConfigItems(typeSection.getConfigurationSection("items"));

         for(int i = 0; i < typeItems.size(); ++i) {
            if (slot == baseSlot + i * 2) {
               ConfigurationSection itemConfig = (ConfigurationSection)typeItems.get(i);
               int typeId = itemConfig.getInt("id");
               String name = ChatColor.translateAlternateColorCodes('&', itemConfig.getString("name"));
               this.recordPlayerTypeVote(player, typeId);
               player.sendMessage(this.plugin.getMessage("choice", "&a\u5df2\u9009\u62e9: %modeName%").replace("%modeName%", name));
               this.playItemSound(player, itemConfig);
               this.refreshGUI(player);
               return;
            }
         }

      }
   }

   private void handleRoleSelection(Player player, int slot) {
      ConfigurationSection roleSection = this.voteGuiConfig.getConfigurationSection("role-section");
      if (roleSection != null) {
         int baseSlot = roleSection.getInt("slot", 21);
         List<ConfigurationSection> roleItems = this.getConfigItems(roleSection.getConfigurationSection("items"));

         for(int i = 0; i < roleItems.size(); ++i) {
            if (slot == baseSlot + i * 2) {
               ConfigurationSection itemConfig = roleItems.get(i);
               int roleId = itemConfig.getInt("id");
               String roleName = ChatColor.translateAlternateColorCodes('&', itemConfig.getString("name", "未知阵营"));
               this.recordPlayerRoleVote(player, roleId);
               String key = roleId == 1 ? "choice_camp_escaper" : "choice_camp_hunter";
               String fallback = roleId == 1 ? "&a已选择阵营: %role%" : "&c已选择阵营: %role%";
               player.sendMessage(this.plugin.getMessage(key, fallback).replace("%role%", roleName));
               this.playItemSound(player, itemConfig);
               player.closeInventory();
               return;
            }
         }
      }
   }

   private void refreshGUI(final Player player) {
      player.closeInventory();
      (new BukkitRunnable() {
         public void run() {
            VoteSystemGUI.openVoteGUI(VoteSystem.this, VoteSystem.this.voteGuiConfig, player);
         }
      }).runTaskLater(this.plugin, 2L);
   }

   public void playItemSound(Player player, ConfigurationSection itemConfig) {
      try {
         String soundName = itemConfig.getString("sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
         Sound sound = Sound.valueOf(soundName);
         float pitch = (float)itemConfig.getDouble("pitch", (double)1.0F);
         player.playSound(player.getLocation(), sound, 1.0F, pitch);
      } catch (IllegalArgumentException var6) {
         this.plugin.getLogger().warning("\u65e0\u6548\u7684\u97f3\u6548\u540d: " + itemConfig.getString("sound"));
      }

   }

   public boolean isSupportedGameMode(int modeId) {
      return modeId == 2;
   }

   public int normalizeGameMode(int modeId) {
      return this.isSupportedGameMode(modeId) ? modeId : 2;
   }

   @EventHandler
   public void onPlayerInteract(PlayerInteractEvent event) {
      if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
         Player player = event.getPlayer();
         ItemStack item = event.getItem();
         if (item != null && item.hasItemMeta()) {
            String persistentKey = this.voteItemConfig.getString("persistent-key", "campaign_vote");
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            if (container.has(new NamespacedKey(this.plugin, persistentKey), PersistentDataType.STRING)) {
               if (BedrockSupport.isBedrockPlayer(this.plugin, player)) {
                  try {
                     BedrockVoteSystemGUI.openBedrockVoteMenu(this, this.voteGuiConfig, this.plugin, player);
                  } catch (Exception var7) {
                     VoteSystemGUI.openVoteGUI(this, this.voteGuiConfig, player);
                  }
               } else {
                  VoteSystemGUI.openVoteGUI(this, this.voteGuiConfig, player);
               }

               event.setCancelled(true);
            }

         }
      }
   }
}
