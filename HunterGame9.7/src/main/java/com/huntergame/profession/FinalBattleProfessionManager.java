package com.huntergame.profession;

import com.huntergame.HunterGame;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class FinalBattleProfessionManager implements Listener {
   private static final String JOBS_FOLDER = "jobs";
   private static final String GUI_CONFIG_PATH = "final_battle.profession_gui";
   private static final String GUI_TITLE = "\u9009\u62e9\u804c\u4e1a";
   private static final String ITEM_EDITOR_TITLE_PREFIX = "\u7f16\u8f91\u804c\u4e1a\u7269\u54c1: ";
   private static final int ITEM_EDITOR_SIZE = 45;
   private static final long SELECTION_GUI_OPEN_DELAY_TICKS = 40L;
   private static final List<String> DEFAULT_JOB_RESOURCES = Arrays.asList("jobs/escaper_archers.yml", "jobs/escaper_assassin.yml", "jobs/escaper_boom.yml", "jobs/escaper_flash_warrior.yml", "jobs/escaper_juggernaut.yml", "jobs/escaper_lurk.yml", "jobs/escaper_mace.yml", "jobs/escaper_pilot.yml", "jobs/escaper_warrior.yml", "jobs/hunter_archers.yml", "jobs/hunter_assassin.yml", "jobs/hunter_boom.yml", "jobs/hunter_flash_warrior.yml", "jobs/hunter_juggernaut.yml", "jobs/hunter_lurk.yml", "jobs/hunter_mace.yml", "jobs/hunter_pilot.yml", "jobs/hunter_warrior.yml", "jobs/hunter_forbidden_mage.yml");
   private final HunterGame plugin;
   private final File jobsFolder;
   private final Map<String, Profession> professions = new LinkedHashMap();
   private final Set<UUID> waitingPlayers = new HashSet();
   private final Map<UUID, String> selectedProfessions = new HashMap();
   private final Map<String, Integer> hunterSelections = new HashMap();
   private final Map<String, Integer> escaperSelections = new HashMap();
   private final Map<UUID, Map<Integer, String>> guiSlotsByPlayer = new HashMap();
   private final Map<UUID, Integer> guiRefreshTasks = new HashMap();
   private final Map<UUID, File> editingJobFiles = new HashMap();

   public FinalBattleProfessionManager(HunterGame plugin) {
      this.plugin = plugin;
      this.jobsFolder = new File(plugin.getDataFolder(), "jobs");
      this.reload();
   }

   public void reload() {
      this.ensureJobsFolder();
      this.professions.clear();
      File[] files = this.jobsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ENGLISH).endsWith(".yml"));
      if (files != null) {
         Arrays.sort(files, Comparator.comparing(File::getName));

         for(File file : files) {
            Profession profession = this.loadProfession(file);
            if (profession != null && profession.enabled) {
               this.professions.put(profession.id, profession);
            }
         }

      }
   }

   public void resetSelections() {
      this.cancelAllGuiRefreshTasks();
      this.waitingPlayers.clear();
      this.selectedProfessions.clear();
      this.hunterSelections.clear();
      this.escaperSelections.clear();
      this.guiSlotsByPlayer.clear();
   }

   public List<String> getProfessionIds() {
      return new ArrayList(this.professions.keySet());
   }

   public boolean saveProfessionFromInventory(Player player, String id, String displayName) {
      if (!this.isValidJobId(id)) {
         player.sendMessage(this.plugin.getMessage("profession_invalid_id", "&c\u804c\u4e1aID\u53ea\u80fd\u5305\u542b\u5b57\u6bcd\u3001\u6570\u5b57\u3001\u4e0b\u5212\u7ebf\u548c\u77ed\u6a2a\u7ebf\u3002"));
         return false;
      } else {
         this.ensureJobsFolder();
         File jobFile = this.getJobFile(id);
         FileConfiguration jobConfig = YamlConfiguration.loadConfiguration(jobFile);
         jobConfig.set("id", id);
         jobConfig.set("display_name", displayName);
         this.setDefault(jobConfig, "enabled", true);
         this.setDefault(jobConfig, "icon", this.getIconMaterial(player).name());
         this.setDefault(jobConfig, "slot", this.getNextFreeSlot(id));
         this.setDefault(jobConfig, "max_per_team", 0);
         this.setDefault(jobConfig, "max_health", (double)20.0F);
         this.setDefault(jobConfig, "role", FinalBattleProfessionManager.ProfessionRole.ALL.configValue);
         this.setDefault(jobConfig, "skill", "");
         if (!jobConfig.contains("potion_effects")) {
            jobConfig.set("potion_effects", this.createDefaultPotionEffectTemplate());
         }

         if (!jobConfig.contains("lore")) {
            List<String> lore = new ArrayList();
            lore.add("&7\u9009\u62e9\u540e\u83b7\u5f97\u8be5\u804c\u4e1a\u914d\u7f6e\u7269\u54c1");
            lore.add("&7\u9002\u7528\u9635\u8425: &e%role%");
            lore.add("&7\u804c\u4e1a\u6280\u80fd: &e%skill%");
            lore.add("&7\u6bcf\u961f\u6700\u591a: &e%limit%");
            lore.add("&7\u5f53\u524d\u961f\u4f0d\u5df2\u9009\u62e9: &b%selected%");
            jobConfig.set("lore", lore);
         }

         jobConfig.set("items", (Object)null);
         jobConfig.set("equipment", (Object)null);
         int saved = this.saveInventoryItems(player.getInventory(), jobConfig);
         saved += this.saveEquipmentItems(player.getInventory(), jobConfig);

         try {
            jobConfig.save(jobFile);
            this.reload();
            player.sendMessage(this.plugin.getMessage("profession_saved", "&a\u5df2\u4fdd\u5b58\u7ec8\u7ae0\u804c\u4e1a &e%id% &a\u5230 jobs/%file%\uff0c\u7269\u54c1\u6570\u91cf: %count%").replace("%id%", id).replace("%file%", id + ".yml").replace("%count%", String.valueOf(saved)));
            return true;
         } catch (IOException e) {
            this.plugin.getLogger().severe("\u4fdd\u5b58\u804c\u4e1a " + id + " \u5931\u8d25: " + e.getMessage());
            player.sendMessage(this.plugin.getMessage("profession_save_failed", "&c\u4fdd\u5b58\u804c\u4e1a\u914d\u7f6e\u5931\u8d25\uff0c\u8bf7\u67e5\u770b\u63a7\u5236\u53f0\u3002"));
            return false;
         }
      }
   }

   public boolean openProfessionItemEditor(Player player, String id) {
      if (!this.isValidJobId(id)) {
         player.sendMessage(this.plugin.getMessage("profession_invalid_id", "&c\u804c\u4e1aID\u53ea\u80fd\u5305\u542b\u5b57\u6bcd\u3001\u6570\u5b57\u3001\u4e0b\u5212\u7ebf\u548c\u77ed\u6a2a\u7ebf\u3002"));
         return false;
      } else {
         this.ensureJobsFolder();
         File jobFile = this.findExistingJobFile(id);
         if (jobFile == null) {
            player.sendMessage(this.plugin.getMessage("profession_not_found", "&c\u672a\u627e\u5230\u804c\u4e1a\u914d\u7f6e: %id%").replace("%id%", id));
            return false;
         } else {
            FileConfiguration jobConfig = YamlConfiguration.loadConfiguration(jobFile);
            Inventory inventory = Bukkit.createInventory((InventoryHolder)null, 45, this.color("&8\u7f16\u8f91\u804c\u4e1a\u7269\u54c1: " + id));
            this.loadProfessionItemsToEditor(inventory, jobConfig);
            this.editingJobFiles.put(player.getUniqueId(), jobFile);
            player.openInventory(inventory);
            return true;
         }
      }
   }

   public void startSelection(Collection<Player> players) {
      this.resetSelections();
      if (this.professions.isEmpty()) {
         this.plugin.getLogger().warning("jobs \u6587\u4ef6\u5939\u4e2d\u6ca1\u6709\u53ef\u7528\u7ec8\u7ae0\u804c\u4e1a\uff0c\u5df2\u8df3\u8fc7\u804c\u4e1a\u9009\u62e9\u3002");
      } else {
         for(Player player : players) {
            if (player != null && player.isOnline() && this.isFinalBattlePlayer(player)) {
               if (!this.hasAvailableProfession(player)) {
                  player.sendMessage(this.plugin.getMessage("profession_no_available_for_role", "&c\u5f53\u524d\u6ca1\u6709\u9002\u5408\u4f60\u9635\u8425\u7684\u7ec8\u7ae0\u804c\u4e1a\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\u68c0\u67e5 jobs \u914d\u7f6e\u3002"));
                  Logger var10000 = this.plugin.getLogger();
                  String var10001 = player.getName();
                  var10000.warning("\u73a9\u5bb6 " + var10001 + " \u6ca1\u6709\u53ef\u9009\u7ec8\u7ae0\u804c\u4e1a\uff0c\u9635\u8425: " + this.getPlayerRoleName(player));
               } else {
                  this.waitingPlayers.add(player.getUniqueId());
                  Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.openSelectionGui(player), 40L);
               }
            }
         }

      }
   }

   public boolean hasSelected(Player player) {
      return this.selectedProfessions.containsKey(player.getUniqueId());
   }

   public boolean giveSelectedProfessionLoadout(Player player) {
      if (player == null) {
         return false;
      } else {
         String professionId = (String)this.selectedProfessions.get(player.getUniqueId());
         Profession profession = (Profession)this.professions.get(professionId);
         if (profession == null) {
            this.plugin.getLogger().warning("\u65e0\u6cd5\u91cd\u65b0\u53d1\u653e\u7ec8\u7ae0\u804c\u4e1a\u7269\u54c1\uff0c\u73a9\u5bb6\u672a\u9009\u62e9\u804c\u4e1a\u6216\u804c\u4e1a\u5df2\u4e0d\u5b58\u5728: " + player.getName());
            return false;
         } else {
            this.applyMaxHealth(player, profession);
            this.giveProfessionItems(player, profession);
            this.giveProfessionSkill(player, profession);
            this.applyPotionEffects(player, profession);
            return true;
         }
      }
   }

   private Profession loadProfession(File file) {
      FileConfiguration jobConfig = YamlConfiguration.loadConfiguration(file);
      String fallbackId = this.stripYamlExtension(file.getName());
      String id = jobConfig.getString("id", fallbackId);
      if (!this.isValidJobId(id)) {
         this.plugin.getLogger().warning("\u5ffd\u7565\u65e0\u6548\u804c\u4e1a\u6587\u4ef6: " + file.getName());
         return null;
      } else {
         String displayName = this.color(jobConfig.getString("display_name", id));
         Material icon = this.parseMaterial(jobConfig.getString("icon", "CHEST"), Material.CHEST);
         int slot = jobConfig.getInt("slot", -1);
         int maxPerTeam = jobConfig.getInt("max_per_team", 0);
         double maxHealth = Math.max((double)1.0F, jobConfig.getDouble("max_health", (double)20.0F));
         ProfessionRole role = this.parseProfessionRole(jobConfig, id);
         String skill = this.color(jobConfig.getString("skill", ""));
         boolean enabled = jobConfig.getBoolean("enabled", true);
         List<String> lore = jobConfig.getStringList("lore");
         Map<Integer, ItemStack> items = this.loadItems(jobConfig.getConfigurationSection("items"));
         Map<String, ItemStack> equipment = this.loadEquipment(jobConfig.getConfigurationSection("equipment"));
         List<ProfessionPotionEffect> potionEffects = this.loadPotionEffects(jobConfig, id);
         return new Profession(id, displayName, icon, slot, maxPerTeam, maxHealth, role, skill, enabled, lore, items, equipment, potionEffects);
      }
   }

   private Map<Integer, ItemStack> loadItems(ConfigurationSection section) {
      Map<Integer, ItemStack> items = new HashMap();
      if (section == null) {
         return items;
      } else {
         for(String key : section.getKeys(false)) {
            try {
               int slot = Integer.parseInt(key);
               ItemStack item = section.getItemStack(key + ".item");
               if (item != null && item.getType() != Material.AIR) {
                  items.put(slot, item);
               }
            } catch (NumberFormatException var7) {
            }
         }

         return items;
      }
   }

   private Map<String, ItemStack> loadEquipment(ConfigurationSection section) {
      Map<String, ItemStack> equipment = new HashMap();
      if (section == null) {
         return equipment;
      } else {
         for(String key : section.getKeys(false)) {
            ItemStack item = section.getItemStack(key + ".item");
            if (item != null && item.getType() != Material.AIR) {
               equipment.put(key.toLowerCase(Locale.ENGLISH), item);
            }
         }

         return equipment;
      }
   }

   private void openSelectionGui(Player player) {
      if (this.waitingPlayers.contains(player.getUniqueId()) && this.isFinalBattlePlayer(player)) {
         int rows = Math.max(1, Math.min(6, this.plugin.getConfig().getInt("final_battle.profession_gui.rows", 3)));
         String title = this.color(this.plugin.getConfig().getString("final_battle.profession_gui.title", "\u9009\u62e9\u804c\u4e1a"));
         Inventory inventory = Bukkit.createInventory((InventoryHolder)null, rows * 9, title);
         if (this.renderSelectionGui(player, inventory)) {
            player.openInventory(inventory);
            this.startGuiRefreshTask(player);
         }
      }
   }

   private boolean renderSelectionGui(Player player, Inventory inventory) {
      if (this.waitingPlayers.contains(player.getUniqueId()) && this.isFinalBattlePlayer(player)) {
         inventory.clear();
         Map<Integer, String> slotMap = new HashMap();
         int nextSlot = 0;

         for(Profession profession : this.professions.values()) {
            if (this.isRoleAllowed(player, profession)) {
               int slot = profession.slot >= 0 ? profession.slot : this.nextFreeGuiSlot(inventory, nextSlot);
               nextSlot = slot + 1;
               if (slot >= 0 && slot < inventory.getSize()) {
                  inventory.setItem(slot, this.createGuiItem(profession, player));
                  slotMap.put(slot, profession.id);
               }
            }
         }

         if (slotMap.isEmpty()) {
            this.waitingPlayers.remove(player.getUniqueId());
            this.guiSlotsByPlayer.remove(player.getUniqueId());
            this.cancelGuiRefreshTask(player.getUniqueId());
            player.sendMessage(this.plugin.getMessage("profession_no_available_for_role", "&c\u5f53\u524d\u6ca1\u6709\u9002\u5408\u4f60\u9635\u8425\u7684\u7ec8\u7ae0\u804c\u4e1a\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\u68c0\u67e5 jobs \u914d\u7f6e\u3002"));
            Logger var10000 = this.plugin.getLogger();
            String var10001 = player.getName();
            var10000.warning("\u73a9\u5bb6 " + var10001 + " \u6253\u5f00\u804c\u4e1a GUI \u65f6\u6ca1\u6709\u53ef\u9009\u804c\u4e1a\uff0c\u9635\u8425: " + this.getPlayerRoleName(player));
            return false;
         } else {
            this.guiSlotsByPlayer.put(player.getUniqueId(), slotMap);
            return true;
         }
      } else {
         return false;
      }
   }

   private void startGuiRefreshTask(Player player) {
      UUID uuid = player.getUniqueId();
      this.cancelGuiRefreshTask(uuid);
      int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, () -> this.refreshOpenSelectionGui(player), 20L, 20L);
      this.guiRefreshTasks.put(uuid, taskId);
   }

   private void refreshOpenSelectionGui(Player player) {
      UUID uuid = player.getUniqueId();
      if (player.isOnline() && this.waitingPlayers.contains(uuid) && this.isFinalBattlePlayer(player)) {
         if (this.isProfessionGui(player.getOpenInventory().getTitle())) {
            Inventory inventory = player.getOpenInventory().getTopInventory();
            if (!this.renderSelectionGui(player, inventory)) {
               player.closeInventory();
            } else {
               player.updateInventory();
            }
         }
      } else {
         this.cancelGuiRefreshTask(uuid);
      }
   }

   private void cancelGuiRefreshTask(UUID uuid) {
      Integer taskId = (Integer)this.guiRefreshTasks.remove(uuid);
      if (taskId != null) {
         Bukkit.getScheduler().cancelTask(taskId);
      }

   }

   private void cancelAllGuiRefreshTasks() {
      for(Integer taskId : this.guiRefreshTasks.values()) {
         Bukkit.getScheduler().cancelTask(taskId);
      }

      this.guiRefreshTasks.clear();
   }

   private ItemStack createGuiItem(Profession profession, Player player) {
      ItemStack item = new ItemStack(profession.icon);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(profession.displayName);
         List<String> lore = new ArrayList();
         int selected = this.getSelectionCount(player, profession.id);
         int max = profession.maxPerTeam;

         for(String line : profession.lore) {
            lore.add(this.color(line).replace("%limit%", max <= 0 ? "\u4e0d\u9650" : String.valueOf(max)).replace("%selected%", String.valueOf(selected)).replace("%role%", profession.role.displayName).replace("%skill%", profession.skill.isEmpty() ? "\u65e0" : profession.skill));
         }

         if (!lore.isEmpty()) {
            lore.add("");
         }

         meta.setLore(lore);
         item.setItemMeta(meta);
      }

      return item;
   }

   private void loadProfessionItemsToEditor(Inventory inventory, FileConfiguration jobConfig) {
      inventory.clear();
      this.setEditorItem(inventory, 0, jobConfig.getItemStack("equipment.helmet.item"));
      this.setEditorItem(inventory, 1, jobConfig.getItemStack("equipment.chestplate.item"));
      this.setEditorItem(inventory, 2, jobConfig.getItemStack("equipment.leggings.item"));
      this.setEditorItem(inventory, 3, jobConfig.getItemStack("equipment.boots.item"));
      this.setEditorItem(inventory, 4, jobConfig.getItemStack("equipment.offhand.item"));

      for(int inventorySlot = 0; inventorySlot < 36; ++inventorySlot) {
         ItemStack item = jobConfig.getItemStack("items." + inventorySlot + ".item");
         this.setEditorItem(inventory, this.inventorySlotToEditorSlot(inventorySlot), item);
      }

   }

   private void setEditorItem(Inventory inventory, int slot, ItemStack item) {
      if (slot >= 0 && slot < inventory.getSize()) {
         inventory.setItem(slot, this.cloneItem(item));
      }
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player) {
         Player player = (Player)event.getWhoClicked();
         if (this.editingJobFiles.containsKey(player.getUniqueId())) {
            this.handleProfessionItemEditorClick(event);
         } else if (!this.isProfessionGui(event.getView().getTitle())) {
            if (this.waitingPlayers.contains(player.getUniqueId())) {
               event.setCancelled(true);
            }

         } else {
            event.setCancelled(true);
            if (this.waitingPlayers.contains(player.getUniqueId())) {
               Profession profession = this.findProfessionBySlot(player, event.getRawSlot());
               if (profession != null) {
                  this.selectProfession(player, profession);
               }
            }
         }
      }
   }

   private void handleProfessionItemEditorClick(InventoryClickEvent event) {
      if (event.isShiftClick()) {
         event.setCancelled(true);
      } else {
         int rawSlot = event.getRawSlot();
         if (rawSlot >= 0 && rawSlot < event.getView().getTopInventory().getSize() && this.isLockedEditorSlot(rawSlot)) {
            event.setCancelled(true);
         }

      }
   }

   @EventHandler
   public void onInventoryDrag(InventoryDragEvent event) {
      if (event.getWhoClicked() instanceof Player) {
         Player player = (Player)event.getWhoClicked();
         if (this.editingJobFiles.containsKey(player.getUniqueId())) {
            int topSize = event.getView().getTopInventory().getSize();

            for(int rawSlot : event.getRawSlots()) {
               if (rawSlot >= 0 && rawSlot < topSize && this.isLockedEditorSlot(rawSlot)) {
                  event.setCancelled(true);
                  return;
               }
            }

         }
      }
   }

   private void saveProfessionItemEditor(Player player, Inventory inventory, File jobFile) {
      FileConfiguration jobConfig = YamlConfiguration.loadConfiguration(jobFile);
      jobConfig.set("items", (Object)null);
      jobConfig.set("equipment", (Object)null);
      int saved = 0;
      saved += this.saveEditorEquipmentItem(jobConfig, "helmet", inventory.getItem(0));
      saved += this.saveEditorEquipmentItem(jobConfig, "chestplate", inventory.getItem(1));
      saved += this.saveEditorEquipmentItem(jobConfig, "leggings", inventory.getItem(2));
      saved += this.saveEditorEquipmentItem(jobConfig, "boots", inventory.getItem(3));
      saved += this.saveEditorEquipmentItem(jobConfig, "offhand", inventory.getItem(4));

      for(int inventorySlot = 0; inventorySlot < 36; ++inventorySlot) {
         ItemStack item = inventory.getItem(this.inventorySlotToEditorSlot(inventorySlot));
         if (item != null && item.getType() != Material.AIR) {
            jobConfig.set("items." + inventorySlot + ".item", item.clone());
            ++saved;
         }
      }

      try {
         jobConfig.save(jobFile);
         this.reload();
         player.sendMessage(this.plugin.getMessage("profession_items_saved", "&a\u5df2\u4fdd\u5b58\u804c\u4e1a\u7269\u54c1\u5230 jobs/%file%\uff0c\u7269\u54c1\u6570\u91cf: %count%").replace("%file%", jobFile.getName()).replace("%count%", String.valueOf(saved)));
      } catch (IOException e) {
         Logger var10000 = this.plugin.getLogger();
         String var10001 = jobFile.getName();
         var10000.severe("\u4fdd\u5b58\u804c\u4e1a\u7269\u54c1\u5931\u8d25: " + var10001 + " - " + e.getMessage());
         player.sendMessage(this.plugin.getMessage("profession_items_save_failed", "&c\u4fdd\u5b58\u804c\u4e1a\u7269\u54c1\u5931\u8d25\uff0c\u8bf7\u67e5\u770b\u63a7\u5236\u53f0\u3002"));
      }

   }

   private int saveEditorEquipmentItem(FileConfiguration jobConfig, String slot, ItemStack item) {
      if (item != null && item.getType() != Material.AIR) {
         jobConfig.set("equipment." + slot + ".item", item.clone());
         return 1;
      } else {
         return 0;
      }
   }

   private boolean isLockedEditorSlot(int slot) {
      return slot >= 5 && slot <= 8;
   }

   private int inventorySlotToEditorSlot(int inventorySlot) {
      if (inventorySlot >= 0 && inventorySlot <= 8) {
         return 36 + inventorySlot;
      } else {
         return inventorySlot >= 9 && inventorySlot <= 35 ? inventorySlot : -1;
      }
   }

   @EventHandler
   public void onInventoryClose(InventoryCloseEvent event) {
      if (event.getPlayer() instanceof Player) {
         Player player = (Player)event.getPlayer();
         File editingJobFile = (File)this.editingJobFiles.remove(player.getUniqueId());
         if (editingJobFile != null) {
            this.saveProfessionItemEditor(player, event.getInventory(), editingJobFile);
         } else if (this.isProfessionGui(event.getView().getTitle()) && this.waitingPlayers.contains(player.getUniqueId())) {
            this.cancelGuiRefreshTask(player.getUniqueId());
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
               if (this.waitingPlayers.contains(player.getUniqueId()) && player.isOnline() && this.isFinalBattlePlayer(player)) {
                  player.sendMessage(this.plugin.getMessage("profession_must_choose", "&c\u4f60\u5fc5\u987b\u9009\u62e9\u4e00\u4e2a\u7ec8\u7ae0\u804c\u4e1a\u624d\u80fd\u7ee7\u7eed\u6e38\u620f\uff01"));
                  this.openSelectionGui(player);
               }

            }, 1L);
         }
      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      UUID uuid = event.getPlayer().getUniqueId();
      this.waitingPlayers.remove(uuid);
      this.guiSlotsByPlayer.remove(uuid);
      this.cancelGuiRefreshTask(uuid);
      this.editingJobFiles.remove(uuid);
   }

   private void selectProfession(Player player, Profession profession) {
      if (!this.isRoleAllowed(player, profession)) {
         player.sendMessage(this.plugin.getMessage("profession_role_only", "&c\u8be5\u804c\u4e1a\u4ec5\u9650 %role% \u9009\u62e9\u3002").replace("%role%", profession.role.displayName));
         this.refreshOpenSelectionGui(player);
      } else if (!this.canSelect(player, profession)) {
         player.sendMessage(this.plugin.getMessage("profession_team_limit_reached", "&c\u8be5\u804c\u4e1a\u5728\u4f60\u7684\u961f\u4f0d\u4e2d\u5df2\u8fbe\u5230\u9009\u62e9\u4e0a\u9650\uff01"));
         this.refreshOpenSelectionGui(player);
      } else {
         this.addSelection(player, profession.id);
         this.selectedProfessions.put(player.getUniqueId(), profession.id);
         this.waitingPlayers.remove(player.getUniqueId());
         this.guiSlotsByPlayer.remove(player.getUniqueId());
         this.cancelGuiRefreshTask(player.getUniqueId());
         this.applyMaxHealth(player, profession);
         this.giveProfessionItems(player, profession);
         this.giveProfessionSkill(player, profession);
         this.applyPotionEffects(player, profession);
         player.sendMessage(this.plugin.getMessage("profession_selected", "&a\u4f60\u9009\u62e9\u4e86\u7ec8\u7ae0\u804c\u4e1a: %profession%").replace("%profession%", profession.displayName));
         player.closeInventory();
      }
   }

   private void giveProfessionSkill(Player player, Profession profession) {
      String skill = ChatColor.stripColor(profession.skill).trim();
      if (!skill.isEmpty()) {
         if (!this.plugin.getSkillManager().isSkillConfigured(skill)) {
            player.sendMessage(this.plugin.getMessage("profession_skill_invalid", "&c\u804c\u4e1a\u6280\u80fd\u914d\u7f6e\u65e0\u6548: %skill%").replace("%skill%", skill));
            this.plugin.getLogger().warning("\u804c\u4e1a " + profession.id + " \u914d\u7f6e\u4e86\u65e0\u6548\u6280\u80fd: " + skill);
         } else if (!this.plugin.getSkillManager().isSkillEnabled(skill)) {
            player.sendMessage(this.plugin.getMessage("profession_skill_disabled", "&c\u8be5\u804c\u4e1a\u6280\u80fd\u5df2\u7981\u7528: %skill%").replace("%skill%", skill));
            this.plugin.getLogger().warning("\u804c\u4e1a " + profession.id + " \u914d\u7f6e\u7684\u6280\u80fd\u5df2\u7981\u7528: " + skill);
         } else {
            this.plugin.getSkillManager().confirmSkillSelection(player, skill);
         }
      }
   }

   private void applyMaxHealth(Player player, Profession profession) {
      if (player.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
         player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(profession.maxHealth);
         player.setHealth(Math.min(profession.maxHealth, player.getMaxHealth()));
      }
   }

   private void giveProfessionItems(Player player, Profession profession) {
      PlayerInventory inventory = player.getInventory();
      inventory.clear();
      player.getEquipment().clear();
      inventory.setItemInOffHand(new ItemStack(Material.AIR));

      for(Map.Entry<Integer, ItemStack> entry : profession.items.entrySet()) {
         ItemStack item = ((ItemStack)entry.getValue()).clone();
         int slot = (Integer)entry.getKey();
         ItemStack current = slot >= 0 && slot < inventory.getStorageContents().length ? inventory.getItem(slot) : null;
         if (current != null && current.getType() != Material.AIR) {
            inventory.addItem(new ItemStack[]{item}).values().forEach((leftover) -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
         } else {
            inventory.setItem(slot, item);
         }
      }

      this.giveProfessionEquipment(inventory, profession.equipment);
   }

   private void giveProfessionEquipment(PlayerInventory inventory, Map<String, ItemStack> equipment) {
      Objects.requireNonNull(inventory);
      this.setEquipmentItem(equipment, "helmet", inventory::setHelmet);
      Objects.requireNonNull(inventory);
      this.setEquipmentItem(equipment, "chestplate", inventory::setChestplate);
      Objects.requireNonNull(inventory);
      this.setEquipmentItem(equipment, "leggings", inventory::setLeggings);
      Objects.requireNonNull(inventory);
      this.setEquipmentItem(equipment, "boots", inventory::setBoots);
      Objects.requireNonNull(inventory);
      this.setEquipmentItem(equipment, "offhand", inventory::setItemInOffHand);
   }

   private void setEquipmentItem(Map<String, ItemStack> equipment, String key, Consumer<ItemStack> setter) {
      ItemStack item = (ItemStack)equipment.get(key);
      if (item != null && item.getType() != Material.AIR) {
         setter.accept(item.clone());
      }

   }

   private void applyPotionEffects(Player player, Profession profession) {
      for(ProfessionPotionEffect effect : profession.potionEffects) {
         player.addPotionEffect(new PotionEffect(effect.type, effect.durationTicks, effect.amplifier, effect.ambient, effect.particles, effect.icon), true);
      }

   }

   private int saveInventoryItems(PlayerInventory inventory, FileConfiguration jobConfig) {
      int saved = 0;
      ItemStack[] contents = inventory.getStorageContents();

      for(int slot = 0; slot < contents.length; ++slot) {
         ItemStack item = contents[slot];
         if (item != null && item.getType() != Material.AIR) {
            jobConfig.set("items." + slot + ".item", item.clone());
            ++saved;
         }
      }

      return saved;
   }

   private int saveEquipmentItems(PlayerInventory inventory, FileConfiguration jobConfig) {
      int saved = 0;
      saved += this.saveEquipmentItem(jobConfig, "helmet", inventory.getHelmet());
      saved += this.saveEquipmentItem(jobConfig, "chestplate", inventory.getChestplate());
      saved += this.saveEquipmentItem(jobConfig, "leggings", inventory.getLeggings());
      saved += this.saveEquipmentItem(jobConfig, "boots", inventory.getBoots());
      saved += this.saveEquipmentItem(jobConfig, "offhand", inventory.getItemInOffHand());
      return saved;
   }

   private int saveEquipmentItem(FileConfiguration jobConfig, String slot, ItemStack item) {
      if (item != null && item.getType() != Material.AIR) {
         jobConfig.set("equipment." + slot + ".item", item.clone());
         return 1;
      } else {
         return 0;
      }
   }

   private List<Map<String, Object>> createDefaultPotionEffectTemplate() {
      return new ArrayList();
   }

   private List<ProfessionPotionEffect> loadPotionEffects(FileConfiguration jobConfig, String professionId) {
      List<ProfessionPotionEffect> effects = new ArrayList();
      List<?> rawEffects = jobConfig.getList("potion_effects");
      if (rawEffects == null) {
         return effects;
      } else {
         for(Object rawEffect : rawEffects) {
            ProfessionPotionEffect effect = this.parsePotionEffect(rawEffect, professionId);
            if (effect != null) {
               effects.add(effect);
            }
         }

         return effects;
      }
   }

   private ProfessionPotionEffect parsePotionEffect(Object rawEffect, String professionId) {
      if (rawEffect instanceof Map) {
         return this.parsePotionEffectMap((Map)rawEffect, professionId);
      } else if (rawEffect instanceof String) {
         return this.parsePotionEffectString((String)rawEffect, professionId);
      } else {
         this.plugin.getLogger().warning("\u804c\u4e1a " + professionId + " \u5b58\u5728\u65e0\u6548\u836f\u6c34\u6548\u679c\u914d\u7f6e: " + String.valueOf(rawEffect));
         return null;
      }
   }

   private ProfessionPotionEffect parsePotionEffectMap(Map<?, ?> map, String professionId) {
      if (!this.readBoolean(map, "enabled", true)) {
         return null;
      } else {
         Object rawType = map.get("type");
         String typeName = rawType == null ? "" : String.valueOf(rawType).trim();
         if (typeName.isEmpty()) {
            return null;
         } else if (typeName.contains(":")) {
            return this.parsePotionEffectSpec(typeName, professionId);
         } else {
            PotionEffectType type = this.resolvePotionEffectType(typeName);
            if (type == null) {
               this.plugin.getLogger().warning("\u804c\u4e1a " + professionId + " \u914d\u7f6e\u4e86\u65e0\u6548\u836f\u6c34\u6548\u679c: " + typeName);
               return null;
            } else {
               int amplifier = this.readInt(map, "amplifier", 0);
               boolean permanent = this.readBoolean(map, "permanent", true);
               int durationSeconds = this.readInt(map, "duration", 0);
               int durationTicks = !permanent && durationSeconds > 0 ? durationSeconds * 20 : Integer.MAX_VALUE;
               boolean ambient = this.readBoolean(map, "ambient", true);
               boolean particles = this.readBoolean(map, "particles", false);
               boolean icon = this.readBoolean(map, "icon", true);
               return new ProfessionPotionEffect(type, amplifier, durationTicks, ambient, particles, icon);
            }
         }
      }
   }

   private ProfessionPotionEffect parsePotionEffectString(String rawEffect, String professionId) {
      return this.parsePotionEffectSpec(rawEffect, professionId);
   }

   private ProfessionPotionEffect parsePotionEffectSpec(String rawEffect, String professionId) {
      String[] parts = rawEffect.split(":");
      if (parts.length == 0) {
         return null;
      } else {
         String typeName = parts[0].trim();
         if (typeName.isEmpty()) {
            return null;
         } else {
            PotionEffectType type = this.resolvePotionEffectType(typeName);
            if (type == null) {
               this.plugin.getLogger().warning("\u804c\u4e1a " + professionId + " \u914d\u7f6e\u4e86\u65e0\u6548\u836f\u6c34\u6548\u679c: " + rawEffect);
               return null;
            } else {
               int durationSeconds = parts.length > 1 ? this.parseInt(parts[1], -1) : -1;
               int amplifier = parts.length > 2 ? this.parseInt(parts[2], 0) : 0;
               int durationTicks = durationSeconds < 0 ? Integer.MAX_VALUE : durationSeconds * 20;
               return new ProfessionPotionEffect(type, amplifier, durationTicks, true, false, true);
            }
         }
      }
   }

   private PotionEffectType resolvePotionEffectType(String typeName) {
      if (typeName == null) {
         return null;
      } else {
         String keyText = typeName.trim().toLowerCase(Locale.ENGLISH).replace(' ', '_');
         if (keyText.isEmpty()) {
            return null;
         } else {
            try {
               NamespacedKey key = keyText.contains(":") ? NamespacedKey.fromString(keyText) : NamespacedKey.minecraft(keyText);
               return key == null ? null : (PotionEffectType)Registry.EFFECT.get(key);
            } catch (IllegalArgumentException var4) {
               return null;
            }
         }
      }
   }

   private int readInt(Map<?, ?> map, String key, int defaultValue) {
      Object value = map.get(key);
      if (value instanceof Number) {
         return ((Number)value).intValue();
      } else {
         return value == null ? defaultValue : this.parseInt(String.valueOf(value), defaultValue);
      }
   }

   private boolean readBoolean(Map<?, ?> map, String key, boolean defaultValue) {
      Object value = map.get(key);
      if (value instanceof Boolean) {
         return (Boolean)value;
      } else {
         return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
      }
   }

   private int parseInt(String value, int defaultValue) {
      try {
         return Integer.parseInt(value.trim());
      } catch (NumberFormatException var4) {
         return defaultValue;
      }
   }

   private boolean canSelect(Player player, Profession profession) {
      return profession.maxPerTeam <= 0 || this.getSelectionCount(player, profession.id) < profession.maxPerTeam;
   }

   private boolean hasAvailableProfession(Player player) {
      for(Profession profession : this.professions.values()) {
         if (this.isRoleAllowed(player, profession)) {
            return true;
         }
      }

      return false;
   }

   private boolean isRoleAllowed(Player player, Profession profession) {
      UUID uuid = player.getUniqueId();
      switch (profession.role.ordinal()) {
         case 0:
         default:
            return true;
         case 1:
            return this.plugin.isHunter(uuid);
         case 2:
            return this.plugin.isEscaper(uuid);
      }
   }

   private String getPlayerRoleName(Player player) {
      UUID uuid = player.getUniqueId();
      if (this.plugin.isHunter(uuid)) {
         return FinalBattleProfessionManager.ProfessionRole.HUNTER.displayName;
      } else {
         return this.plugin.isEscaper(uuid) ? FinalBattleProfessionManager.ProfessionRole.ESCAPER.displayName : "\u672a\u77e5";
      }
   }

   private void addSelection(Player player, String professionId) {
      Map<String, Integer> selections = this.plugin.isHunter(player.getUniqueId()) ? this.hunterSelections : this.escaperSelections;
      selections.put(professionId, (Integer)selections.getOrDefault(professionId, 0) + 1);
   }

   private int getSelectionCount(Player player, String professionId) {
      Map<String, Integer> selections = this.plugin.isHunter(player.getUniqueId()) ? this.hunterSelections : this.escaperSelections;
      return (Integer)selections.getOrDefault(professionId, 0);
   }

   private Profession findProfessionBySlot(Player player, int rawSlot) {
      if (rawSlot < 0) {
         return null;
      } else {
         Map<Integer, String> slots = (Map)this.guiSlotsByPlayer.get(player.getUniqueId());
         return slots == null ? null : (Profession)this.professions.get(slots.get(rawSlot));
      }
   }

   private boolean isFinalBattlePlayer(Player player) {
      UUID uuid = player.getUniqueId();
      return this.plugin.isFinalBattleMode() && (this.plugin.isHunter(uuid) || this.plugin.isEscaper(uuid));
   }

   private boolean isProfessionGui(String title) {
      return this.color(this.plugin.getConfig().getString("final_battle.profession_gui.title", "\u9009\u62e9\u804c\u4e1a")).equals(title);
   }

   private int nextFreeGuiSlot(Inventory inventory, int startSlot) {
      for(int slot = startSlot; slot < inventory.getSize(); ++slot) {
         if (inventory.getItem(slot) == null) {
            return slot;
         }
      }

      return -1;
   }

   private int getNextFreeSlot(String currentId) {
      Set<Integer> usedSlots = new HashSet();

      for(Profession profession : this.professions.values()) {
         if (!profession.id.equals(currentId)) {
            usedSlots.add(profession.slot);
         }
      }

      for(int slot = 10; slot <= 16; ++slot) {
         if (!usedSlots.contains(slot)) {
            return slot;
         }
      }

      return Math.max(0, this.professions.size());
   }

   private File getJobFile(String id) {
      return new File(this.jobsFolder, id + ".yml");
   }

   private File findExistingJobFile(String id) {
      File directFile = this.getJobFile(id);
      if (directFile.exists()) {
         return directFile;
      } else {
         File[] files = this.jobsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ENGLISH).endsWith(".yml"));
         if (files == null) {
            return null;
         } else {
            Arrays.sort(files, Comparator.comparing(File::getName));

            for(File file : files) {
               FileConfiguration jobConfig = YamlConfiguration.loadConfiguration(file);
               String fileId = this.stripYamlExtension(file.getName());
               String configId = jobConfig.getString("id", fileId);
               if (id.equalsIgnoreCase(fileId) || id.equalsIgnoreCase(configId)) {
                  return file;
               }
            }

            return null;
         }
      }
   }

   private Material getIconMaterial(Player player) {
      ItemStack mainHand = player.getInventory().getItemInMainHand();
      return mainHand != null && mainHand.getType() != Material.AIR ? mainHand.getType() : Material.CHEST;
   }

   private Material parseMaterial(String value, Material defaultMaterial) {
      if (value == null) {
         return defaultMaterial;
      } else {
         try {
            return Material.valueOf(value.toUpperCase(Locale.ENGLISH));
         } catch (IllegalArgumentException var4) {
            return defaultMaterial;
         }
      }
   }

   private ProfessionRole parseProfessionRole(FileConfiguration jobConfig, String professionId) {
      String rawRole = jobConfig.getString("role", jobConfig.getString("team", jobConfig.getString("category", FinalBattleProfessionManager.ProfessionRole.ALL.configValue)));
      ProfessionRole role = FinalBattleProfessionManager.ProfessionRole.fromConfig(rawRole);
      if (role == null) {
         this.plugin.getLogger().warning("\u804c\u4e1a " + professionId + " \u914d\u7f6e\u4e86\u65e0\u6548\u9002\u7528\u9635\u8425: " + rawRole + "\uff0c\u5df2\u6309 all \u5904\u7406\u3002");
         return FinalBattleProfessionManager.ProfessionRole.ALL;
      } else {
         return role;
      }
   }

   private boolean isValidJobId(String id) {
      return id != null && id.matches("[A-Za-z0-9_-]+");
   }

   private String stripYamlExtension(String fileName) {
      int dotIndex = fileName.lastIndexOf(46);
      return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
   }

   private String color(String text) {
      return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
   }

   private ItemStack cloneItem(ItemStack item) {
      return item == null ? null : item.clone();
   }

   private void setDefault(FileConfiguration config, String path, Object value) {
      if (!config.contains(path)) {
         config.set(path, value);
      }

   }

   private void ensureJobsFolder() {
      if (!this.jobsFolder.exists()) {
         this.jobsFolder.mkdirs();
      }

      if (!this.hasJobFiles()) {
         for(String resource : DEFAULT_JOB_RESOURCES) {
            this.plugin.saveResource(resource, false);
         }
      }

   }

   private boolean hasJobFiles() {
      File[] files = this.jobsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ENGLISH).endsWith(".yml"));
      return files != null && files.length > 0;
   }

   private static final class Profession {
      private final String id;
      private final String displayName;
      private final Material icon;
      private final int slot;
      private final int maxPerTeam;
      private final double maxHealth;
      private final ProfessionRole role;
      private final String skill;
      private final boolean enabled;
      private final List<String> lore;
      private final Map<Integer, ItemStack> items;
      private final Map<String, ItemStack> equipment;
      private final List<ProfessionPotionEffect> potionEffects;

      private Profession(String id, String displayName, Material icon, int slot, int maxPerTeam, double maxHealth, ProfessionRole role, String skill, boolean enabled, List<String> lore, Map<Integer, ItemStack> items, Map<String, ItemStack> equipment, List<ProfessionPotionEffect> potionEffects) {
         this.id = id;
         this.displayName = displayName;
         this.icon = icon;
         this.slot = slot;
         this.maxPerTeam = maxPerTeam;
         this.maxHealth = maxHealth;
         this.role = role;
         this.skill = skill;
         this.enabled = enabled;
         this.lore = lore;
         this.items = items;
         this.equipment = equipment;
         this.potionEffects = potionEffects;
      }
   }

   private static enum ProfessionRole {
      ALL("all", "\u5168\u90e8"),
      HUNTER("hunter", "\u730e\u4eba"),
      ESCAPER("escaper", "\u9003\u751f\u8005");

      private final String configValue;
      private final String displayName;

      private ProfessionRole(String configValue, String displayName) {
         this.configValue = configValue;
         this.displayName = displayName;
      }

      private static ProfessionRole fromConfig(String rawRole) {
         if (rawRole == null) {
            return ALL;
         } else {
            switch (rawRole.trim().toLowerCase(Locale.ENGLISH)) {
               case "":
               case "all":
               case "any":
               case "both":
               case "\u5168\u90e8":
               case "\u4e0d\u9650":
               case "\u4efb\u610f":
                  return ALL;
               case "hunter":
               case "hunters":
               case "\u730e\u4eba":
                  return HUNTER;
               case "escaper":
               case "escape":
               case "escapers":
               case "runner":
               case "runners":
               case "\u9003\u751f\u8005":
                  return ESCAPER;
               default:
                  return null;
            }
         }
      }

      // $FF: synthetic method
      private static ProfessionRole[] $values() {
         return new ProfessionRole[]{ALL, HUNTER, ESCAPER};
      }
   }

   private static final class ProfessionPotionEffect {
      private final PotionEffectType type;
      private final int amplifier;
      private final int durationTicks;
      private final boolean ambient;
      private final boolean particles;
      private final boolean icon;

      private ProfessionPotionEffect(PotionEffectType type, int amplifier, int durationTicks, boolean ambient, boolean particles, boolean icon) {
         this.type = type;
         this.amplifier = amplifier;
         this.durationTicks = durationTicks;
         this.ambient = ambient;
         this.particles = particles;
         this.icon = icon;
      }
   }
}
