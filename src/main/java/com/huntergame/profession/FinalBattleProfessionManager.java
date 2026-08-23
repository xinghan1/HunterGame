package com.huntergame.profession;

import com.huntergame.HunterGame;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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
import java.util.Set;
import java.util.UUID;

public class FinalBattleProfessionManager implements Listener {

    private static final String JOBS_FOLDER = "jobs";
    private static final String GUI_CONFIG_PATH = "final_battle.profession_gui";
    private static final String GUI_TITLE = "选择职业";
    private static final String ITEM_EDITOR_TITLE_PREFIX = "编辑职业物品: ";
    private static final int ITEM_EDITOR_SIZE = 45;
    private static final long SELECTION_GUI_OPEN_DELAY_TICKS = 20L * 2L;
    private static final List<String> DEFAULT_JOB_RESOURCES = Arrays.asList(
            "jobs/escaper_archers.yml",
            "jobs/escaper_assassin.yml",
            "jobs/escaper_boom.yml",
            "jobs/escaper_flash_warrior.yml",
            "jobs/escaper_juggernaut.yml",
            "jobs/escaper_lurk.yml",
            "jobs/escaper_mace.yml",
            "jobs/escaper_pilot.yml",
            "jobs/escaper_warrior.yml",
            "jobs/hunter_archers.yml",
            "jobs/hunter_assassin.yml",
            "jobs/hunter_boom.yml",
            "jobs/hunter_flash_warrior.yml",
            "jobs/hunter_juggernaut.yml",
            "jobs/hunter_lurk.yml",
            "jobs/hunter_mace.yml",
            "jobs/hunter_pilot.yml",
            "jobs/hunter_warrior.yml",
            "jobs/hunter_forbidden_mage.yml"
//            "jobs/hunter_spearman.yml",
//            "jobs/escaper_spearman.yml"
    );

    private final HunterGame plugin;
    private final File jobsFolder;
    private final Map<String, Profession> professions = new LinkedHashMap<>();
    private final Set<UUID> waitingPlayers = new HashSet<>();
    private final Map<UUID, String> selectedProfessions = new HashMap<>();
    private final Map<String, Integer> hunterSelections = new HashMap<>();
    private final Map<String, Integer> escaperSelections = new HashMap<>();
    private final Map<UUID, Map<Integer, String>> guiSlotsByPlayer = new HashMap<>();
    private final Map<UUID, Integer> guiRefreshTasks = new HashMap<>();
    private final Map<UUID, File> editingJobFiles = new HashMap<>();

    public FinalBattleProfessionManager(HunterGame plugin) {
        this.plugin = plugin;
        this.jobsFolder = new File(plugin.getDataFolder(), JOBS_FOLDER);
        reload();
    }

    public void reload() {
        ensureJobsFolder();
        professions.clear();

        File[] files = jobsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ENGLISH).endsWith(".yml"));
        if (files == null) {
            return;
        }

        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            Profession profession = loadProfession(file);
            if (profession != null && profession.enabled) {
                professions.put(profession.id, profession);
            }
        }
    }

    public void resetSelections() {
        cancelAllGuiRefreshTasks();
        waitingPlayers.clear();
        selectedProfessions.clear();
        hunterSelections.clear();
        escaperSelections.clear();
        guiSlotsByPlayer.clear();
    }

    public List<String> getProfessionIds() {
        return new ArrayList<>(professions.keySet());
    }

    public boolean saveProfessionFromInventory(Player player, String id, String displayName) {
        if (!isValidJobId(id)) {
            player.sendMessage(plugin.getMessage("profession_invalid_id", "&c职业ID只能包含字母、数字、下划线和短横线。"));
            return false;
        }

        ensureJobsFolder();
        File jobFile = getJobFile(id);
        FileConfiguration jobConfig = YamlConfiguration.loadConfiguration(jobFile);

        jobConfig.set("id", id);
        jobConfig.set("display_name", displayName);
        setDefault(jobConfig, "enabled", true);
        setDefault(jobConfig, "icon", getIconMaterial(player).name());
        setDefault(jobConfig, "slot", getNextFreeSlot(id));
        setDefault(jobConfig, "max_per_team", 0);
        setDefault(jobConfig, "max_health", 20.0);
        setDefault(jobConfig, "role", ProfessionRole.ALL.configValue);
        setDefault(jobConfig, "skill", "");
        if (!jobConfig.contains("potion_effects")) {
            jobConfig.set("potion_effects", createDefaultPotionEffectTemplate());
        }
        if (!jobConfig.contains("lore")) {
            List<String> lore = new ArrayList<>();
            lore.add("&7选择后获得该职业配置物品");
            lore.add("&7适用阵营: &e%role%");
            lore.add("&7职业技能: &e%skill%");
            lore.add("&7每队最多: &e%limit%");
            lore.add("&7当前队伍已选择: &b%selected%");
            jobConfig.set("lore", lore);
        }

        jobConfig.set("items", null);
        jobConfig.set("equipment", null);
        int saved = saveInventoryItems(player.getInventory(), jobConfig);
        saved += saveEquipmentItems(player.getInventory(), jobConfig);

        try {
            jobConfig.save(jobFile);
            reload();
            player.sendMessage(plugin.getMessage("profession_saved", "&a已保存终章职业 &e%id% &a到 jobs/%file%，物品数量: %count%")
                    .replace("%id%", id)
                    .replace("%file%", id + ".yml")
                    .replace("%count%", String.valueOf(saved)));
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("保存职业 " + id + " 失败: " + e.getMessage());
            player.sendMessage(plugin.getMessage("profession_save_failed", "&c保存职业配置失败，请查看控制台。"));
            return false;
        }
    }

    public boolean openProfessionItemEditor(Player player, String id) {
        if (!isValidJobId(id)) {
            player.sendMessage(plugin.getMessage("profession_invalid_id", "&c职业ID只能包含字母、数字、下划线和短横线。"));
            return false;
        }

        ensureJobsFolder();
        File jobFile = findExistingJobFile(id);
        if (jobFile == null) {
            player.sendMessage(plugin.getMessage("profession_not_found", "&c未找到职业配置: %id%")
                    .replace("%id%", id));
            return false;
        }

        FileConfiguration jobConfig = YamlConfiguration.loadConfiguration(jobFile);
        Inventory inventory = Bukkit.createInventory(null, ITEM_EDITOR_SIZE, color("&8" + ITEM_EDITOR_TITLE_PREFIX + id));
        loadProfessionItemsToEditor(inventory, jobConfig);
        editingJobFiles.put(player.getUniqueId(), jobFile);
        player.openInventory(inventory);
        return true;
    }

    public void startSelection(Collection<Player> players) {
        resetSelections();
        if (professions.isEmpty()) {
            plugin.getLogger().warning("jobs 文件夹中没有可用终章职业，已跳过职业选择。");
            return;
        }

        for (Player player : players) {
            if (player != null && player.isOnline() && isFinalBattlePlayer(player)) {
                if (!hasAvailableProfession(player)) {
                    player.sendMessage(plugin.getMessage("profession_no_available_for_role", "&c当前没有适合你阵营的终章职业，请联系管理员检查 jobs 配置。"));
                    plugin.getLogger().warning("玩家 " + player.getName() + " 没有可选终章职业，阵营: " + getPlayerRoleName(player));
                    continue;
                }
                waitingPlayers.add(player.getUniqueId());
                Bukkit.getScheduler().runTaskLater(plugin, () -> openSelectionGui(player), SELECTION_GUI_OPEN_DELAY_TICKS);
            }
        }
    }

    public boolean hasSelected(Player player) {
        return selectedProfessions.containsKey(player.getUniqueId());
    }

    public boolean giveSelectedProfessionLoadout(Player player) {
        if (player == null) {
            return false;
        }

        String professionId = selectedProfessions.get(player.getUniqueId());
        Profession profession = professions.get(professionId);
        if (profession == null) {
            plugin.getLogger().warning("无法重新发放终章职业物品，玩家未选择职业或职业已不存在: " + player.getName());
            return false;
        }

        applyMaxHealth(player, profession);
        giveProfessionItems(player, profession);
        giveProfessionSkill(player, profession);
        applyPotionEffects(player, profession);        return true;
    }

    private Profession loadProfession(File file) {
        FileConfiguration jobConfig = YamlConfiguration.loadConfiguration(file);
        String fallbackId = stripYamlExtension(file.getName());
        String id = jobConfig.getString("id", fallbackId);
        if (!isValidJobId(id)) {
            plugin.getLogger().warning("忽略无效职业文件: " + file.getName());
            return null;
        }

        String displayName = color(jobConfig.getString("display_name", id));
        Material icon = parseMaterial(jobConfig.getString("icon", "CHEST"), Material.CHEST);
        int slot = jobConfig.getInt("slot", -1);
        int maxPerTeam = jobConfig.getInt("max_per_team", 0);
        double maxHealth = Math.max(1.0, jobConfig.getDouble("max_health", 20.0));
        ProfessionRole role = parseProfessionRole(jobConfig, id);
        String skill = color(jobConfig.getString("skill", ""));
        boolean enabled = jobConfig.getBoolean("enabled", true);
        List<String> lore = jobConfig.getStringList("lore");
        Map<Integer, ItemStack> items = loadItems(jobConfig.getConfigurationSection("items"));
        Map<String, ItemStack> equipment = loadEquipment(jobConfig.getConfigurationSection("equipment"));
        List<ProfessionPotionEffect> potionEffects = loadPotionEffects(jobConfig, id);
        return new Profession(id, displayName, icon, slot, maxPerTeam, maxHealth, role, skill, enabled, lore, items, equipment, potionEffects);
    }

    private Map<Integer, ItemStack> loadItems(ConfigurationSection section) {
        Map<Integer, ItemStack> items = new HashMap<>();
        if (section == null) {
            return items;
        }

        for (String key : section.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                ItemStack item = section.getItemStack(key + ".item");
                if (item != null && item.getType() != Material.AIR) {
                    items.put(slot, item);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return items;
    }

    private Map<String, ItemStack> loadEquipment(ConfigurationSection section) {
        Map<String, ItemStack> equipment = new HashMap<>();
        if (section == null) {
            return equipment;
        }

        for (String key : section.getKeys(false)) {
            ItemStack item = section.getItemStack(key + ".item");
            if (item != null && item.getType() != Material.AIR) {
                equipment.put(key.toLowerCase(Locale.ENGLISH), item);
            }
        }
        return equipment;
    }

    private void openSelectionGui(Player player) {
        if (!waitingPlayers.contains(player.getUniqueId()) || !isFinalBattlePlayer(player)) {
            return;
        }

        int rows = Math.max(1, Math.min(6, plugin.getConfig().getInt(GUI_CONFIG_PATH + ".rows", 3)));
        String title = color(plugin.getConfig().getString(GUI_CONFIG_PATH + ".title", GUI_TITLE));
        Inventory inventory = Bukkit.createInventory(null, rows * 9, title);

        if (!renderSelectionGui(player, inventory)) {
            return;
        }

        player.openInventory(inventory);
        startGuiRefreshTask(player);
    }

    private boolean renderSelectionGui(Player player, Inventory inventory) {
        if (!waitingPlayers.contains(player.getUniqueId()) || !isFinalBattlePlayer(player)) {
            return false;
        }

        inventory.clear();
        Map<Integer, String> slotMap = new HashMap<>();

        int nextSlot = 0;
        for (Profession profession : professions.values()) {
            if (!isRoleAllowed(player, profession)) {
                continue;
            }
            int slot = profession.slot >= 0 ? profession.slot : nextFreeGuiSlot(inventory, nextSlot);
            nextSlot = slot + 1;
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(slot, createGuiItem(profession, player));
            slotMap.put(slot, profession.id);
        }

        if (slotMap.isEmpty()) {
            waitingPlayers.remove(player.getUniqueId());
            guiSlotsByPlayer.remove(player.getUniqueId());
            cancelGuiRefreshTask(player.getUniqueId());
            player.sendMessage(plugin.getMessage("profession_no_available_for_role", "&c当前没有适合你阵营的终章职业，请联系管理员检查 jobs 配置。"));
            plugin.getLogger().warning("玩家 " + player.getName() + " 打开职业 GUI 时没有可选职业，阵营: " + getPlayerRoleName(player));
            return false;
        }

        guiSlotsByPlayer.put(player.getUniqueId(), slotMap);
        return true;
    }

    private void startGuiRefreshTask(Player player) {
        UUID uuid = player.getUniqueId();
        cancelGuiRefreshTask(uuid);
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> refreshOpenSelectionGui(player), 20L, 20L);
        guiRefreshTasks.put(uuid, taskId);
    }

    private void refreshOpenSelectionGui(Player player) {
        UUID uuid = player.getUniqueId();
        if (!player.isOnline() || !waitingPlayers.contains(uuid) || !isFinalBattlePlayer(player)) {
            cancelGuiRefreshTask(uuid);
            return;
        }

        if (!isProfessionGui(player.getOpenInventory().getTitle())) {
            return;
        }

        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (!renderSelectionGui(player, inventory)) {
            player.closeInventory();
            return;
        }

        player.updateInventory();
    }

    private void cancelGuiRefreshTask(UUID uuid) {
        Integer taskId = guiRefreshTasks.remove(uuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void cancelAllGuiRefreshTasks() {
        for (Integer taskId : guiRefreshTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        guiRefreshTasks.clear();
    }

    private ItemStack createGuiItem(Profession profession, Player player) {
        ItemStack item = new ItemStack(profession.icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(profession.displayName);
            List<String> lore = new ArrayList<>();
            int selected = getSelectionCount(player, profession.id);
            int max = profession.maxPerTeam;
            for (String line : profession.lore) {
                lore.add(color(line)
                        .replace("%limit%", max <= 0 ? "不限" : String.valueOf(max))
                        .replace("%selected%", String.valueOf(selected))
                        .replace("%role%", profession.role.displayName)
                        .replace("%skill%", profession.skill.isEmpty() ? "无" : profession.skill));
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
        setEditorItem(inventory, 0, jobConfig.getItemStack("equipment.helmet.item"));
        setEditorItem(inventory, 1, jobConfig.getItemStack("equipment.chestplate.item"));
        setEditorItem(inventory, 2, jobConfig.getItemStack("equipment.leggings.item"));
        setEditorItem(inventory, 3, jobConfig.getItemStack("equipment.boots.item"));
        setEditorItem(inventory, 4, jobConfig.getItemStack("equipment.offhand.item"));

        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack item = jobConfig.getItemStack("items." + inventorySlot + ".item");
            setEditorItem(inventory, inventorySlotToEditorSlot(inventorySlot), item);
        }
    }

    private void setEditorItem(Inventory inventory, int slot, ItemStack item) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, cloneItem(item));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (editingJobFiles.containsKey(player.getUniqueId())) {
            handleProfessionItemEditorClick(event);
            return;
        }

        if (!isProfessionGui(event.getView().getTitle())) {
            if (waitingPlayers.contains(player.getUniqueId())) {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);
        if (!waitingPlayers.contains(player.getUniqueId())) {
            return;
        }

        Profession profession = findProfessionBySlot(player, event.getRawSlot());
        if (profession == null) {
            return;
        }
        selectProfession(player, profession);
    }

    private void handleProfessionItemEditorClick(InventoryClickEvent event) {
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < event.getView().getTopInventory().getSize() && isLockedEditorSlot(rawSlot)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (!editingJobFiles.containsKey(player.getUniqueId())) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize && isLockedEditorSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void saveProfessionItemEditor(Player player, Inventory inventory, File jobFile) {
        FileConfiguration jobConfig = YamlConfiguration.loadConfiguration(jobFile);
        jobConfig.set("items", null);
        jobConfig.set("equipment", null);

        int saved = 0;
        saved += saveEditorEquipmentItem(jobConfig, "helmet", inventory.getItem(0));
        saved += saveEditorEquipmentItem(jobConfig, "chestplate", inventory.getItem(1));
        saved += saveEditorEquipmentItem(jobConfig, "leggings", inventory.getItem(2));
        saved += saveEditorEquipmentItem(jobConfig, "boots", inventory.getItem(3));
        saved += saveEditorEquipmentItem(jobConfig, "offhand", inventory.getItem(4));

        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack item = inventory.getItem(inventorySlotToEditorSlot(inventorySlot));
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            jobConfig.set("items." + inventorySlot + ".item", item.clone());
            saved++;
        }

        try {
            jobConfig.save(jobFile);
            reload();
            player.sendMessage(plugin.getMessage("profession_items_saved", "&a已保存职业物品到 jobs/%file%，物品数量: %count%")
                    .replace("%file%", jobFile.getName())
                    .replace("%count%", String.valueOf(saved)));
        } catch (IOException e) {
            plugin.getLogger().severe("保存职业物品失败: " + jobFile.getName() + " - " + e.getMessage());
            player.sendMessage(plugin.getMessage("profession_items_save_failed", "&c保存职业物品失败，请查看控制台。"));
        }
    }

    private int saveEditorEquipmentItem(FileConfiguration jobConfig, String slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return 0;
        }
        jobConfig.set("equipment." + slot + ".item", item.clone());
        return 1;
    }

    private boolean isLockedEditorSlot(int slot) {
        return slot >= 5 && slot <= 8;
    }

    private int inventorySlotToEditorSlot(int inventorySlot) {
        if (inventorySlot >= 0 && inventorySlot <= 8) {
            return 36 + inventorySlot;
        }
        if (inventorySlot >= 9 && inventorySlot <= 35) {
            return inventorySlot;
        }
        return -1;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        File editingJobFile = editingJobFiles.remove(player.getUniqueId());
        if (editingJobFile != null) {
            saveProfessionItemEditor(player, event.getInventory(), editingJobFile);
            return;
        }

        if (!isProfessionGui(event.getView().getTitle()) || !waitingPlayers.contains(player.getUniqueId())) {
            return;
        }

        cancelGuiRefreshTask(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (waitingPlayers.contains(player.getUniqueId()) && player.isOnline() && isFinalBattlePlayer(player)) {
                player.sendMessage(plugin.getMessage("profession_must_choose", "&c你必须选择一个终章职业才能继续游戏！"));
                openSelectionGui(player);
            }
        }, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        waitingPlayers.remove(uuid);
        guiSlotsByPlayer.remove(uuid);
        cancelGuiRefreshTask(uuid);
        editingJobFiles.remove(uuid);
    }

    private void selectProfession(Player player, Profession profession) {
        if (!isRoleAllowed(player, profession)) {
            player.sendMessage(plugin.getMessage("profession_role_only", "&c该职业仅限 %role% 选择。")
                    .replace("%role%", profession.role.displayName));
            refreshOpenSelectionGui(player);
            return;
        }

        if (!canSelect(player, profession)) {
            player.sendMessage(plugin.getMessage("profession_team_limit_reached", "&c该职业在你的队伍中已达到选择上限！"));
            refreshOpenSelectionGui(player);
            return;
        }

        addSelection(player, profession.id);
        selectedProfessions.put(player.getUniqueId(), profession.id);
        waitingPlayers.remove(player.getUniqueId());
        guiSlotsByPlayer.remove(player.getUniqueId());
        cancelGuiRefreshTask(player.getUniqueId());
        applyMaxHealth(player, profession);
        giveProfessionItems(player, profession);
        giveProfessionSkill(player, profession);
        applyPotionEffects(player, profession);        player.sendMessage(plugin.getMessage("profession_selected", "&a你选择了终章职业: %profession%")
                .replace("%profession%", profession.displayName));
        player.closeInventory();
    }

    private void giveProfessionSkill(Player player, Profession profession) {
        String skill = ChatColor.stripColor(profession.skill).trim();
        if (skill.isEmpty()) {
            return;
        }

        if (!plugin.getSkillManager().isSkillConfigured(skill)) {
            player.sendMessage(plugin.getMessage("profession_skill_invalid", "&c职业技能配置无效: %skill%")
                    .replace("%skill%", skill));
            plugin.getLogger().warning("职业 " + profession.id + " 配置了无效技能: " + skill);
            return;
        }

        if (!plugin.getSkillManager().isSkillEnabled(skill)) {
            player.sendMessage(plugin.getMessage("profession_skill_disabled", "&c该职业技能已禁用: %skill%")
                    .replace("%skill%", skill));
            plugin.getLogger().warning("职业 " + profession.id + " 配置的技能已禁用: " + skill);
            return;
        }

        plugin.getSkillManager().confirmSkillSelection(player, skill);
    }

    private void applyMaxHealth(Player player, Profession profession) {
        if (player.getAttribute(Attribute.GENERIC_MAX_HEALTH) == null) {
            return;
        }
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(profession.maxHealth);
        player.setHealth(Math.min(profession.maxHealth, player.getMaxHealth()));
    }

    private void giveProfessionItems(Player player, Profession profession) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        player.getEquipment().clear();
        inventory.setItemInOffHand(new ItemStack(Material.AIR));
        for (Map.Entry<Integer, ItemStack> entry : profession.items.entrySet()) {
            ItemStack item = entry.getValue().clone();
            int slot = entry.getKey();
            ItemStack current = slot >= 0 && slot < inventory.getStorageContents().length ? inventory.getItem(slot) : null;
            if (current == null || current.getType() == Material.AIR) {
                inventory.setItem(slot, item);
            } else {
                inventory.addItem(item).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }
        }
        giveProfessionEquipment(inventory, profession.equipment);
    }

    private void giveProfessionEquipment(PlayerInventory inventory, Map<String, ItemStack> equipment) {
        setEquipmentItem(equipment, "helmet", inventory::setHelmet);
        setEquipmentItem(equipment, "chestplate", inventory::setChestplate);
        setEquipmentItem(equipment, "leggings", inventory::setLeggings);
        setEquipmentItem(equipment, "boots", inventory::setBoots);
        setEquipmentItem(equipment, "offhand", inventory::setItemInOffHand);
    }

    private void setEquipmentItem(Map<String, ItemStack> equipment, String key, java.util.function.Consumer<ItemStack> setter) {
        ItemStack item = equipment.get(key);
        if (item != null && item.getType() != Material.AIR) {
            setter.accept(item.clone());
        }
    }

    private void applyPotionEffects(Player player, Profession profession) {
        for (ProfessionPotionEffect effect : profession.potionEffects) {
            player.addPotionEffect(new PotionEffect(
                    effect.type,
                    effect.durationTicks,
                    effect.amplifier,
                    effect.ambient,
                    effect.particles,
                    effect.icon
            ), true);
        }
    }

    private int saveInventoryItems(PlayerInventory inventory, FileConfiguration jobConfig) {
        int saved = 0;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            jobConfig.set("items." + slot + ".item", item.clone());
            saved++;
        }
        return saved;
    }

    private int saveEquipmentItems(PlayerInventory inventory, FileConfiguration jobConfig) {
        int saved = 0;
        saved += saveEquipmentItem(jobConfig, "helmet", inventory.getHelmet());
        saved += saveEquipmentItem(jobConfig, "chestplate", inventory.getChestplate());
        saved += saveEquipmentItem(jobConfig, "leggings", inventory.getLeggings());
        saved += saveEquipmentItem(jobConfig, "boots", inventory.getBoots());
        saved += saveEquipmentItem(jobConfig, "offhand", inventory.getItemInOffHand());
        return saved;
    }

    private int saveEquipmentItem(FileConfiguration jobConfig, String slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return 0;
        }
        jobConfig.set("equipment." + slot + ".item", item.clone());
        return 1;
    }

    private List<Map<String, Object>> createDefaultPotionEffectTemplate() {
        return new ArrayList<>();
    }

    private List<ProfessionPotionEffect> loadPotionEffects(FileConfiguration jobConfig, String professionId) {
        List<ProfessionPotionEffect> effects = new ArrayList<>();
        List<?> rawEffects = jobConfig.getList("potion_effects");
        if (rawEffects == null) {
            return effects;
        }

        for (Object rawEffect : rawEffects) {
            ProfessionPotionEffect effect = parsePotionEffect(rawEffect, professionId);
            if (effect != null) {
                effects.add(effect);
            }
        }
        return effects;
    }

    private ProfessionPotionEffect parsePotionEffect(Object rawEffect, String professionId) {
        if (rawEffect instanceof Map<?, ?>) {
            return parsePotionEffectMap((Map<?, ?>) rawEffect, professionId);
        }
        if (rawEffect instanceof String) {
            return parsePotionEffectString((String) rawEffect, professionId);
        }
        plugin.getLogger().warning("职业 " + professionId + " 存在无效药水效果配置: " + rawEffect);
        return null;
    }

    private ProfessionPotionEffect parsePotionEffectMap(Map<?, ?> map, String professionId) {
        if (!readBoolean(map, "enabled", true)) {
            return null;
        }

        Object rawType = map.get("type");
        String typeName = rawType == null ? "" : String.valueOf(rawType).trim();
        if (typeName.isEmpty()) {
            return null;
        }
        if (typeName.contains(":")) {
            return parsePotionEffectSpec(typeName, professionId);
        }

        PotionEffectType type = resolvePotionEffectType(typeName);
        if (type == null) {
            plugin.getLogger().warning("职业 " + professionId + " 配置了无效药水效果: " + typeName);
            return null;
        }

        int amplifier = readInt(map, "amplifier", 0);
        boolean permanent = readBoolean(map, "permanent", true);
        int durationSeconds = readInt(map, "duration", 0);
        int durationTicks = permanent || durationSeconds <= 0 ? Integer.MAX_VALUE : durationSeconds * 20;
        boolean ambient = readBoolean(map, "ambient", true);
        boolean particles = readBoolean(map, "particles", false);
        boolean icon = readBoolean(map, "icon", true);
        return new ProfessionPotionEffect(type, amplifier, durationTicks, ambient, particles, icon);
    }

    private ProfessionPotionEffect parsePotionEffectString(String rawEffect, String professionId) {
        return parsePotionEffectSpec(rawEffect, professionId);
    }

    private ProfessionPotionEffect parsePotionEffectSpec(String rawEffect, String professionId) {
        String[] parts = rawEffect.split(":");
        if (parts.length == 0) {
            return null;
        }

        String typeName = parts[0].trim();
        if (typeName.isEmpty()) {
            return null;
        }

        PotionEffectType type = resolvePotionEffectType(typeName);
        if (type == null) {
            plugin.getLogger().warning("职业 " + professionId + " 配置了无效药水效果: " + rawEffect);
            return null;
        }

        int durationSeconds = parts.length > 1 ? parseInt(parts[1], -1) : -1;
        int amplifier = parts.length > 2 ? parseInt(parts[2], 0) : 0;
        int durationTicks = durationSeconds < 0 ? Integer.MAX_VALUE : durationSeconds * 20;
        return new ProfessionPotionEffect(type, amplifier, durationTicks, true, false, true);
    }

    private PotionEffectType resolvePotionEffectType(String typeName) {
        if (typeName == null) {
            return null;
        }

        String keyText = typeName.trim().toLowerCase(Locale.ENGLISH).replace(' ', '_');
        if (keyText.isEmpty()) {
            return null;
        }

        try {
            NamespacedKey key = keyText.contains(":")
                    ? NamespacedKey.fromString(keyText)
                    : NamespacedKey.minecraft(keyText);
            return key == null ? null : Registry.EFFECT.get(key);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private int readInt(Map<?, ?> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return value == null ? defaultValue : parseInt(String.valueOf(value), defaultValue);
    }

    private boolean readBoolean(Map<?, ?> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private boolean canSelect(Player player, Profession profession) {
        return profession.maxPerTeam <= 0 || getSelectionCount(player, profession.id) < profession.maxPerTeam;
    }

    private boolean hasAvailableProfession(Player player) {
        for (Profession profession : professions.values()) {
            if (isRoleAllowed(player, profession)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRoleAllowed(Player player, Profession profession) {
        UUID uuid = player.getUniqueId();
        switch (profession.role) {
            case HUNTER:
                return plugin.isHunter(uuid);
            case ESCAPER:
                return plugin.isEscaper(uuid);
            case ALL:
            default:
                return true;
        }
    }

    private String getPlayerRoleName(Player player) {
        UUID uuid = player.getUniqueId();
        if (plugin.isHunter(uuid)) {
            return ProfessionRole.HUNTER.displayName;
        }
        if (plugin.isEscaper(uuid)) {
            return ProfessionRole.ESCAPER.displayName;
        }
        return "未知";
    }

    private void addSelection(Player player, String professionId) {
        Map<String, Integer> selections = plugin.isHunter(player.getUniqueId()) ? hunterSelections : escaperSelections;
        selections.put(professionId, selections.getOrDefault(professionId, 0) + 1);
    }

    private int getSelectionCount(Player player, String professionId) {
        Map<String, Integer> selections = plugin.isHunter(player.getUniqueId()) ? hunterSelections : escaperSelections;
        return selections.getOrDefault(professionId, 0);
    }

    private Profession findProfessionBySlot(Player player, int rawSlot) {
        if (rawSlot < 0) {
            return null;
        }

        Map<Integer, String> slots = guiSlotsByPlayer.get(player.getUniqueId());
        if (slots == null) {
            return null;
        }
        return professions.get(slots.get(rawSlot));
    }

    private boolean isFinalBattlePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        return plugin.isFinalBattleMode() && (plugin.isHunter(uuid) || plugin.isEscaper(uuid));
    }

    private boolean isProfessionGui(String title) {
        return color(plugin.getConfig().getString(GUI_CONFIG_PATH + ".title", GUI_TITLE)).equals(title);
    }

    private int nextFreeGuiSlot(Inventory inventory, int startSlot) {
        for (int slot = startSlot; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                return slot;
            }
        }
        return -1;
    }

    private int getNextFreeSlot(String currentId) {
        Set<Integer> usedSlots = new HashSet<>();
        for (Profession profession : professions.values()) {
            if (!profession.id.equals(currentId)) {
                usedSlots.add(profession.slot);
            }
        }

        for (int slot = 10; slot <= 16; slot++) {
            if (!usedSlots.contains(slot)) {
                return slot;
            }
        }
        return Math.max(0, professions.size());
    }

    private File getJobFile(String id) {
        return new File(jobsFolder, id + ".yml");
    }

    private File findExistingJobFile(String id) {
        File directFile = getJobFile(id);
        if (directFile.exists()) {
            return directFile;
        }

        File[] files = jobsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ENGLISH).endsWith(".yml"));
        if (files == null) {
            return null;
        }

        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            FileConfiguration jobConfig = YamlConfiguration.loadConfiguration(file);
            String fileId = stripYamlExtension(file.getName());
            String configId = jobConfig.getString("id", fileId);
            if (id.equalsIgnoreCase(fileId) || id.equalsIgnoreCase(configId)) {
                return file;
            }
        }
        return null;
    }

    private Material getIconMaterial(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.getType() != Material.AIR) {
            return mainHand.getType();
        }
        return Material.CHEST;
    }

    private Material parseMaterial(String value, Material defaultMaterial) {
        if (value == null) {
            return defaultMaterial;
        }
        try {
            return Material.valueOf(value.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            return defaultMaterial;
        }
    }

    private ProfessionRole parseProfessionRole(FileConfiguration jobConfig, String professionId) {
        String rawRole = jobConfig.getString("role", jobConfig.getString("team", jobConfig.getString("category", ProfessionRole.ALL.configValue)));
        ProfessionRole role = ProfessionRole.fromConfig(rawRole);
        if (role == null) {
            plugin.getLogger().warning("职业 " + professionId + " 配置了无效适用阵营: " + rawRole + "，已按 all 处理。");
            return ProfessionRole.ALL;
        }
        return role;
    }

    private boolean isValidJobId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]+");
    }

    private String stripYamlExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
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
        if (!jobsFolder.exists()) {
            jobsFolder.mkdirs();
        }
        if (!hasJobFiles()) {
            for (String resource : DEFAULT_JOB_RESOURCES) {
                plugin.saveResource(resource, false);
            }
        }
        migrateLegacyMaceSkills();
    }

    private void migrateLegacyMaceSkills() {
        migrateLegacyMaceSkill(new File(jobsFolder, "hunter_mace.yml"));
        migrateLegacyMaceSkill(new File(jobsFolder, "escaper_mace.yml"));
    }

    private void migrateLegacyMaceSkill(File jobFile) {
        if (!jobFile.isFile()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(jobFile);
        if (!"闪现".equals(config.getString("skill", ""))) {
            return;
        }

        config.set("skill", "腾空");
        List<String> lore = config.getStringList("lore");
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (line.contains("向正前方闪现30格")) {
                lore.set(
                        i,
                        "&7手持重锤+右键，向上腾空约12格，冷却12秒"
                );
            }
        }
        config.set("lore", lore);

        try {
            config.save(jobFile);
        } catch (IOException ex) {
            plugin.getLogger().warning(
                    "迁移重锤职业腾空技能失败: " + jobFile.getName()
                            + " - " + ex.getMessage()
            );
        }
    }

    private boolean hasJobFiles() {
        File[] files = jobsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ENGLISH).endsWith(".yml"));
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

        private Profession(String id, String displayName, Material icon, int slot, int maxPerTeam, double maxHealth, ProfessionRole role, String skill,
                           boolean enabled, List<String> lore, Map<Integer, ItemStack> items,
                           Map<String, ItemStack> equipment, List<ProfessionPotionEffect> potionEffects) {
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

    private enum ProfessionRole {
        ALL("all", "全部"),
        HUNTER("hunter", "猎人"),
        ESCAPER("escaper", "逃生者");

        private final String configValue;
        private final String displayName;

        ProfessionRole(String configValue, String displayName) {
            this.configValue = configValue;
            this.displayName = displayName;
        }

        private static ProfessionRole fromConfig(String rawRole) {
            if (rawRole == null) {
                return ALL;
            }

            String value = rawRole.trim().toLowerCase(Locale.ENGLISH);
            switch (value) {
                case "":
                case "all":
                case "any":
                case "both":
                case "全部":
                case "不限":
                case "任意":
                    return ALL;
                case "hunter":
                case "hunters":
                case "猎人":
                    return HUNTER;
                case "escaper":
                case "escape":
                case "escapers":
                case "runner":
                case "runners":
                case "逃生者":
                    return ESCAPER;
                default:
                    return null;
            }
        }
    }

    private static final class ProfessionPotionEffect {
        private final PotionEffectType type;
        private final int amplifier;
        private final int durationTicks;
        private final boolean ambient;
        private final boolean particles;
        private final boolean icon;

        private ProfessionPotionEffect(PotionEffectType type, int amplifier, int durationTicks,
                                       boolean ambient, boolean particles, boolean icon) {
            this.type = type;
            this.amplifier = amplifier;
            this.durationTicks = durationTicks;
            this.ambient = ambient;
            this.particles = particles;
            this.icon = icon;
        }
    }
}

